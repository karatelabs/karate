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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Netty handler for WebSocket client connections.
 * Public for extension by subclasses.
 */
public class WsClientHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger logger = LoggerFactory.getLogger(WsClientHandler.class);

    protected final WsClient client;
    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;

    public WsClientHandler(WsClient client, WebSocketClientHandshaker handshaker) {
        this.client = client;
        this.handshaker = handshaker;
    }

    public ChannelPromise getHandshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.debug("websocket disconnected: {}", client.getUri());
        client.handleDisconnect();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(ctx.channel(), (FullHttpResponse) msg);
                logger.debug("websocket handshake complete: {}", client.getUri());
                handshakeFuture.setSuccess();
            } catch (WebSocketHandshakeException e) {
                logger.error("websocket handshake failed: {}", e.getMessage());
                handshakeFuture.setFailure(e);
            }
            return;
        }

        if (msg instanceof FullHttpResponse response) {
            throw new IllegalStateException(
                    "unexpected FullHttpResponse (status=" + response.status() +
                            ", content=" + response.content().toString(StandardCharsets.UTF_8) + ")");
        }

        WebSocketFrame frame = (WebSocketFrame) msg;

        if (frame instanceof TextWebSocketFrame textFrame) {
            onTextFrame(ctx, textFrame);
        } else if (frame instanceof BinaryWebSocketFrame binaryFrame) {
            onBinaryFrame(ctx, binaryFrame);
        } else if (frame instanceof PingWebSocketFrame pingFrame) {
            onPingFrame(ctx, pingFrame);
        } else if (frame instanceof PongWebSocketFrame pongFrame) {
            onPongFrame(ctx, pongFrame);
        } else if (frame instanceof CloseWebSocketFrame closeFrame) {
            onCloseFrame(ctx, closeFrame);
        }
    }

    protected void onTextFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String text = frame.text();
        logger.trace("received text frame: {}", text.length() > 100 ? text.substring(0, 100) + "..." : text);
        client.handleFrame(WsFrame.text(text));
    }

    protected void onBinaryFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        byte[] bytes = new byte[frame.content().readableBytes()];
        frame.content().readBytes(bytes);
        logger.trace("received binary frame: {} bytes", bytes.length);
        client.handleFrame(WsFrame.binary(bytes));
    }

    protected void onPingFrame(ChannelHandlerContext ctx, PingWebSocketFrame frame) {
        logger.trace("received ping, sending pong");
        ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
    }

    protected void onPongFrame(ChannelHandlerContext ctx, PongWebSocketFrame frame) {
        logger.trace("received pong");
        client.handleFrame(WsFrame.pong());
    }

    protected void onCloseFrame(ChannelHandlerContext ctx, CloseWebSocketFrame frame) {
        int code = frame.statusCode();
        String reason = frame.reasonText();
        logger.debug("received close frame: {} {}", code, reason);
        client.handleFrame(WsFrame.close(code, reason));
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("websocket error: {}", cause.getMessage());
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        client.handleError(cause);
        ctx.close();
    }

}
