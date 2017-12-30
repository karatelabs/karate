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
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValueMap;
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
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import java.util.Collections;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class FeatureServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final FeatureProvider provider;

    public FeatureServerHandler(FeatureProvider provider) {
        this.provider = provider;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
        HttpRequest request = new HttpRequest();
        request.setUri(msg.uri());
        request.setMethod(msg.method().name());
        msg.headers().forEach(h -> request.addHeader(h.getKey(), h.getValue()));
        HttpContent httpContent = (HttpContent) msg;
        ByteBuf content = httpContent.content();
        if (content.isReadable()) {
            byte[] bytes = new byte[content.readableBytes()];
            content.readBytes(bytes);
            request.setBody(bytes);
        }
        writeResponse(msg, request, ctx);
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    private final StringBuilder sb = new StringBuilder();

    private void writeResponse(HttpMessage nettyRequest, HttpRequest request, ChannelHandlerContext ctx) {
        sb.setLength(0);
        String requestUri = request.getUri();
        QueryStringDecoder qsDecoder = new QueryStringDecoder(requestUri);
        Match match = Match.init()
                .defText(ScriptValueMap.VAR_REQUEST_URI, requestUri)
                .defText(ScriptValueMap.VAR_REQUEST_METHOD, request.getMethod())
                .def(ScriptValueMap.VAR_REQUEST_HEADERS, request.getHeaders())
                .def(ScriptValueMap.VAR_RESPONSE_STATUS, 200)
                .def(ScriptValueMap.VAR_REQUEST_PARAMS, qsDecoder.parameters());
        if (request.getBody() != null) {
            String requestBody = FileUtils.toString(request.getBody());
            match.def(ScriptValueMap.VAR_REQUEST, requestBody);
        }
        ScriptValueMap result = provider.handle(match.vars());
        ScriptValue responseValue = result.get(ScriptValueMap.VAR_RESPONSE);
        ScriptValue responseStatus = result.get(ScriptValueMap.VAR_RESPONSE_STATUS);
        HttpResponseStatus nettyResponseStatus;
        if (responseStatus == null) {
            nettyResponseStatus = OK;
        } else {
            nettyResponseStatus = HttpResponseStatus.valueOf(Integer.valueOf(responseStatus.getValue().toString()));
        }
        FullHttpResponse response;
        if (responseValue == null) {
            response = new DefaultFullHttpResponse(HTTP_1_1, nettyResponseStatus);
        } else {
            ByteBuf responseBuf;
            if (responseValue.getType() == ScriptValue.Type.BYTE_ARRAY) {
                responseBuf = Unpooled.copiedBuffer(responseValue.getValue(byte[].class));
            } else {
                responseBuf = Unpooled.copiedBuffer(responseValue.getAsString(), CharsetUtil.UTF_8);
            }
            response = new DefaultFullHttpResponse(HTTP_1_1, nettyResponseStatus, responseBuf);
        }
        ScriptValue responseHeaders = result.get(ScriptValueMap.VAR_RESPONSE_HEADERS);
        Map<String, Object> headersMap = responseHeaders == null ? Collections.EMPTY_MAP : responseHeaders.evalAsMap(provider.getContext());
        headersMap.forEach((k, v) -> response.headers().set(k, v));
        if (!headersMap.containsKey(HttpUtils.HEADER_CONTENT_TYPE) && responseValue != null) {
            response.headers().set(HttpUtils.HEADER_CONTENT_TYPE, HttpUtils.getContentType(responseValue));
        }
        ctx.write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

}
