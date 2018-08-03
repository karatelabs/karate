/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.intuit.karate.netty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Match;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StringUtils;
import com.intuit.karate.cucumber.FeatureProvider;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpUtils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.CharsetUtil;

/**
 * Request handler that serves HTTP/2 responses according to the specified Karate feature
 */
public final class FeatureServerHttp2ConnectionHandler extends Http2ConnectionHandler {

    private static final Logger logger = LoggerFactory.getLogger(FeatureServerHttp2ConnectionHandler.class);
    private final FeatureProvider provider;
    private final Runnable stopFunction; // TODO: add in use of stop function
    private static final String VAR_AFTER_SCENARIO = "afterScenario";

    FeatureServerHttp2ConnectionHandler(FeatureProvider provider, Runnable stopFunction, Http2ConnectionDecoder decoder, 
    		Http2ConnectionEncoder encoder, Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
        this.provider = provider;
        this.stopFunction = stopFunction;
    }

	@Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
   		logger.info("ConnectionHandler channelReadComplete: ) ");
   		//encoder(
        ctx.flush();
        super.channelReadComplete(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    	logger.info("ConnectionHandler write: ({}) ", msg);
    	if (msg instanceof FullHttpResponse) {
    		FullHttpResponse response = (FullHttpResponse)msg;
            Http2Headers headers = HttpConversionUtil.toHttp2Headers(response, false);
            encoder().writeHeaders(ctx, 1, headers, 0, false, ctx.newPromise());
            encoder().writeData(ctx, 1, response.content(), 0, true, ctx.newPromise());
    		
            return;
    	}
    	
    	ctx.write(msg, promise);
    }

	
    /**
     * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via HTTP/2
     * on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
   		logger.info("ConnectionHandler userEventTriggered: event({}) ", evt.toString());
        if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
            HttpServerUpgradeHandler.UpgradeEvent upgradeEvent =
                    (HttpServerUpgradeHandler.UpgradeEvent) evt;
            logger.info("ConnectionHandler userEventTriggered: request({}) ", upgradeEvent.upgradeRequest().toString());
            
            writeResponse(toHttpRequest(upgradeEvent.upgradeRequest()), ctx);
        }

        super.userEventTriggered(ctx, evt);
    }

    private HttpRequest toHttpRequest(FullHttpRequest msg) {
            HttpRequest request = new HttpRequest();
            StringUtils.Pair url = HttpUtils.parseUriIntoUrlBaseAndPath(msg.uri());
            if (url.left == null) {
                String requestScheme = provider.isSsl() ? "https" : "http";
                String host = msg.headers().get(HttpUtils.HEADER_HOST);
                request.setUrlBase(requestScheme + "://" + host);
            } else {
                request.setUrlBase(url.left);
            }
            request.setUri(url.right);
            request.setMethod(msg.method().name());
            msg.headers().forEach(h -> request.addHeader(h.getKey(), h.getValue()));
            QueryStringDecoder decoder = new QueryStringDecoder(url.right);
            decoder.parameters().forEach((k, v) -> request.putParam(k, v));
            HttpContent httpContent = (HttpContent) msg;
            ByteBuf content = httpContent.content();
            if (content.isReadable()) {
                byte[] bytes = new byte[content.readableBytes()];
                content.readBytes(bytes);
                request.setBody(bytes);
            }
		return request;
	}

	@Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }

    /**
     * 
     *
     */
    private void writeResponse(HttpRequest request, ChannelHandlerContext ctx) {
        provider.getContext().logger.debug("writeResponse for request headers: {}", request.getHeaders());
        Match match = Match.init()
                .defText(ScriptValueMap.VAR_REQUEST_URL_BASE, request.getUrlBase())
                .defText(ScriptValueMap.VAR_REQUEST_URI, request.getUri())
                .defText(ScriptValueMap.VAR_REQUEST_METHOD, request.getMethod())
                .def(ScriptValueMap.VAR_REQUEST_HEADERS, request.getHeaders())
                .def(ScriptValueMap.VAR_RESPONSE_STATUS, 200)
                .def(ScriptValueMap.VAR_REQUEST_PARAMS, request.getParams());
        if (request.getBody() != null) {
            String requestBody = FileUtils.toString(request.getBody());
            match.def(ScriptValueMap.VAR_REQUEST, requestBody);
        }
        ScriptValue responseValue, responseStatus, responseHeaders, afterScenario;
        Map<String, Object> responseHeadersMap, configResponseHeadersMap;
        // this is a sledgehammer approach to concurrency !
        // which is why for simulating 'delay', users should use the VAR_AFTER_SCENARIO (see end)
        synchronized (provider) { // BEGIN TRANSACTION !
            ScriptValueMap result = provider.handle(match.vars());
            ScriptContext context = provider.getContext();
            ScriptValue configResponseHeaders = context.getConfig().getResponseHeaders();
            responseValue = result.remove(ScriptValueMap.VAR_RESPONSE);
            responseStatus = result.remove(ScriptValueMap.VAR_RESPONSE_STATUS);
            responseHeaders = result.remove(ScriptValueMap.VAR_RESPONSE_HEADERS);
            afterScenario = result.remove(VAR_AFTER_SCENARIO);
            configResponseHeadersMap = configResponseHeaders == null ? null : configResponseHeaders.evalAsMap(context);
            responseHeadersMap = responseHeaders == null ? null : responseHeaders.evalAsMap(context);
        } // END TRANSACTION !!
        
        // Handle the Karate specified status
        HttpResponseStatus httpResponseStatus = responseStatus == null ? 
        		HttpResponseStatus.OK : HttpResponseStatus.valueOf(Integer.valueOf(responseStatus.getValue().toString()));

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, httpResponseStatus);
        
        // Handle Karate specified response headers
        // trying to avoid creating a map unless absolutely necessary
        Map<String, Object> newResponseHeaders = null;
        if (responseHeadersMap != null) {
        	Map<String, Object> temp = new LinkedHashMap(responseHeadersMap.size());
        	responseHeadersMap.forEach((k, v) -> {
        		if (v instanceof List) { // MultiValueMap returned by proceed / response.headers
        			List values = (List) v;
        			temp.put(k, StringUtils.join(values, ','));
        		} else {
        			temp.put(k, v);
        		}
        	});
        	newResponseHeaders = temp;
        }        
        if (configResponseHeadersMap != null) {
        	if (newResponseHeaders == null) {
        		newResponseHeaders = configResponseHeadersMap;
        	} else {
        		newResponseHeaders.putAll(configResponseHeadersMap);
        	}
        }
        if (newResponseHeaders != null) {
        	newResponseHeaders.forEach((k, v) -> response.headers().set(k, v));
        }
        if (responseValue != null && (newResponseHeaders == null || !newResponseHeaders.containsKey(HttpUtils.HEADER_CONTENT_TYPE))) {
        	response.headers().set(HttpUtils.HEADER_CONTENT_TYPE, HttpUtils.getContentType(responseValue));
        }
        if (provider.isCorsEnabled()) {
        	response.headers().set(HttpUtils.HEADER_AC_ALLOW_ORIGIN, "*");
        }
        
        Http2Headers headers = HttpConversionUtil.toHttp2Headers(response, false);
        encoder().writeHeaders(ctx, 1, headers, 0, false, ctx.newPromise());
        
        // Handle Karate specified response body
        if (responseValue != null){
            ByteBuf responseBuf;
            if (responseValue.getType() == ScriptValue.Type.BYTE_ARRAY) {
                responseBuf = Unpooled.copiedBuffer(responseValue.getValue(byte[].class));
            } else {
                responseBuf = Unpooled.copiedBuffer(responseValue.getAsString(), CharsetUtil.UTF_8);
            }
            
            logger.info("ConnectionHandler ResponseHandling response body: {} ", responseBuf.toString(CharsetUtil.UTF_8));
            encoder().writeData(ctx, 1, responseBuf, 0, true, ctx.newPromise());
        }
        
        // functions here are outside of the 'transaction' and should not mutate global state !
        // typically this is where users can set up an artificial delay or sleep
        if (afterScenario != null && afterScenario.isFunction()) {
            afterScenario.invokeFunction(provider.getContext());
        }        
    }    
}
