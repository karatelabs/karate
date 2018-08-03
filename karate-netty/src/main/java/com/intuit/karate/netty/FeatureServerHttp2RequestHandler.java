/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;
import io.netty.util.CharsetUtil;

/**
 * Handles all the requests for data. It receives a {@link FullHttpRequest},
 * which has been converted by a {@link InboundHttp2ToHttpAdapter} before it
 * arrived here. For further details, check {@link Http2OrHttpHandler} where the
 * pipeline is setup.
 */
public class FeatureServerHttp2RequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(FeatureServerHttp2RequestHandler.class);
    private final FeatureProvider provider;
    private final Runnable stopFunction;
    private static final String VAR_AFTER_SCENARIO = "afterScenario";

    FeatureServerHttp2RequestHandler(FeatureProvider provider, Runnable stopFunction) {
        this.provider = provider;
        this.stopFunction = stopFunction;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
		logger.info("Feature Server channelRead0 ");
        provider.getContext().logger.debug("handling method: {}, uri: {}", msg.method(), msg.uri());
        provider.getContext().logger.debug("handling request: {}", msg.toString());

//        StringUtils.Pair url = HttpUtils.parseUriIntoUrlBaseAndPath(msg.uri());
//        HttpRequest request = new HttpRequest();
//        if (url.left == null) {
//            String requestScheme = provider.isSsl() ? "https" : "http";
//            String host = msg.headers().get(HttpUtils.HEADER_HOST);
//            request.setUrlBase(requestScheme + "://" + host);
//        } else {
//            request.setUrlBase(url.left);
//        }
//        request.setUri(url.right);
//        request.setMethod(msg.method().name());
//        msg.headers().forEach(h -> request.addHeader(h.getKey(), h.getValue()));
//        QueryStringDecoder decoder = new QueryStringDecoder(url.right);
//        decoder.parameters().forEach((k, v) -> request.putParam(k, v));
//        HttpContent httpContent = (HttpContent) msg;
//        ByteBuf content = httpContent.content();
//        if (content.isReadable()) {
//            byte[] bytes = new byte[content.readableBytes()];
//            content.readBytes(bytes);
//            request.setBody(bytes);
//        }
        writeResponse(toHttpRequest(msg), ctx);
    
//        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);

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
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    	logger.info("Feature Server channelReadComplete ");
    	ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
    
    /**
     * 
     */
    private void writeResponse(HttpRequest request, ChannelHandlerContext ctx) {
//        provider.getContext().logger.debug("writeResponse for request headers: {}", request.getHeaders());
//        provider.getContext().logger.debug("writeResponse for request body: {}", FileUtils.toString(request.getBody()));
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
        HttpResponseStatus nettyResponseStatus;
        if (responseStatus == null) {
            nettyResponseStatus = HttpResponseStatus.OK;
        } else {
            nettyResponseStatus = HttpResponseStatus.valueOf(Integer.valueOf(responseStatus.getValue().toString()));
        }
        FullHttpResponse response;
        if (responseValue == null || responseValue.isNull()) {
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, nettyResponseStatus);
        } else {
            ByteBuf responseBuf;
            if (responseValue.getType() == ScriptValue.Type.BYTE_ARRAY) {
                responseBuf = Unpooled.copiedBuffer(responseValue.getValue(byte[].class));
            } else {
                responseBuf = Unpooled.copiedBuffer(responseValue.getAsString(), CharsetUtil.UTF_8);
            }
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, nettyResponseStatus, responseBuf);
        }
        // trying to avoid creating a map unless absolutely necessary
        Map<String, Object> headers = null;
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
            headers = temp;
        }
        if (configResponseHeadersMap != null) {
            if (headers == null) {
                headers = configResponseHeadersMap;
            } else {
                headers.putAll(configResponseHeadersMap);
            }
        }
        if (headers != null) {
            headers.forEach((k, v) -> response.headers().set(k, v));
        }
        if (responseValue != null && (headers == null || !headers.containsKey(HttpUtils.HEADER_CONTENT_TYPE))) {
            response.headers().set(HttpUtils.HEADER_CONTENT_TYPE, HttpUtils.getContentType(responseValue));
        }
        if (provider.isCorsEnabled()) {
            response.headers().set(HttpUtils.HEADER_AC_ALLOW_ORIGIN, "*");
        }
        // functions here are outside of the 'transaction' and should not mutate global state !
        // typically this is where users can set up an artificial delay or sleep
        if (afterScenario != null && afterScenario.isFunction()) {
            afterScenario.invokeFunction(provider.getContext());
        }
        provider.getContext().logger.debug("writeResponse of response body: {}", response.content().toString(CharsetUtil.UTF_8));
        ctx.write(response);
    }
//    private void writeResponse(HttpRequest request, ChannelHandlerContext ctx) {
//       provider.getContext().logger.debug("writeResponse for request body: {}", FileUtils.toString(request.getBody()));
//       Match match = Match.init()
//                .defText(ScriptValueMap.VAR_REQUEST_URL_BASE, request.getUrlBase())
//                .defText(ScriptValueMap.VAR_REQUEST_URI, request.getUri())
//                .defText(ScriptValueMap.VAR_REQUEST_METHOD, request.getMethod())
//                .def(ScriptValueMap.VAR_REQUEST_HEADERS, request.getHeaders())
//                .def(ScriptValueMap.VAR_RESPONSE_STATUS, 200)
//                .def(ScriptValueMap.VAR_REQUEST_PARAMS, request.getParams());
//        if (request.getBody() != null) {
//            String requestBody = FileUtils.toString(request.getBody());
//            match.def(ScriptValueMap.VAR_REQUEST, requestBody);
//        }
//        ScriptValue responseValue, responseStatus, responseHeaders, afterScenario;
//        Map<String, Object> responseHeadersMap, configResponseHeadersMap;
//        // this is a sledgehammer approach to concurrency !
//        // which is why for simulating 'delay', users should use the VAR_AFTER_SCENARIO (see end)
//        synchronized (provider) { // BEGIN TRANSACTION !
//            ScriptValueMap result = provider.handle(match.vars());
//            ScriptContext context = provider.getContext();
//            ScriptValue configResponseHeaders = context.getConfig().getResponseHeaders();
//            responseValue = result.remove(ScriptValueMap.VAR_RESPONSE);
//            responseStatus = result.remove(ScriptValueMap.VAR_RESPONSE_STATUS);
//            responseHeaders = result.remove(ScriptValueMap.VAR_RESPONSE_HEADERS);
//            afterScenario = result.remove(VAR_AFTER_SCENARIO);
//            configResponseHeadersMap = configResponseHeaders == null ? null : configResponseHeaders.evalAsMap(context);
//            responseHeadersMap = responseHeaders == null ? null : responseHeaders.evalAsMap(context);
//        } // END TRANSACTION !!
//        
//        // Handle the Karate specified status
//        Http2Headers headers = new DefaultHttp2Headers();
//        
//        if (responseStatus == null) {
//        	headers.status(HttpResponseStatus.OK.codeAsText());
//        } else {
//        	HttpResponseStatus httpResponseStatus = HttpResponseStatus.valueOf(Integer.valueOf(responseStatus.getValue().toString()));
//        	headers.status(httpResponseStatus.codeAsText());
//        }
//        
//        // Handle Karate specified response headers
////        // trying to avoid creating a map unless absolutely necessary
////        Map<String, Object> headers = null;
////        if (responseHeadersMap != null) {
////            Map<String, Object> temp = new LinkedHashMap(responseHeadersMap.size());
////            responseHeadersMap.forEach((k, v) -> {
////                if (v instanceof List) { // MultiValueMap returned by proceed / response.headers
////                    List values = (List) v;
////                    temp.put(k, StringUtils.join(values, ','));
////                } else {
////                    temp.put(k, v);
////                }
////            });
////            headers = temp;
////        }
////        if (configResponseHeadersMap != null) {
////            if (headers == null) {
////                headers = configResponseHeadersMap;
////            } else {
////                headers.putAll(configResponseHeadersMap);
////            }
////        }
////        if (headers != null) {
////            headers.forEach((k, v) -> response.headers().set(k, v));
////        }
////        if (responseValue != null && (headers == null || !headers.containsKey(HttpUtils.HEADER_CONTENT_TYPE))) {
////            response.headers().set(HttpUtils.HEADER_CONTENT_TYPE, HttpUtils.getContentType(responseValue));
////        }
////        if (provider.isCorsEnabled()) {
////            response.headers().set(HttpUtils.HEADER_AC_ALLOW_ORIGIN, "*");
////        }        
//        
//        // Handle Karate specified response body
//        if (responseValue != null){
//            ByteBuf responseBuf;
//            if (responseValue.getType() == ScriptValue.Type.BYTE_ARRAY) {
//                responseBuf = Unpooled.copiedBuffer(responseValue.getValue(byte[].class));
//            } else {
//                responseBuf = Unpooled.copiedBuffer(responseValue.getAsString(), CharsetUtil.UTF_8);
//            }
//            provider.getContext().logger.debug("writeResponse response body: {}", FileUtils.toString(responseBuf.array()));
//            headers.set(HttpHeaderNames.CONTENT_LENGTH, responseBuf.readableBytes());
//            ctx.write(new DefaultHttp2DataFrame(responseBuf, true));
//        }
//        ctx.write(new DefaultHttp2HeadersFrame(headers));
//        
//        ctx.flush();
//
//        // functions here are outside of the 'transaction' and should not mutate global state !
//        // typically this is where users can set up an artificial delay or sleep
//        if (afterScenario != null && afterScenario.isFunction()) {
//            afterScenario.invokeFunction(provider.getContext());
//        }
//    }
}
