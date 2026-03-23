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
package com.intuit.karate.core;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V1 compatibility shim for {@link io.karatelabs.core.MockServer}.
 * <p>
 * This class provides backward compatibility for code written against Karate v1.
 * Migrate to {@link io.karatelabs.core.MockServer} for new code.
 *
 * @deprecated Use {@link io.karatelabs.core.MockServer} instead.
 *             This class will be removed in a future release.
 */
@Deprecated(since = "2.0", forRemoval = true)
public class MockServer {

    private final io.karatelabs.core.MockServer delegate;

    private MockServer(io.karatelabs.core.MockServer delegate) {
        this.delegate = delegate;
    }

    /**
     * Create a builder for a mock server from a feature file path.
     *
     * @deprecated Use {@link io.karatelabs.core.MockServer#feature(String)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static Builder feature(String path) {
        return new Builder().feature(path);
    }

    /**
     * Create a builder for a mock server from a feature string.
     *
     * @deprecated Use {@link io.karatelabs.core.MockServer#featureString(String)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static Builder featureString(String featureContent) {
        return new Builder().featureString(featureContent);
    }

    /**
     * Get the port the server is listening on.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public int getPort() {
        return delegate.getPort();
    }

    /**
     * Check if the server is using SSL/TLS.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public boolean isSsl() {
        return delegate.isSsl();
    }

    /**
     * Get the base URL for the server.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public String getUrl() {
        return delegate.getUrl();
    }

    /**
     * Stop the mock server and wait for shutdown to complete (blocking).
     * <p>
     * V1 name was stop(); v2 uses stopAndWait().
     *
     * @deprecated Use {@link io.karatelabs.core.MockServer#stopAndWait()} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public void stop() {
        delegate.stopAndWait();
    }

    /**
     * Stop the mock server and wait for shutdown to complete (blocking).
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public void stopAndWait() {
        delegate.stopAndWait();
    }

    /**
     * Stop the mock server asynchronously (non-blocking).
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public void stopAsync() {
        delegate.stopAsync();
    }

    /**
     * Wait for the server to be stopped (blocking).
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public void waitSync() {
        delegate.waitSync();
    }

    /**
     * Get a variable from the mock handler's global context.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Object getVariable(String name) {
        return delegate.getVariable(name);
    }

    /**
     * Get the underlying v2 MockServer for gradual migration.
     */
    public io.karatelabs.core.MockServer toV2MockServer() {
        return delegate;
    }

    /**
     * V1 compatibility Builder for MockServer.
     *
     * @deprecated Use {@link io.karatelabs.core.MockServer.Builder} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static class Builder {

        private io.karatelabs.core.MockServer.Builder delegate;
        private Map<String, Object> args;

        Builder() {
            // delegate will be set by feature() or featureString()
        }

        private Builder(io.karatelabs.core.MockServer.Builder delegate) {
            this.delegate = delegate;
        }

        /**
         * Add a feature file by path.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder feature(String path) {
            this.delegate = io.karatelabs.core.MockServer.feature(path);
            return this;
        }

        /**
         * Add a feature from inline string content.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder featureString(String featureContent) {
            this.delegate = io.karatelabs.core.MockServer.featureString(featureContent);
            return this;
        }

        /**
         * Set the port to listen on. 0 = dynamic port.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder port(int port) {
            delegate.port(port);
            return this;
        }

        /**
         * Set initial arguments (variables) for the mock using a map.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder arg(Map<String, Object> args) {
            delegate.arg(args);
            this.args = args;
            return this;
        }

        /**
         * Set a single argument (variable) for the mock.
         * <p>
         * V1 had this single key-value variant; v2 only has Map variant.
         *
         * @deprecated Use {@code arg(Map.of(key, value))} instead.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder arg(String key, Object value) {
            if (args == null) {
                args = new LinkedHashMap<>();
            }
            args.put(key, value);
            delegate.arg(args);
            return this;
        }

        /**
         * Set a path prefix to strip from incoming requests.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder pathPrefix(String prefix) {
            delegate.pathPrefix(prefix);
            return this;
        }

        /**
         * Enable SSL/TLS.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder ssl(boolean ssl) {
            delegate.ssl(ssl);
            return this;
        }

        /**
         * Set the SSL certificate file path (PEM format).
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder certPath(String certPath) {
            delegate.certPath(certPath);
            return this;
        }

        /**
         * Set the SSL private key file path (PEM format).
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder keyPath(String keyPath) {
            delegate.keyPath(keyPath);
            return this;
        }

        /**
         * Set the SSL certificate file.
         * <p>
         * V1 used File objects; v2 uses String paths.
         *
         * @deprecated Use {@link #certPath(String)} instead.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder certFile(File certFile) {
            delegate.certPath(certFile.getAbsolutePath());
            return this;
        }

        /**
         * Set the SSL private key file.
         * <p>
         * V1 used File objects; v2 uses String paths.
         *
         * @deprecated Use {@link #keyPath(String)} instead.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder keyFile(File keyFile) {
            delegate.keyPath(keyFile.getAbsolutePath());
            return this;
        }

        /**
         * Configure HTTP server on the specified port.
         * <p>
         * V1 combined port setting with ssl=false; v2 separates these.
         *
         * @deprecated Use {@code port(port)} instead. SSL is disabled by default.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder http(int port) {
            delegate.port(port);
            delegate.ssl(false);
            return this;
        }

        /**
         * Configure HTTPS server on the specified port.
         * <p>
         * V1 combined port setting with ssl=true; v2 separates these.
         *
         * @deprecated Use {@code ssl(true).port(port)} instead.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder https(int port) {
            delegate.port(port);
            delegate.ssl(true);
            return this;
        }

        /**
         * Enable watch mode for hot-reloading feature files.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder watch(boolean watch) {
            delegate.watch(watch);
            return this;
        }

        /**
         * Start the mock server.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public MockServer start() {
            return new MockServer(delegate.start());
        }

        /**
         * Build and start the mock server.
         * <p>
         * V1 name was build(); v2 uses start().
         *
         * @deprecated Use {@link #start()} instead.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public MockServer build() {
            return start();
        }

        /**
         * Get the underlying v2 Builder for gradual migration.
         */
        public io.karatelabs.core.MockServer.Builder toV2Builder() {
            return delegate;
        }
    }

}
