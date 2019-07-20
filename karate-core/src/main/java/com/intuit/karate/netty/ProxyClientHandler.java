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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
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
public class ProxyClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = LoggerFactory.getLogger(ProxyClientHandler.class);

    private ProxyRemoteHandler remoteHandler;
    protected Channel clientChannel;
    private final Map<String, ProxyRemoteHandler> REMOTE_HANDLERS = new ConcurrentHashMap();
    private final Object LOCK = new Object();

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        clientChannel = ctx.channel();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject httpObject) throws Exception {
        if (!(httpObject instanceof HttpRequest)) {
            if (remoteHandler != null) {
                if (logger.isTraceEnabled()) {
                    logger.trace(">> pass: {}", httpObject);
                }
                ReferenceCountUtil.retain(httpObject);
                remoteHandler.remoteChannel.writeAndFlush(httpObject);
                return;
            }
            // last chunk for ssl CONNECT, can be safely ignored
            if (logger.isTraceEnabled()) {
                logger.trace(">> skip: {}", httpObject);
            }
            return;
        }
        HttpRequest request = (HttpRequest) httpObject;
        ProxyContext pc = new ProxyContext(request);
        remoteHandler = NettyUtils.isConnect(request) ? null : REMOTE_HANDLERS.get(pc.hostColonPort);
        if (remoteHandler != null) {
            NettyUtils.fixHeadersForProxy(request);
            if (logger.isTraceEnabled()) {
                logger.trace(">> write: {}", request);
            }
            remoteHandler.remoteChannel.writeAndFlush(request);
            return;
        }
        if (logger.isTraceEnabled()) {
            logger.trace(">> init: {}", request);
        }
        Bootstrap b = new Bootstrap();
        b.group(new NioEventLoopGroup(4));
        b.channel(NioSocketChannel.class);
        b.handler(new ChannelInitializer() {
            @Override
            protected void initChannel(Channel remoteChannel) throws Exception {
                ChannelPipeline p = remoteChannel.pipeline();
                if (pc.ssl) {
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
                            synchronized (LOCK) {
                                LOCK.notify();
                            }
                        });
                        synchronized (LOCK) {
                            LOCK.wait();
                        }
                    });
                }
                p.addLast(new HttpRequestEncoder());
                p.addLast(new HttpResponseDecoder());
                remoteHandler = new ProxyRemoteHandler(clientChannel);
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
                if (pc.ssl) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("** connected (ssl): {}", cf.channel());
                    }
                } else { // not ssl
                    Channel remoteChannel = cf.channel();
                    if (logger.isTraceEnabled()) {
                        logger.trace("** connected (not-ssl): {}", remoteChannel);
                    }
                    NettyUtils.fixHeadersForProxy(request);
                    remoteChannel.writeAndFlush(request).addListener(l -> {
                        synchronized (LOCK) {
                            LOCK.notify();
                        }
                    });
                }
            } else {
                NettyUtils.flushAndClose(clientChannel);
            }
        });
        if (!pc.ssl) {
            synchronized (LOCK) {
                LOCK.wait();
            }
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
