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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.URI;
import java.util.function.Consumer;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class WebSocketClient implements WebSocketListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketClient.class);

    private final URI uri;
    private final int port;
    private final boolean ssl;
    private final Channel channel;
    private final EventLoopGroup group;
    
    private Consumer<String> textHandler;
    private Consumer<byte[]> binaryHandler;
    
    @Override
    public void onMessage(String text) {
        if (textHandler != null) {
            textHandler.accept(text);
        }
    }

    @Override
    public void onMessage(byte[] bytes) {
        if (binaryHandler != null) {
            binaryHandler.accept(bytes);
        }
    }    
    
    private boolean waiting;
    
    public WebSocketClient(String url, Consumer<String> textHandler) {
        this(url, textHandler, null);
    }

    public WebSocketClient(String url, Consumer<String> textHandler, Consumer<byte[]> binaryHandler) {
        this.textHandler = textHandler;
        this.binaryHandler = binaryHandler;
        uri = URI.create(url);
        ssl = "wss".equalsIgnoreCase(uri.getScheme());
        SslContext sslContext;
        if (ssl) {
            try {
                sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            } catch (SSLException e) {
                throw new RuntimeException(e);
            }
        } else {
            sslContext = null;
        }
        port = uri.getPort() == -1 ? (ssl ? 443 : 80) : uri.getPort();
        group = new NioEventLoopGroup();
        try {
            WebSocketClientInitializer initializer = new WebSocketClientInitializer(uri, port, sslContext, this);
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(initializer);
            channel = b.connect(uri.getHost(), port).sync().channel();
            initializer.getHandler().handshakeFuture().sync();
        } catch (Exception e) {
            logger.error("websocket server init failed: {}", e.getMessage());
            throw new RuntimeException(e);            
        }
    }

    public void setBinaryHandler(Consumer<byte[]> binaryHandler) {
        this.binaryHandler = binaryHandler;
    }

    public void setTextHandler(Consumer<String> textHandler) {
        this.textHandler = textHandler;
    }        

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

}
