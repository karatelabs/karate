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
import io.netty.handler.codec.http.HttpDecoderConfig;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpObjectDecoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.karatelabs.common.KarateLifecycle;
import io.karatelabs.common.Stoppable;
import io.karatelabs.common.ThreadUtils;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class HttpServer implements Stoppable {

    static final Logger logger = LoggerFactory.getLogger(HttpServer.class);

    public static final int DEFAULT_MAX_INITIAL_LINE_LENGTH = HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH;
    public static final int DEFAULT_MAX_HEADER_SIZE = HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE;
    public static final int DEFAULT_MAX_CONTENT_LENGTH = HttpUtils.MEGABYTE;

    /** Servers that started and have not stopped — the registry is the single source of truth,
     *  see {@link KarateLifecycle} for the ecosystem-wide version of this. */
    private static List<HttpServer> active() {
        return KarateLifecycle.running().stream()
                .filter(HttpServer.class::isInstance)
                .map(HttpServer.class::cast)
                .toList();
    }

    public static void shutdownAll() {
        List<HttpServer> servers = active();
        if (servers.isEmpty()) {
            return;
        }
        logger.info("shutting down {} active server(s)", servers.size());
        for (HttpServer server : servers) {
            server.stopAsync();
        }
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
        return builder().port(port).handler(handler).start();
    }

    public static HttpServer start(int port, Function<HttpRequest, HttpResponse> handler, SseHandler sseHandler) {
        return builder().port(port).handler(handler).sseHandler(sseHandler).start();
    }

    public static HttpServer start(int port, SslContext sslContext, Function<HttpRequest, HttpResponse> handler) {
        return builder().port(port).sslContext(sslContext).handler(handler).start();
    }

    public static HttpServer start(int port, SslContext sslContext, Function<HttpRequest, HttpResponse> handler, SseHandler sseHandler) {
        return builder().port(port).sslContext(sslContext).handler(handler).sseHandler(sseHandler).start();
    }

    public static HttpServer start(int port, Function<HttpRequest, HttpResponse> handler, SseHandler sseHandler, WsHandler wsHandler) {
        return builder().port(port).handler(handler).sseHandler(sseHandler).wsHandler(wsHandler).start();
    }

    public static HttpServer start(int port, SslContext sslContext, Function<HttpRequest, HttpResponse> handler, SseHandler sseHandler, WsHandler wsHandler) {
        return builder().port(port).sslContext(sslContext).handler(handler).sseHandler(sseHandler).wsHandler(wsHandler).start();
    }

    /**
     * Start bound to a specific interface — {@code host} = {@code "localhost"}/{@code "127.0.0.1"} listens on
     * loopback only (not reachable off-box), {@code "0.0.0.0"}/{@code null}/blank listens on every interface
     * (the historical default). Lets a server be hardened to localhost-only; the caller (e.g. karate-max
     * {@code serve}) decides the default per deployment (host-dev = localhost, in-container = 0.0.0.0 so
     * Docker {@code -p} forwarding can reach it).
     */
    public static HttpServer start(String host, int port, Function<HttpRequest, HttpResponse> handler, SseHandler sseHandler, WsHandler wsHandler) {
        return builder().host(host).port(port).handler(handler).sseHandler(sseHandler).wsHandler(wsHandler).start();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Request limits apply to the HTTP/1.x pipeline and are in bytes. A request over one never reaches the
     * handler and is answered: initial line 414, headers 431, aggregated body 413.
     */
    public static class Builder {

        private String host;
        private int port;
        private SslContext sslContext;
        private Function<HttpRequest, HttpResponse> handler;
        private SseHandler sseHandler;
        private WsHandler wsHandler;
        private int maxInitialLineLength = DEFAULT_MAX_INITIAL_LINE_LENGTH;
        private int maxHeaderSize = DEFAULT_MAX_HEADER_SIZE;
        private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;

        private Builder() {
        }

        /** See {@link HttpServer#start(String, int, Function, SseHandler, WsHandler)} for bind-host semantics. */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder sslContext(SslContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder handler(Function<HttpRequest, HttpResponse> handler) {
            this.handler = handler;
            return this;
        }

        public Builder sseHandler(SseHandler sseHandler) {
            this.sseHandler = sseHandler;
            return this;
        }

        public Builder wsHandler(WsHandler wsHandler) {
            this.wsHandler = wsHandler;
            return this;
        }

        public Builder maxInitialLineLength(int bytes) {
            this.maxInitialLineLength = positive("maxInitialLineLength", bytes);
            return this;
        }

        public Builder maxHeaderSize(int bytes) {
            this.maxHeaderSize = positive("maxHeaderSize", bytes);
            return this;
        }

        public Builder maxContentLength(int bytes) {
            this.maxContentLength = positive("maxContentLength", bytes);
            return this;
        }

        public HttpServer start() {
            return new HttpServer(this);
        }

    }

    private static int positive(String name, int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException(name + " must be a positive number of bytes, was: " + bytes);
        }
        return bytes;
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

    @Override
    public String lifecycleName() {
        return "http-server:" + port;
    }

    @Override
    public String lifecycleKind() {
        return "http-server";
    }

    /** Graceful, blocking, idempotent — {@link #stopAndWait()} by another name, for
     *  {@link KarateLifecycle}. Servers that wrap one of these (mock server, OAuth2 callback
     *  server) inherit registration from here and must not register themselves as well. */
    @Override
    public void stop() {
        stopAndWait();
    }

    public void stopAndWait() {
        stopAsync();
        try {
            // the closeFuture, not group termination, is what guarantees the port is free on return
            channel.closeFuture().sync();
            bossGroup.terminationFuture().sync();
            workerGroup.terminationFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while shutting down HTTP server", e);
        }
        logger.debug("stop: shutdown complete");
    }

    public void stopAsync() {
        KarateLifecycle.unregister(this);
        logger.debug("stop: shutting down");
        // close the listen socket first — group termination closes registered channels only as a
        // side effect, and (without a quiet period) can complete before the fd is released
        channel.close();
        // no quiet period: a server being torn down accepts nothing more, so waiting for one to
        // pass only delays the (blocking) stop — the timeout still bounds in-flight work
        bossGroup.shutdownGracefully(0, 15, TimeUnit.SECONDS);
        workerGroup.shutdownGracefully(0, 15, TimeUnit.SECONDS);
    }

    private HttpServer(Builder b) {
        String host = b.host;
        int requestedPort = b.port;
        this.handler = b.handler;
        this.sseHandler = b.sseHandler;
        this.wsHandler = b.wsHandler;
        this.sslContext = b.sslContext;
        HttpDecoderConfig decoderConfig = new HttpDecoderConfig()
                .setMaxInitialLineLength(b.maxInitialLineLength)
                .setMaxHeaderSize(b.maxHeaderSize);
        int maxContentLength = b.maxContentLength;
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
                            p.addLast(new HttpServerCodec(decoderConfig));
                            p.addLast(new DecoderFailureHandler());
                            p.addLast(new HttpObjectAggregator(maxContentLength));
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
            KarateLifecycle.register(this);
            String protocol = sslContext != null ? "https" : "http";
            logger.debug("{} server started on {}:{}", protocol, isa.getHostString(), port);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
