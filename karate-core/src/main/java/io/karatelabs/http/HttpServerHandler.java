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
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
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
        HttpRequest request;
        try {
            request = toRequest(req);
        } catch (Exception e) {
            // A malformed REQUEST LINE fails here, before any handler exists to answer it — a bad
            // percent-escape in the query string (`?state=%zz`) makes Netty's QueryStringDecoder throw
            // while we are still building the request. This used to escape channelRead0 into the pipeline
            // tail, which logs the exception and writes NOTHING: the client then waits for a response that
            // will never come, while the server happily serves every other connection. A hung request is
            // worse than any wrong status, so say 400 — the client sent something we cannot parse.
            logger.warn("bad request '{}': {}", req.uri(), e.getMessage());
            ctx.writeAndFlush(error(HttpResponseStatus.BAD_REQUEST,
                    "cannot parse the request URI: " + e.getMessage()));
            return;
        }
        if (server.wsHandler != null && isWsUpgrade(req)) {
            try {
                handleWsUpgrade(ctx, req, request);
            } catch (Exception e) {
                String message = e.getMessage();
                logger.error("ws upgrade error: {}", message);
                ctx.writeAndFlush(error(message));
            }
            return;
        }
        if (server.sseHandler != null && isSseRequest(req)) {
            try {
                SseConnection connection = new SseConnection(ctx);
                server.sseHandler.accept(request, connection);
            } catch (Exception e) {
                String message = e.getMessage();
                logger.error("sse handler error: {}", message);
                ctx.writeAndFlush(error(message));
            }
            return;
        }
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

    static boolean isSseRequest(FullHttpRequest req) {
        // SSE per the EventSource spec is GET-only. Other methods (e.g. MCP
        // Streamable HTTP POSTs that advertise text/event-stream so the server
        // *may* upgrade) must reach the regular handler, which decides whether
        // to return JSON or open an SSE stream.
        if (!HttpMethod.GET.equals(req.method())) {
            return false;
        }
        String accept = req.headers().get("Accept");
        return accept != null && accept.contains("text/event-stream");
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
        String statusText = response.getStatusText();
        HttpResponseStatus status = statusText == null
                ? HttpResponseStatus.valueOf(response.getStatus())
                : HttpResponseStatus.valueOf(response.getStatus(), statusText);
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
        return error(HttpResponseStatus.INTERNAL_SERVER_ERROR, message);
    }

    static FullHttpResponse error(HttpResponseStatus status, String message) {
        FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        // never NPE while building the apology: an exception with a null message is ordinary (an NPE, an
        // IllegalStateException with no text), and every caller here passes `e.getMessage()` straight in.
        // Throwing from the error path leaves the request unanswered, which is the failure this response
        // exists to prevent.
        String text = message == null ? String.valueOf(status.reasonPhrase()) : message;
        ByteBuf content = Unpooled.copiedBuffer(text.getBytes(StandardCharsets.UTF_8));
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        res.content().writeBytes(content);
        return res;
    }

    /**
     * The backstop: <b>no request leaves this server unanswered.</b> Netty's default sends an uncaught
     * exception to the pipeline tail, which logs it and writes nothing — so a client waits out its own
     * timeout while the server looks healthy. Anything that reaches here is a bug we have not handled
     * above, and the honest answer is a 500 and a closed connection rather than silence.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("http server pipeline error: {}", cause.toString());
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(error(cause.getMessage()))
                    .addListener(io.netty.channel.ChannelFutureListener.CLOSE);
        } else {
            ctx.close();
        }
    }

    static boolean isWsUpgrade(FullHttpRequest req) {
        String upgrade = req.headers().get(HttpHeaderNames.UPGRADE);
        return upgrade != null && "websocket".equalsIgnoreCase(upgrade);
    }

    private void handleWsUpgrade(ChannelHandlerContext ctx, FullHttpRequest req, HttpRequest request) {
        String wsUrl = "ws://" + req.headers().get(HttpHeaderNames.HOST) + req.uri();
        // 1 MB receive cap (Netty defaults to 64k) — proxied streams (e.g. a VNC clipboard paste
        // through the websockify tunnel) can legitimately exceed the default
        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(wsUrl, null, true, HttpUtils.MEGABYTE);
        WebSocketServerHandshaker handshaker = factory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            return;
        }
        WsConnection connection = new WsConnection(ctx);
        handshaker.handshake(ctx.channel(), req).addListener(future -> {
            if (future.isSuccess()) {
                ChannelPipeline p = ctx.pipeline();
                p.addLast(new WsServerHandler(connection));
                server.wsHandler.accept(request, connection);
            } else {
                logger.error("ws handshake failed: {}", future.cause().getMessage());
                ctx.close();
            }
        });
    }

}
