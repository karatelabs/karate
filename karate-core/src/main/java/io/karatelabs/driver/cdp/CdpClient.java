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
package io.karatelabs.driver.cdp;

import io.karatelabs.http.HttpUtils;
import io.karatelabs.http.WsClient;
import io.karatelabs.http.WsClientOptions;
import io.karatelabs.http.WsException;
import io.karatelabs.http.WsFrame;
import net.minidev.json.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Chrome DevTools Protocol client built on WebSocket.
 * Handles request/response correlation and event subscription.
 */
public class CdpClient {

    private static final Logger logger = LoggerFactory.getLogger(CdpClient.class);

    private final WsClient ws;
    private final AtomicInteger idGenerator = new AtomicInteger();
    private final ConcurrentHashMap<Integer, PendingRequest> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Consumer<CdpEvent>>> eventHandlers = new ConcurrentHashMap<>();
    private final List<CdpEventListener> externalListeners = new CopyOnWriteArrayList<>();
    private final Duration defaultTimeout;
    private volatile String sessionId;

    static class PendingRequest {
        final CompletableFuture<CdpResponse> future;
        final String method;

        PendingRequest(CompletableFuture<CdpResponse> future, String method) {
            this.future = future;
            this.method = method;
        }
    }

    // Factory methods

    public static CdpClient connect(String webSocketUrl) {
        return connect(webSocketUrl, Duration.ofSeconds(30));
    }

    public static CdpClient connect(String webSocketUrl, Duration defaultTimeout) {
        WsClientOptions options = WsClientOptions.builder(webSocketUrl)
                .disablePing() // CDP handles its own keepalive
                .maxPayloadSize(HttpUtils.MEGABYTE * 16) // Screenshots can be large
                .build();
        WsClient ws = WsClient.connect(options);
        CdpClient client = new CdpClient(ws, defaultTimeout);
        client.waitForReady();
        return client;
    }

    private CdpClient(WsClient ws, Duration defaultTimeout) {
        this.ws = ws;
        this.defaultTimeout = defaultTimeout;
        setupMessageHandler();
    }

    /**
     * Wait for CDP to be ready by sending a simple command.
     * This verifies the WebSocket connection is fully established and CDP is responsive.
     */
    private void waitForReady() {
        try {
            // Use a shorter timeout for readiness check
            Duration readyTimeout = defaultTimeout.compareTo(Duration.ofSeconds(10)) > 0
                    ? Duration.ofSeconds(10) : defaultTimeout;
            CdpMessage message = new CdpMessage(this, nextId(), "Browser.getVersion");
            message.timeout(readyTimeout);
            CdpResponse response = send(message);
            if (response.isError()) {
                logger.warn("CDP readiness check returned error: {}", response.getErrorMessage());
            } else {
                logger.debug("CDP ready, browser: {}", response.getResult().get("product"));
            }
        } catch (Exception e) {
            throw new RuntimeException("CDP connection failed readiness check: " + e.getMessage(), e);
        }
    }

    private void setupMessageHandler() {
        ws.onMessage(frame -> {
            if (frame.isText()) {
                handleMessage(frame.getText());
            }
        });
        ws.onClose(() -> {
            // Complete all pending futures exceptionally
            for (PendingRequest pr : pending.values()) {
                pr.future.completeExceptionally(
                        new WsException(WsException.Type.CONNECTION_CLOSED, "websocket closed"));
            }
            pending.clear();
        });
        ws.onError(error -> {
            logger.error("CDP connection error: {}", error.getMessage());
        });
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(String json) {
        Map<String, Object> map;
        try {
            map = (Map<String, Object>) JSONValue.parseWithException(json);
        } catch (Exception e) {
            logger.error("failed to parse CDP message: {}", e.getMessage());
            return;
        }

        if (map.containsKey("id")) {
            // Response to a request
            int id = ((Number) map.get("id")).intValue();
            PendingRequest pr = pending.remove(id);
            if (pr != null) {
                CdpResponse response = new CdpResponse(map);
                pr.future.complete(response);
            } else {
                // Expected for fire-and-forget messages (sendWithoutWaiting)
                logger.trace("received response for fire-and-forget request id: {}", id);
            }
        } else if (map.containsKey("method")) {
            // Event
            String method = (String) map.get("method");
            CdpEvent event = new CdpEvent(map);
            dispatchEvent(method, event);
        }
    }

    private void dispatchEvent(String method, CdpEvent event) {
        List<Consumer<CdpEvent>> handlers = eventHandlers.get(method);
        if (handlers != null) {
            for (Consumer<CdpEvent> handler : handlers) {
                try {
                    handler.accept(event);
                } catch (Exception e) {
                    logger.error("event handler error for {}: {}", method, e.getMessage());
                }
            }
        }
        // Forward to external listeners (for recording, debugging, etc.)
        if (!externalListeners.isEmpty()) {
            Map<String, Object> params = event.getParams();
            for (CdpEventListener listener : externalListeners) {
                try {
                    listener.onEvent(method, params);
                } catch (Exception e) {
                    logger.error("external listener error for {}: {}", method, e.getMessage());
                }
            }
        }
    }

    // Message creation

    public int nextId() {
        return idGenerator.incrementAndGet();
    }

    /**
     * Create a new CDP message builder.
     * Automatically includes sessionId if one is set.
     */
    public CdpMessage method(String method) {
        CdpMessage message = new CdpMessage(this, nextId(), method);
        if (sessionId != null) {
            message.sessionId(sessionId);
        }
        return message;
    }

    /**
     * Create a new CDP message builder for browser-level commands.
     * Does NOT include sessionId - use for Target.* commands that operate at browser level.
     */
    public CdpMessage browserMethod(String method) {
        return new CdpMessage(this, nextId(), method);
    }

    /**
     * Get the current session ID.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Set the session ID for subsequent requests.
     * Used when switching between page targets.
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    // Send methods

    /**
     * Blocking send with response.
     */
    CdpResponse send(CdpMessage message) {
        try {
            return sendAsync(message).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                throw new RuntimeException("CDP timeout for: " + message.getMethod());
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("CDP error: " + cause.getMessage(), cause);
        }
    }

    /**
     * Async send with response tracking.
     */
    CompletableFuture<CdpResponse> sendAsync(CdpMessage message) {
        // Fail fast if connection is closed
        if (!ws.isOpen()) {
            return CompletableFuture.failedFuture(
                    new WsException(WsException.Type.CONNECTION_CLOSED, "websocket not open"));
        }

        CompletableFuture<CdpResponse> future = new CompletableFuture<>();
        int messageId = message.getId();
        pending.put(messageId, new PendingRequest(future, message.getMethod()));

        String json = message.toJson();
        logger.trace(">>> {}", json);

        try {
            ws.send(json);
        } catch (Exception e) {
            pending.remove(messageId);
            return CompletableFuture.failedFuture(e);
        }

        // Apply timeout and cleanup pending map on timeout
        Duration timeout = message.getTimeout() != null ? message.getTimeout() : defaultTimeout;
        return future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((result, ex) -> {
                    if (ex instanceof TimeoutException) {
                        pending.remove(messageId);
                        logger.debug("CDP request {} ({}) timed out", messageId, message.getMethod());
                    }
                });
    }

    /**
     * Fire and forget - no response expected.
     */
    void sendWithoutWaiting(CdpMessage message) {
        String json = message.toJson();
        logger.trace(">>> {}", json);
        ws.send(json);
    }

    /**
     * Cancel all pending Runtime.evaluate requests because a dialog opened.
     * Called when Page.javascriptDialogOpening event is received.
     */
    void cancelPendingEvaluations() {
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            PendingRequest pr = entry.getValue();
            if ("Runtime.evaluate".equals(pr.method)) {
                logger.debug("cancelling pending Runtime.evaluate (id: {}) due to dialog", entry.getKey());
                pr.future.completeExceptionally(
                        new DialogOpenedException("dialog opened while Runtime.evaluate was pending"));
                iterator.remove();
            }
        }
    }

    // Event subscription

    /**
     * Subscribe to CDP events by method name.
     */
    public void on(String eventName, Consumer<CdpEvent> handler) {
        eventHandlers.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /**
     * Unsubscribe from CDP events.
     */
    public void off(String eventName, Consumer<CdpEvent> handler) {
        List<Consumer<CdpEvent>> handlers = eventHandlers.get(eventName);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }

    /**
     * Remove all handlers for an event.
     */
    public void offAll(String eventName) {
        eventHandlers.remove(eventName);
    }

    // External event listeners (for recording, debugging, etc.)

    /**
     * Add an external event listener that receives all CDP events.
     * Used for traffic recording, debugging, etc.
     */
    public void addExternalListener(CdpEventListener listener) {
        externalListeners.add(listener);
    }

    /**
     * Remove an external event listener.
     */
    public void removeExternalListener(CdpEventListener listener) {
        externalListeners.remove(listener);
    }

    // Lifecycle

    public void close() {
        ws.close();
    }

    public boolean isOpen() {
        return ws.isOpen();
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

}
