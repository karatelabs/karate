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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class HttpServer {

    static final Logger logger = LoggerFactory.getLogger(HttpServer.class);
    private static final Set<HttpServer> ACTIVE_SERVERS = ConcurrentHashMap.newKeySet();

    public static void shutdownAll() {
        if (ACTIVE_SERVERS.isEmpty()) {
            return;
        }
        logger.info("shutting down {} active server(s)", ACTIVE_SERVERS.size());
        for (HttpServer server : ACTIVE_SERVERS) {
            server.stopAsync();
        }
        ACTIVE_SERVERS.clear();
    }

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final Channel channel;
    private final int port;
    private final SslContext sslContext;

    final Function<HttpRequest, HttpResponse> handler;

    public static HttpServer start(int port, Function<HttpRequest, HttpResponse> handler) {
        return new HttpServer(port, null, handler);
    }

    public static HttpServer start(int port, SslContext sslContext, Function<HttpRequest, HttpResponse> handler) {
        return new HttpServer(port, sslContext, handler);
    }

    public boolean isSsl() {
        return sslContext != null;
    }

    public int getPort() {
        return port;
    }

    public void waitSync() {
        try {
            channel.closeFuture().sync();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void stopAndWait() {
        stopAsync();
        try {
            bossGroup.terminationFuture().sync();
            workerGroup.terminationFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while shutting down HTTP server", e);
        }
        logger.debug("stop: shutdown complete");
    }

    public void stopAsync() {
        ACTIVE_SERVERS.remove(this);
        logger.debug("stop: shutting down");
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    private HttpServer(int requestedPort, SslContext sslContext, Function<HttpRequest, HttpResponse> handler) {
        this.handler = handler;
        this.sslContext = sslContext;
        bossGroup = new MultiThreadIoEventLoopGroup(1, daemonThreadFactory("http-boss-"), NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(daemonThreadFactory("http-worker-"), NioIoHandler.newFactory());
        CorsConfig corsConfig = CorsConfigBuilder
                .forAnyOrigin().allowNullOrigin()
                .allowedRequestHeaders(HttpUtils.Header.keys())
                .allowedRequestMethods(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                        HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.HEAD)
                .build();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel c) {
                            ChannelPipeline p = c.pipeline();
                            if (sslContext != null) {
                                p.addLast(sslContext.newHandler(c.alloc()));
                            }
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(HttpUtils.MEGABYTE));
                            p.addLast(new CorsHandler(corsConfig));
                            p.addLast(new HttpServerHandler(HttpServer.this));
                        }
                    });
            channel = bootstrap.bind(requestedPort).sync().channel();
            InetSocketAddress isa = (InetSocketAddress) channel.localAddress();
            port = isa.getPort();
            ACTIVE_SERVERS.add(this);
            String protocol = sslContext != null ? "https" : "http";
            logger.debug("{} server started on port: {}", protocol, port);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
