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

import com.intuit.karate.StringUtils;
import com.intuit.karate.core.FeaturesBackend;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.MultiValuedMap;
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
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.MDC;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 *
 * @author pthomas3
 */
public class FeatureServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final FeaturesBackend backend;
    private final Runnable stopFunction;
    private final boolean ssl;
    private final Supplier<SslContext> contextSupplier;

    public FeatureServerHandler(FeaturesBackend backend, boolean ssl, Supplier<SslContext> contextSupplier, Runnable stopFunction) {
        this.backend = backend;
        this.ssl = ssl;
        this.contextSupplier = contextSupplier;
        this.stopFunction = stopFunction;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private static final String STOP_URI = "/__admin/stop";

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
        long startTime = System.currentTimeMillis();
        String requestId = System.identityHashCode(msg) + ""; //Simple requestId based on distinct JVM objectId
        MDC.put("karateRequestId", requestId);
        backend.getContext().logger.debug("handling method: {}, uri: {}", msg.method(), msg.uri());
        FullHttpResponse nettyResponse;
        long delay = 0L;
        if (msg.uri().startsWith(STOP_URI)) {
            backend.getContext().logger.info("stop uri invoked, shutting down");
            ByteBuf responseBuf = Unpooled.copiedBuffer("stopped", CharsetUtil.UTF_8);
            nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, responseBuf);
            stopFunction.run();
        } else if (HttpMethod.CONNECT.equals(msg.method())) { // HTTPS proxy
            SslContext sslContext = contextSupplier.get();
            SslHandler sslHandler = sslContext.newHandler(ctx.alloc());
            FullHttpResponse response = NettyUtils.connectionEstablished();
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response).addListener(l -> ctx.channel().pipeline().addFirst(sslHandler));
            // do NOT close channel
            return;
        } else {
            StringUtils.Pair url = NettyUtils.parseUriIntoUrlBaseAndPath(msg.uri());
            HttpRequest request = new HttpRequest();
            if (url.left == null) {
                String requestScheme = ssl ? "https" : "http";
                String host = msg.headers().get(HttpHeaderNames.HOST);
                request.setUrlBase(requestScheme + "://" + host);
            } else {
                request.setUrlBase(url.left);
            }
            request.setUri(url.right);
            request.setMethod(msg.method().name());
            request.setRequestId(requestId);
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
            HttpResponse response = backend.buildResponse(request, startTime);
            HttpResponseStatus httpResponseStatus = HttpResponseStatus.valueOf(response.getStatus());
            byte[] responseBody = response.getBody();
            if (responseBody != null) {
                ByteBuf responseBuf = Unpooled.copiedBuffer(responseBody);
                nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, httpResponseStatus, responseBuf);
            } else {
                nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, httpResponseStatus);
            }
            MultiValuedMap karateHeaders = response.getHeaders();
            if (karateHeaders != null) {
                HttpHeaders nettyHeaders = nettyResponse.headers();
                karateHeaders.forEach((k, v) -> nettyHeaders.add(k, v));
            }
            if (response.getDelay() != 0L) {
                delay = response.getDelay() - response.getResponseTime();
            }
        }
        ctx.executor().schedule(() -> {
            ctx.write(nettyResponse);
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }, delay, TimeUnit.MILLISECONDS)
                .addListener((future) -> backend.getContext().logger.debug("response {} written after {} milliseconds", future.isSuccess() ? "successfully" : "not", System.currentTimeMillis() - startTime));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause.getMessage() == null) {
            cause.printStackTrace();
        } else {
            backend.getContext().logger.error("closing connection: {}", cause.getMessage());
        }
        ctx.close();
    }

}
