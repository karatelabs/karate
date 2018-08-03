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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.util.CharsetUtil;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

import java.net.URI;
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

//    @Override
//    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
//        int processed = data.readableBytes() + padding;
//        
//   		logger.info("ConnectionHandler onDataRead: StreamId({}) endOfStream({}) ", streamId, endOfStream);
//   		logger.info("ConnectionHandler onDataRead: data({})) ", data);
//   		
//   		Http2Stream inStream = decoder().connection().stream(streamId);
//   		FullHttpRequest request = getRequest(inStream);
//        writeResponse(toHttpRequest(request), ctx);
//
//		logger.info("ConnectionHandler onDataRead: Request: request({}) ", request);
//   		
////   		DefaultHttp2DataFrame frame = new DefaultHttp2DataFrame(data,endOfStream,padding);   		
////        ctx.fireChannelRead(frame);
//   		
//        return processed;
//    }
//
//    @Override
//    public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
//                              Http2Headers headers, int padding, boolean endOfStream) throws Http2Exception {
//   		logger.info("ConnectionHandler onHeadersRead: Method({}) StreamId({}) endOfStream({}) ", headers.method(), streamId, endOfStream);
//   		
//   		//Http2HeadersFrame frame = new DefaultHttp2HeadersFrame(headers,endOfStream,padding);   		
//        //ctx.fireChannelRead(frame);
//   		Http2Stream inStream = decoder().connection().stream(streamId);
//   		FullHttpRequest request = getRequest(inStream);
//   		if (request == null){
//   			request = HttpConversionUtil.toFullHttpRequest(streamId, headers, ctx.alloc(), false);
//   			putRequest(inStream, request);
//  		}
//   		else {
//   			HttpConversionUtil.addHttp2ToHttpHeaders(streamId, headers, request, true);
//   			putRequest(inStream, request);
//   		}
//   		
//		logger.info("ConnectionHandler onHeadersRead: Request: request({}) ", request);
//    }


//    private final FullHttpRequest getRequest(Http2Stream stream) {
//        return (FullHttpRequest) stream.getProperty(messageKey);
//    }
//
//
//    private final void putRequest(Http2Stream stream, FullHttpRequest request) {
//    	FullHttpRequest previous = stream.setProperty(messageKey, request);
//        if (previous != request && previous != null) {
//            previous.release();
//        }
//    }
//    @Override
//    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
//                              short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
//   		logger.info("*****ConnectionHandler onHeadersRead: Method({}) StreamId({}) endOfStream({}) ", headers.method(), streamId, endOfStream);
//        onHeadersRead(ctx, streamId, headers, padding, endOfStream);
//    }
//
//    @Override
//    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
//                               short weight, boolean exclusive) {
//    }
//
//    @Override
//    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
//    }
//
//    @Override
//    public void onSettingsAckRead(ChannelHandlerContext ctx) {
//    }
//
//    @Override
//    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
//    }
//
//    @Override
//    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
//                                  Http2Headers headers, int padding) {
//    }
//
//    @Override
//    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {
//    }
//
//    @Override
//    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
//    }
//
//    @Override
//    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
//                               Http2Flags flags, ByteBuf payload) {
//    }
//
//	@Override
//	public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
//		// TODO Auto-generated method stub
//		
//	}
//
//	@Override
//	public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
//		// TODO Auto-generated method stub
//		
//	}
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
    
//    static final ByteBuf RESPONSE_BYTES = unreleasableBuffer(copiedBuffer("Hello World", CharsetUtil.UTF_8));
//
//    /**
//     * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via HTTP/2
//     * on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
//     */
//    @Override
//    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
//        if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
//            HttpServerUpgradeHandler.UpgradeEvent upgradeEvent =
//                    (HttpServerUpgradeHandler.UpgradeEvent) evt;
//            onHeadersRead(ctx, 1, http1HeadersToHttp2Headers(upgradeEvent.upgradeRequest()), 0 , true);
//        }
//        super.userEventTriggered(ctx, evt);
//    }
//
//    @Override
//    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
//        super.exceptionCaught(ctx, cause);
//        cause.printStackTrace();
//        ctx.close();
//    }
//
//    /**
//     * Sends a "Hello World" DATA frame to the client.
//     */
//    private void sendResponse(ChannelHandlerContext ctx, int streamId, ByteBuf payload) {
//        // Send a frame for the response status
//        Http2Headers headers = new DefaultHttp2Headers().status(OK.codeAsText());
//        encoder().writeHeaders(ctx, streamId, headers, 0, false, ctx.newPromise());
//        encoder().writeData(ctx, streamId, payload, 0, true, ctx.newPromise());
//
//        // no need to call flush as channelReadComplete(...) will take care of it.
//    }
//
//    @Override
//    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
//        int processed = data.readableBytes() + padding;
//        if (endOfStream) {
//            sendResponse(ctx, streamId, data.retain());
//        }
//        return processed;
//    }
//
//    @Override
//    public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
//                              Http2Headers headers, int padding, boolean endOfStream) {
//        if (endOfStream) {
//            ByteBuf content = ctx.alloc().buffer();
//            content.writeBytes(RESPONSE_BYTES.duplicate());
//            ByteBufUtil.writeAscii(content, " - via HTTP/2");
//            sendResponse(ctx, streamId, content);
//        }
//    }
}
