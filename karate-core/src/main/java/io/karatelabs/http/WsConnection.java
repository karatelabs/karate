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
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * WebSocket connection wrapping a Netty channel.
 * Provides methods to send messages, register callbacks for incoming messages,
 * and manage the connection lifecycle.
 */
public class WsConnection {

    private static final Logger logger = LoggerFactory.getLogger(WsConnection.class);

    private final ChannelHandlerContext ctx;
    private volatile boolean open;
    private volatile Consumer<String> messageHandler;
    private volatile Consumer<byte[]> binaryHandler;

    WsConnection(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        this.open = true;
    }

    /**
     * Send a text message.
     */
    public void send(String text) {
        if (!isOpen()) return;
        ctx.writeAndFlush(new TextWebSocketFrame(text));
    }

    /**
     * Send a binary message.
     */
    public void sendBytes(byte[] data) {
        if (!isOpen()) return;
        ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)));
    }

    /**
     * Register a callback for incoming text messages.
     */
    public void onMessage(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    /**
     * Register a callback for incoming binary messages.
     */
    public void onBinary(Consumer<byte[]> handler) {
        this.binaryHandler = handler;
    }

    /**
     * Register a callback for when the connection closes.
     */
    public void onClose(Runnable callback) {
        ctx.channel().closeFuture().addListener(future -> {
            open = false;
            callback.run();
        });
    }

    /**
     * Close the WebSocket connection with a normal closure.
     */
    public void close() {
        close(1000, null);
    }

    /**
     * Close the WebSocket connection with a specific status code and reason.
     */
    public void close(int code, String reason) {
        if (!open) return;
        open = false;
        ctx.writeAndFlush(new CloseWebSocketFrame(code, reason))
                .addListener(future -> ctx.close());
    }

    public boolean isOpen() {
        return open && ctx.channel().isActive();
    }

    // -- package-private callbacks invoked by WsServerHandler --

    void handleText(String text) {
        Consumer<String> handler = messageHandler;
        if (handler != null) {
            try {
                handler.accept(text);
            } catch (Exception e) {
                logger.error("ws message handler error: {}", e.getMessage());
            }
        }
    }

    void handleBinary(byte[] data) {
        Consumer<byte[]> handler = binaryHandler;
        if (handler != null) {
            try {
                handler.accept(data);
            } catch (Exception e) {
                logger.error("ws binary handler error: {}", e.getMessage());
            }
        }
    }

    void handleClose() {
        open = false;
    }

}
