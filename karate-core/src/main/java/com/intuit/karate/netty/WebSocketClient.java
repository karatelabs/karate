/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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

import com.intuit.karate.Logger;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.URI;
import java.util.Map;
import java.util.function.Function;
import javax.net.ssl.SSLException;

/**
 *
 * @author pthomas3
 */
public class WebSocketClient implements WebSocketListener {

    // mutable
    private Logger logger;

    private final Channel channel;
    private final EventLoopGroup group;

    private final URI uri;
    private final int port;
    private final SslContext sslContext;
    private final WebSocketClientHandler handler;

    private Function<String, Boolean> textHandler;
    private Function<byte[], Boolean> binaryHandler;

    @Override
    public void onMessage(String text) {
        if (textHandler != null) {
            if (textHandler.apply(text)) {
                signal(text);
            }
        }
    }

    @Override
    public void onMessage(byte[] bytes) {
        if (binaryHandler != null) {
            if (binaryHandler.apply(bytes)) {
                signal(bytes);
            }
        }
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public WebSocketClient(WebSocketOptions options, Logger logger) {
        this.logger = logger;
        textHandler = options.getTextHandler();
        binaryHandler = options.getBinaryHandler();
        uri = options.getUri();
        port = options.getPort();
        group = new NioEventLoopGroup();
        if (options.isSsl()) {
            try {
                sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            } catch (SSLException e) {
                throw new RuntimeException(e);
            }
        } else {
            sslContext = null;
        }
        HttpHeaders nettyHeaders = new DefaultHttpHeaders();
        Map<String, Object> headers = options.getHeaders();
        if (headers != null) {
            headers.forEach((k, v) -> nettyHeaders.add(k, v));
        }
        WebSocketClientHandshaker handShaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, options.getSubProtocol(), true, nettyHeaders, options.getMaxPayloadSize());
        handler = new WebSocketClientHandler(handShaker, this);
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel c) {
                            ChannelPipeline p = c.pipeline();
                            if (sslContext != null) {
                                p.addLast(sslContext.newHandler(c.alloc(), uri.getHost(), port));
                            }
                            p.addLast(new HttpClientCodec());
                            p.addLast(new HttpObjectAggregator(8192));
                            p.addLast(WebSocketClientCompressionHandler.INSTANCE);
                            p.addLast(handler);
                        }
                    });
            channel = b.connect(options.getUri().getHost(), options.getPort()).sync().channel();
            handler.handshakeFuture().sync();
        } catch (Exception e) {
            logger.error("websocket client init failed: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void setBinaryHandler(Function<byte[], Boolean> binaryHandler) {
        this.binaryHandler = binaryHandler;
    }

    public void setTextHandler(Function<String, Boolean> textHandler) {
        this.textHandler = textHandler;
    }

    private boolean waiting;

    public void waitSync() {
        if (waiting) {
            return;
        }
        try {
            waiting = true;
            channel.closeFuture().sync();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        channel.writeAndFlush(new CloseWebSocketFrame());
        waitSync();
        group.shutdownGracefully();
    }

    public void ping() {
        WebSocketFrame frame = new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[]{8, 1, 8, 1}));
        channel.writeAndFlush(frame);
    }

    public void send(String msg) {
        WebSocketFrame frame = new TextWebSocketFrame(msg);
        channel.writeAndFlush(frame);
        if (logger.isTraceEnabled()) {
            logger.trace("sent: {}", msg);
        }
    }

    public void sendBytes(byte[] msg) {
        ByteBuf byteBuf = Unpooled.copiedBuffer(msg);
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(byteBuf);
        channel.writeAndFlush(frame);
    }

    private final Object LOCK = new Object();
    private Object signalResult;

    public void signal(Object result) {
        logger.trace("signal called: {}", result);
        synchronized (LOCK) {
            signalResult = result;
            LOCK.notify();
        }
    }

    public Object listen(long timeout) {
        synchronized (LOCK) {
            if (signalResult != null) {
                logger.debug("signal arrived early ! result: {}", signalResult);
                Object temp = signalResult;
                signalResult = null;
                return temp;
            }
            try {
                logger.trace("entered listen wait state");
                LOCK.wait(timeout);
                logger.trace("exit listen wait state, result: {}", signalResult);
            } catch (InterruptedException e) {
                logger.error("listen timed out: {}", e.getMessage());
            }
            Object temp = signalResult;
            signalResult = null;
            return temp;
        }
    }

}
