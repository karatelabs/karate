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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Protocol-agnostic WebSocket client built on Netty.
 */
public class WsClient {

    private static final Logger logger = LoggerFactory.getLogger(WsClient.class);

    private static final Set<WsClient> ACTIVE_CLIENTS = ConcurrentHashMap.newKeySet();
    private static final ExecutorService CALLBACK_EXECUTOR =
            Executors.newCachedThreadPool(daemonThreadFactory("ws-callback-"));

    public static void closeAll() {
        if (ACTIVE_CLIENTS.isEmpty()) {
            return;
        }
        logger.info("closing {} active websocket client(s)", ACTIVE_CLIENTS.size());
        for (WsClient client : ACTIVE_CLIENTS) {
            client.closeNow();
        }
        ACTIVE_CLIENTS.clear();
    }

    public static void shutdownCallbackExecutor() {
        CALLBACK_EXECUTOR.shutdown();
    }

    // Factory methods

    public static WsClient connect(String uri) {
        return connect(WsClientOptions.builder(uri).build());
    }

    public static WsClient connect(URI uri) {
        return connect(WsClientOptions.builder(uri).build());
    }

    public static WsClient connect(WsClientOptions options) {
        WsClient client = new WsClient(options);
        client.doConnect();
        return client;
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    // Instance fields

    private final WsClientOptions options;
    private final ExecutorService callbackExecutor;
    private final List<Consumer<WsFrame>> messageListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> closeListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Throwable>> errorListeners = new CopyOnWriteArrayList<>();

    private EventLoopGroup group;
    private Channel channel;
    private volatile boolean open;
    private String negotiatedSubprotocol;
    private ScheduledFuture<?> pingTask;

    private WsClient(WsClientOptions options) {
        this.options = options;
        this.callbackExecutor = options.getCallbackExecutor() != null
                ? options.getCallbackExecutor()
                : CALLBACK_EXECUTOR;
    }

    private void doConnect() {
        URI uri = options.getUri();
        String scheme = uri.getScheme();
        String host = options.getHost();
        int port = options.getPort();

        SslContext sslContext = null;
        if (options.isSsl()) {
            sslContext = options.getSslContext();
            if (sslContext == null && options.isTrustAllCerts()) {
                try {
                    sslContext = SslContextBuilder.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build();
                } catch (SSLException e) {
                    throw new WsException(WsException.Type.CONNECT_FAILED, "SSL context creation failed", e);
                }
            }
        }

        HttpHeaders headers = new DefaultHttpHeaders();
        for (Map.Entry<String, String> entry : options.getHeaders().entrySet()) {
            headers.add(entry.getKey(), entry.getValue());
        }

        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri,
                WebSocketVersion.V13,
                null,
                options.isCompression(),
                headers,
                options.getMaxPayloadSize()
        );

        WsClientHandler handler = new WsClientHandler(this, handshaker);
        SslContext finalSslContext = sslContext;

        group = new MultiThreadIoEventLoopGroup(1, daemonThreadFactory("ws-client-"), NioIoHandler.newFactory());

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline p = ch.pipeline();
                            if (finalSslContext != null) {
                                p.addLast(finalSslContext.newHandler(ch.alloc(), host, port));
                            }
                            p.addLast(new HttpClientCodec());
                            p.addLast(new HttpObjectAggregator(options.getMaxPayloadSize()));
                            if (options.isCompression()) {
                                p.addLast(new WebSocketClientCompressionHandler(0));
                            }
                            p.addLast(handler);
                        }
                    });

            channel = bootstrap.connect(host, port)
                    .sync()
                    .channel();

            handler.getHandshakeFuture().await(options.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS);

            if (!handler.getHandshakeFuture().isSuccess()) {
                Throwable cause = handler.getHandshakeFuture().cause();
                throw new WsException(WsException.Type.CONNECT_FAILED,
                        "websocket handshake failed: " + (cause != null ? cause.getMessage() : "timeout"), cause);
            }

            open = true;
            ACTIVE_CLIENTS.add(this);
            logger.debug("websocket connected: {}", uri);

            // Start ping task if configured
            if (options.getPingInterval() != null) {
                long intervalMillis = options.getPingInterval().toMillis();
                pingTask = channel.eventLoop().scheduleAtFixedRate(
                        this::ping,
                        intervalMillis,
                        intervalMillis,
                        TimeUnit.MILLISECONDS
                );
            }

        } catch (WsException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WsException(WsException.Type.CONNECT_FAILED, "connection interrupted", e);
        } catch (Exception e) {
            throw new WsException(WsException.Type.CONNECT_FAILED, "connection failed: " + e.getMessage(), e);
        }
    }

    // Connection state

    public boolean isOpen() {
        return open && channel != null && channel.isActive();
    }

    public URI getUri() {
        return options.getUri();
    }

    public String getNegotiatedSubprotocol() {
        return negotiatedSubprotocol;
    }

    // Sending frames

    public void send(String text) {
        send(WsFrame.text(text));
    }

    public void send(byte[] binary) {
        send(WsFrame.binary(binary));
    }

    public void send(WsFrame frame) {
        if (!isOpen()) {
            throw new WsException(WsException.Type.CONNECTION_CLOSED, "websocket is not open");
        }
        WebSocketFrame wsFrame = toNettyFrame(frame);
        channel.writeAndFlush(wsFrame).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                logger.error("send failed: {}", future.cause().getMessage());
            }
        });
    }

    public CompletableFuture<Void> sendAsync(WsFrame frame) {
        if (!isOpen()) {
            return CompletableFuture.failedFuture(
                    new WsException(WsException.Type.CONNECTION_CLOSED, "websocket is not open"));
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        WebSocketFrame wsFrame = toNettyFrame(frame);
        channel.writeAndFlush(wsFrame).addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                future.complete(null);
            } else {
                future.completeExceptionally(
                        new WsException(WsException.Type.SEND_FAILED, "send failed", channelFuture.cause()));
            }
        });
        return future;
    }

    private WebSocketFrame toNettyFrame(WsFrame frame) {
        return switch (frame.getType()) {
            case TEXT -> new TextWebSocketFrame(frame.getText());
            case BINARY -> new BinaryWebSocketFrame(Unpooled.wrappedBuffer(frame.getBytes()));
            case PING -> new PingWebSocketFrame();
            case PONG -> new PongWebSocketFrame();
            case CLOSE -> new CloseWebSocketFrame(frame.getCloseCode(), frame.getCloseReason());
        };
    }

    // Listener registration

    public void onMessage(Consumer<WsFrame> listener) {
        messageListeners.add(listener);
    }

    public void onClose(Runnable listener) {
        closeListeners.add(listener);
    }

    public void onError(Consumer<Throwable> listener) {
        errorListeners.add(listener);
    }

    // Lifecycle

    public void close() {
        if (channel != null && channel.isOpen()) {
            channel.writeAndFlush(new CloseWebSocketFrame())
                    .addListener(ChannelFutureListener.CLOSE);
        }
        cleanup();
    }

    public void closeNow() {
        if (channel != null) {
            channel.close();
        }
        cleanup();
    }

    public void waitSync() {
        if (channel != null) {
            try {
                channel.closeFuture().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void cleanup() {
        ACTIVE_CLIENTS.remove(this);
        open = false;
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    // Ping

    public void ping() {
        if (isOpen()) {
            channel.writeAndFlush(new PingWebSocketFrame());
        }
    }

    // Internal callbacks from handler

    void handleFrame(WsFrame frame) {
        for (Consumer<WsFrame> listener : messageListeners) {
            callbackExecutor.execute(() -> {
                try {
                    listener.accept(frame);
                } catch (Exception e) {
                    logger.error("message listener error: {}", e.getMessage());
                }
            });
        }
    }

    void handleDisconnect() {
        open = false;
        for (Runnable listener : closeListeners) {
            callbackExecutor.execute(() -> {
                try {
                    listener.run();
                } catch (Exception e) {
                    logger.error("close listener error: {}", e.getMessage());
                }
            });
        }
        cleanup();
    }

    void handleError(Throwable cause) {
        for (Consumer<Throwable> listener : errorListeners) {
            callbackExecutor.execute(() -> {
                try {
                    listener.accept(cause);
                } catch (Exception e) {
                    logger.error("error listener error: {}", e.getMessage());
                }
            });
        }
    }

}
