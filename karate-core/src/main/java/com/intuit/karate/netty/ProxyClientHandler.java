/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.ssl.SslHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ProxyClientHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(ProxyClientHandler.class);

    protected final RequestFilter requestFilter;
    protected final ResponseFilter responseFilter;
    private final Map<String, ProxyRemoteHandler> REMOTE_HANDLERS = new ConcurrentHashMap();
    private final Object LOCK = new Object();
    
    private ProxyRemoteHandler remoteHandler;
    protected Channel clientChannel;

    public ProxyClientHandler(RequestFilter requestFilter, ResponseFilter responseFilter) {
        this.requestFilter = requestFilter;
        this.responseFilter = responseFilter;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        clientChannel = ctx.channel();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        boolean isConnect = HttpMethod.CONNECT.equals(request.method());
        ProxyContext pc = new ProxyContext(request, isConnect);
        // if ssl CONNECT, always create new remote pipeline
        if (remoteHandler == null && !isConnect) {
            remoteHandler = REMOTE_HANDLERS.get(pc.hostColonPort);
        }
        if (remoteHandler != null) {
            remoteHandler.send(request);
            return;
        }
        if (logger.isTraceEnabled()) {
            logger.trace(">> init: {} - {}", pc, request);
        }
        Bootstrap b = new Bootstrap();
        b.group(new NioEventLoopGroup(4));
        b.channel(NioSocketChannel.class);
        b.handler(new ChannelInitializer() {
            @Override
            protected void initChannel(Channel remoteChannel) throws Exception {
                ChannelPipeline p = remoteChannel.pipeline();
                if (isConnect) {
                    SSLContext sslContext = NettyUtils.getSslContext(null);
                    SSLEngine remoteSslEngine = sslContext.createSSLEngine(pc.host, pc.port);
                    remoteSslEngine.setUseClientMode(true);
                    remoteSslEngine.setNeedClientAuth(false);
                    SslHandler remoteSslHandler = new SslHandler(remoteSslEngine);
                    p.addLast(remoteSslHandler);
                    remoteSslHandler.handshakeFuture().addListener(rhf -> {
                        if (logger.isTraceEnabled()) {
                            logger.trace("** ssl: server handshake done: {}", remoteChannel);
                        }
                        SSLEngine clientSslEngine = sslContext.createSSLEngine();
                        clientSslEngine.setUseClientMode(false);
                        clientSslEngine.setNeedClientAuth(false);
                        SslHandler clientSslHandler = new SslHandler(clientSslEngine);
                        HttpResponse response = NettyUtils.connectionEstablished();
                        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                        clientChannel.eventLoop().execute(() -> {
                            clientChannel.writeAndFlush(response);
                            clientChannel.pipeline().addFirst(clientSslHandler);
                        });
                        clientSslHandler.handshakeFuture().addListener(chf -> {
                            if (logger.isTraceEnabled()) {
                                logger.trace("** ssl: client handshake done: {}", clientChannel);
                            }
                            unlockAndProceed();
                        });
                        lockAndWait();
                    });
                }
                p.addLast(new HttpClientCodec());
                p.addLast(new HttpContentDecompressor());
                p.addLast(new HttpObjectAggregator(1048576));                 
                remoteHandler = new ProxyRemoteHandler(pc, ProxyClientHandler.this, isConnect ? null : request);
                REMOTE_HANDLERS.put(pc.hostColonPort, remoteHandler);
                p.addLast(remoteHandler);
                if (logger.isTraceEnabled()) {
                    logger.trace("updated remote handlers: {}", REMOTE_HANDLERS);
                }
            }
        });
        ChannelFuture cf = b.connect(pc.host, pc.port);
        cf.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                if (logger.isTraceEnabled()) {
                    logger.trace("** ready: {} - {}", pc, cf.channel());
                }
            } else {
                NettyUtils.flushAndClose(clientChannel);
            }
        });
        if (!isConnect) {
            lockAndWait();
        }
    }

    private void lockAndWait() throws Exception {
        synchronized (LOCK) {
            LOCK.wait();
        }
    }

    protected void unlockAndProceed() {
        synchronized (LOCK) {
            LOCK.notify();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause.getMessage() == null) {
            cause.printStackTrace();
        } else {
            logger.error("closing proxy inbound connection: {}", cause.getMessage());
        }
        ctx.close();
        NettyUtils.flushAndClose(remoteHandler.remoteChannel);
    }

}
