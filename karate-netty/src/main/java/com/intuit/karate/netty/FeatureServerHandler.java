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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.handler.codec.http.HttpUtil;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;

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
        HttpHeaders headers = msg.headers();
        if (!headers.isEmpty()) {
            headers.forEach(h -> {
                CharSequence key = h.getKey();
                CharSequence value = h.getValue();
                request.addHeader(key.toString(), value.toString());
            });
        }
        HttpContent httpContent = (HttpContent) msg;
        ByteBuf content = httpContent.content();
        if (content.isReadable()) {   
            byte[] bytes = new byte[content.readableBytes()];
            content.readBytes(bytes);
            request.setBody(bytes);
        }
        if (!writeResponse(msg, request, ctx)) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }           
    }
    
    private final StringBuilder sb = new StringBuilder();    

    private boolean writeResponse(HttpMessage nettyRequest, HttpRequest request, ChannelHandlerContext ctx) {
        sb.setLength(0);
        String requestUri = request.getUri();
        QueryStringDecoder qsDecoder = new QueryStringDecoder(requestUri);        
        Match match = Match.init()
                .defText(ScriptValueMap.VAR_REQUEST_URI, requestUri)
                .defText(ScriptValueMap.VAR_REQUEST_METHOD, request.getMethod())                
                .def(ScriptValueMap.VAR_REQUEST_PARAMS, qsDecoder.parameters());                
        if (request.getBody() != null) {
            String requestBody = FileUtils.toString(request.getBody());
            match.def(ScriptValueMap.VAR_REQUEST, requestBody);
        }
        ScriptValueMap result = provider.handle(match.allAsMap());
        ScriptValue responseValue = result.get(ScriptValueMap.VAR_RESPONSE);
        boolean keepAlive = HttpUtil.isKeepAlive(nettyRequest);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, nettyRequest.decoderResult().isSuccess()? OK : BAD_REQUEST,
                Unpooled.copiedBuffer(responseValue.getAsString(), CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpUtils.getContentType(responseValue));
        if (keepAlive) {
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        ctx.write(response);
        return keepAlive;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }    
    
}
