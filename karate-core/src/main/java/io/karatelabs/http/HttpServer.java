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
import io.karatelabs.common.ThreadUtils;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    final SseHandler sseHandler;
    final WsHandler wsHandler;

    public static HttpServer start(int port, Function<HttpRequest, HttpResponse> handler) {
        return new HttpServer(null, port, null, handler, null, null);
    }

    public static HttpServer start(int port, Function<HttpRequest, HttpResponse> handler, SseHandler sseHandler) {
        return new HttpServer(null, port, null, handler, sseHandler, null);
    }

    public static HttpServer start(int port, SslContext sslContext, Function<HttpRequest, HttpResponse> handler) {
        return new HttpServer(null, port, sslContext, handler, null, null);
    }

    public static HttpServer start(int port, SslContext sslContext, Function<HttpRequest, HttpResponse> handler, SseHandler sseHandler) {
        return new HttpServer(null, port, sslContext, handler, sseHandler, null);
    }

    public static HttpServer start(int port, Function<HttpRequest, HttpResponse> handler, SseHandler sseHandler, WsHandler wsHandler) {
        return new HttpServer(null, port, null, handler, sseHandler, wsHandler);
    }

    public static HttpServer start(int port, SslContext sslContext, Function<HttpRequest, HttpResponse> handler, SseHandler sseHandler, WsHandler wsHandler) {
        return new HttpServer(null, port, sslContext, handler, sseHandler, wsHandler);
    }

    /**
     * Start bound to a specific interface — {@code host} = {@code "localhost"}/{@code "127.0.0.1"} listens on
     * loopback only (not reachable off-box), {@code "0.0.0.0"}/{@code null}/blank listens on every interface
     * (the historical default). Lets a server be hardened to localhost-only; the caller (e.g. karate-max
     * {@code serve}) decides the default per deployment (host-dev = localhost, in-container = 0.0.0.0 so
     * Docker {@code -p} forwarding can reach it).
     */
    public static HttpServer start(String host, int port, Function<HttpRequest, HttpResponse> handler, SseHandler sseHandler, WsHandler wsHandler) {
        return new HttpServer(host, port, null, handler, sseHandler, wsHandler);
    }

    public boolean isSsl() {
        return sslContext != null;
    }

    public int getPort() {
        return port;
    }

    /** The socket address the server actually bound to — a wildcard ({@code isAnyLocalAddress}) when
     *  started with a null/blank/{@code "0.0.0.0"} host, else the specific interface (e.g. loopback for
     *  {@code "localhost"}). For diagnostics + tests of the bind-host hardening. */
    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) channel.localAddress();
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

    private HttpServer(String host, int requestedPort, SslContext sslContext, Function<HttpRequest, HttpResponse> handler, SseHandler sseHandler, WsHandler wsHandler) {
        this.handler = handler;
        this.sseHandler = sseHandler;
        this.wsHandler = wsHandler;
        this.sslContext = sslContext;
        bossGroup = new MultiThreadIoEventLoopGroup(1, ThreadUtils.daemonFactory("http-boss-"), NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(ThreadUtils.daemonFactory("http-worker-"), NioIoHandler.newFactory());
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
            // host null/blank/"0.0.0.0" → wildcard (all interfaces, the historical default); a specific
            // host (e.g. "localhost"/"127.0.0.1") binds that interface only — loopback is unreachable off-box.
            InetSocketAddress bindAddress = (host == null || host.isBlank() || "0.0.0.0".equals(host))
                    ? new InetSocketAddress(requestedPort)
                    : new InetSocketAddress(host, requestedPort);
            channel = bootstrap.bind(bindAddress).sync().channel();
            InetSocketAddress isa = (InetSocketAddress) channel.localAddress();
            port = isa.getPort();
            ACTIVE_SERVERS.add(this);
            String protocol = sslContext != null ? "https" : "http";
            logger.debug("{} server started on {}:{}", protocol, isa.getHostString(), port);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
