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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Server-Sent Events connection wrapping a Netty channel.
 * Sends the initial HTTP response headers with chunked transfer encoding,
 * then allows sending individual SSE events as data frames.
 */
public class SseConnection {

    private static final Logger logger = LoggerFactory.getLogger(SseConnection.class);

    private final ChannelHandlerContext ctx;
    private volatile boolean open;

    SseConnection(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        this.open = true;
        sendHeaders();
    }

    private void sendHeaders() {
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        response.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        ctx.writeAndFlush(response);
    }

    /**
     * Send an SSE event with the given event name and data.
     *
     * @param event the event name (appears as "event: name" in the stream)
     * @param data  the event data (appears as "data: ..." lines)
     */
    public void send(String event, String data) {
        if (!open || !ctx.channel().isActive()) {
            open = false;
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (event != null) {
            sb.append("event: ").append(event).append('\n');
        }
        if (data != null) {
            for (String line : data.split("\n", -1)) {
                sb.append("data: ").append(line).append('\n');
            }
        }
        sb.append('\n');
        ctx.writeAndFlush(new DefaultHttpContent(
                Unpooled.copiedBuffer(sb.toString(), StandardCharsets.UTF_8)));
    }

    /**
     * Send an SSE event with data only (no event name).
     */
    public void send(String data) {
        send(null, data);
    }

    /**
     * Send an SSE comment (line starting with colon). Useful as a keep-alive.
     */
    public void sendComment(String comment) {
        if (!open || !ctx.channel().isActive()) {
            open = false;
            return;
        }
        String frame = ": " + comment + "\n\n";
        ctx.writeAndFlush(new DefaultHttpContent(
                Unpooled.copiedBuffer(frame, StandardCharsets.UTF_8)));
    }

    /**
     * Close the SSE connection by sending the last chunk and closing the channel.
     */
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                .addListener(future -> ctx.close());
    }

    public boolean isOpen() {
        return open && ctx.channel().isActive();
    }

    /**
     * Register a callback for when the client disconnects.
     */
    public void onDisconnect(Runnable callback) {
        ctx.channel().closeFuture().addListener(future -> {
            open = false;
            callback.run();
        });
    }

}
