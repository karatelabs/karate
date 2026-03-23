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

import io.netty.handler.ssl.SslContext;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Configuration for WebSocket client connections using builder pattern.
 */
public class WsClientOptions {

    private final URI uri;
    private final Map<String, String> headers;
    private final boolean compression;
    private final int maxPayloadSize;
    private final Duration connectTimeout;
    private final Duration pingInterval;
    private final boolean trustAllCerts;
    private final SslContext sslContext;
    private final ExecutorService callbackExecutor;

    private WsClientOptions(Builder builder) {
        this.uri = builder.uri;
        this.headers = builder.headers != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers))
                : Collections.emptyMap();
        this.compression = builder.compression;
        this.maxPayloadSize = builder.maxPayloadSize;
        this.connectTimeout = builder.connectTimeout;
        this.pingInterval = builder.pingInterval;
        this.trustAllCerts = builder.trustAllCerts;
        this.sslContext = builder.sslContext;
        this.callbackExecutor = builder.callbackExecutor;
    }

    public static Builder builder(String uri) {
        return new Builder(URI.create(uri));
    }

    public static Builder builder(URI uri) {
        return new Builder(uri);
    }

    // Accessors

    public URI getUri() {
        return uri;
    }

    public String getHost() {
        return uri.getHost();
    }

    public int getPort() {
        int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        return isSsl() ? 443 : 80;
    }

    public boolean isSsl() {
        String scheme = uri.getScheme();
        return "wss".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public boolean isCompression() {
        return compression;
    }

    public int getMaxPayloadSize() {
        return maxPayloadSize;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getPingInterval() {
        return pingInterval;
    }

    public boolean isTrustAllCerts() {
        return trustAllCerts;
    }

    public SslContext getSslContext() {
        return sslContext;
    }

    public ExecutorService getCallbackExecutor() {
        return callbackExecutor;
    }

    public static class Builder {

        private final URI uri;
        private Map<String, String> headers;
        private boolean compression = false;
        private int maxPayloadSize = HttpUtils.MEGABYTE;
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration pingInterval = Duration.ofSeconds(30);
        private boolean trustAllCerts = true;
        private SslContext sslContext;
        private ExecutorService callbackExecutor;

        private Builder(URI uri) {
            if (uri == null) {
                throw new IllegalArgumentException("URI cannot be null");
            }
            this.uri = uri;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder header(String name, String value) {
            if (this.headers == null) {
                this.headers = new LinkedHashMap<>();
            }
            this.headers.put(name, value);
            return this;
        }

        public Builder compression(boolean enabled) {
            this.compression = enabled;
            return this;
        }

        public Builder maxPayloadSize(int size) {
            this.maxPayloadSize = size;
            return this;
        }

        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        public Builder pingInterval(Duration interval) {
            this.pingInterval = interval;
            return this;
        }

        public Builder disablePing() {
            this.pingInterval = null;
            return this;
        }

        public Builder sslContext(SslContext context) {
            this.sslContext = context;
            return this;
        }

        public Builder trustAllCerts(boolean trust) {
            this.trustAllCerts = trust;
            return this;
        }

        public Builder callbackExecutor(ExecutorService executor) {
            this.callbackExecutor = executor;
            return this;
        }

        public WsClientOptions build() {
            return new WsClientOptions(this);
        }

    }

}
