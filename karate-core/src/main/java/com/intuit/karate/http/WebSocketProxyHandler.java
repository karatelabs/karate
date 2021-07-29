/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class WebSocketProxyHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketProxyHandler.class);

    private final com.intuit.karate.Logger logger = new com.intuit.karate.Logger();
    private WebSocketClient client;
    private final WebSocketOptions options;

    public WebSocketProxyHandler(WebSocketOptions options) {
        this.options = options;
    }

    private Channel channel;

    private void initClient() {
        client = new WebSocketClient(options, logger) {
            @Override
            public void onMessage(String text) {
                LOGGER.debug("<< {}", text);
                channel.eventLoop().submit(() -> {
                    channel.writeAndFlush(new TextWebSocketFrame(text));
                });
            }
        };
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();
        initClient();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof TextWebSocketFrame) {
            String request = ((TextWebSocketFrame) msg).text();
            LOGGER.debug(">> {}", request);
            client.send(request);
        } else if (msg instanceof FullHttpRequest) {
            client.ping();
//            initClient();
//            FullHttpRequest request = (FullHttpRequest) msg;
//            request.retain();
//            client.sendHttpRequest(request);
        } else {
            String message = "unsupported frame type: " + msg.getClass().getName();
            LOGGER.warn(message);
        }
    }

}
