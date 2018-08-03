/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.netty;

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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class FeatureServerHandlerHttp1 extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final FeatureProvider provider;
    private final Runnable stopFunction;

    public FeatureServerHandlerHttp1(FeatureProvider provider, Runnable stopFunction) {
        this.provider = provider;
        this.stopFunction = stopFunction;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        provider.getContext().logger.debug("FeatureServerHandlerHttp1 channelReadComplete: {}", ctx);
        ctx.flush();
    }

    private static final String STOP_URI = "/__admin/stop";
    private static final String ALLOWED_METHODS = "GET, HEAD, POST, PUT, DELETE, PATCH";

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
        provider.getContext().logger.debug("handling method: {}, uri: {}", msg.method(), msg.uri());
        if (msg.uri().startsWith(STOP_URI)) {
            provider.getContext().logger.info("stop uri invoked, shutting down");
            ByteBuf responseBuf = Unpooled.copiedBuffer("stopped", CharsetUtil.UTF_8);
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, responseBuf);            
            ctx.write(response);
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            stopFunction.run();
        }
        if (provider.isCorsEnabled() && msg.method().equals(HttpMethod.OPTIONS)) {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            HttpHeaders responseHeaders = response.headers();
            responseHeaders.set(HttpUtils.HEADER_ALLOW, ALLOWED_METHODS);
            responseHeaders.set(HttpUtils.HEADER_AC_ALLOW_ORIGIN, "*");
            responseHeaders.set(HttpUtils.HEADER_AC_ALLOW_METHODS, ALLOWED_METHODS);
            String requestHeaders = msg.headers().get(HttpUtils.HEADER_AC_REQUEST_HEADERS);
            if (requestHeaders != null) {
                responseHeaders.set(HttpUtils.HEADER_AC_ALLOW_HEADERS, requestHeaders);
            }
            ctx.write(response);
        } else {
            StringUtils.Pair url = HttpUtils.parseUriIntoUrlBaseAndPath(msg.uri());
            HttpRequest request = new HttpRequest();
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
            writeResponse(request, ctx);
        }
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    private static final String VAR_AFTER_SCENARIO = "afterScenario";

    private void writeResponse(HttpRequest request, ChannelHandlerContext ctx) {
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
        ctx.write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        provider.getContext().logger.error("server error, {}", cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }

}
