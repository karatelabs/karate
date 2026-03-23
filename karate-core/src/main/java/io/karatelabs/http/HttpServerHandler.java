/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    static final Logger logger = LoggerFactory.getLogger(HttpServerHandler.class);

    final HttpServer server;

    HttpServerHandler(HttpServer server) {
        this.server = server;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        HttpRequest request = toRequest(req);
        try {
            HttpResponse response = server.handler.apply(request);
            FullHttpResponse res = toResponse(response);
            int delay = response.getDelay();
            if (delay > 0) {
                // Use Netty's non-blocking scheduler for delay
                ctx.executor().schedule(() -> ctx.writeAndFlush(res), delay, TimeUnit.MILLISECONDS);
            } else {
                ctx.writeAndFlush(res);
            }
        } catch (Exception e) {
            String message = e.getMessage();
            logger.error("http server error: {}", message);
            ctx.writeAndFlush(error(message));
        }
    }

    static HttpRequest toRequest(FullHttpRequest req) {
        HttpRequest request = new HttpRequest();
        request.setUrl(req.uri());
        request.setMethod(req.method().name());
        HttpHeaders hh = req.headers();
        Map<String, List<String>> headers = new HashMap<>(hh.size());
        hh.forEach(entry -> {
            List<String> list = headers.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
            list.add(entry.getValue());
        });
        request.setHeaders(headers);
        // Build urlBase from Host header for server-side templates
        String host = hh.get("Host");
        if (host != null) {
            String proto = hh.get("X-Forwarded-Proto");
            if (proto == null) {
                proto = "http";
            }
            request.setUrlBase(proto + "://" + host);
        }
        ByteBuf buf = req.content();
        int len = buf.readableBytes();
        if (len > 0) {
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            request.setBody(bytes);
        }
        return request;
    }

    static FullHttpResponse toResponse(HttpResponse response) {
        HttpResponseStatus status = HttpResponseStatus.valueOf(response.getStatus());
        FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        Map<String, List<String>> headers = response.getHeaders();
        if (headers != null) {
            headers.forEach((k, list) -> {
                for (String v : list) {
                    res.headers().add(k, v);
                }
            });
        }
        byte[] bytes = response.getBodyBytes();
        if (bytes != null) {
            ByteBuf content = Unpooled.copiedBuffer(bytes);
            res.content().writeBytes(content);
        }
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes == null ? 0 : bytes.length);
        return res;
    }

    static FullHttpResponse error(String message) {
        FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        ByteBuf content = Unpooled.copiedBuffer(message.getBytes(StandardCharsets.UTF_8));
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        res.content().writeBytes(content);
        return res;
    }

}
