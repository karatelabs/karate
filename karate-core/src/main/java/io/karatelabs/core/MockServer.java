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
package io.karatelabs.core;

import io.karatelabs.common.Resource;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpResponse;
import io.karatelabs.http.HttpServer;
import io.karatelabs.js.SimpleObject;
import io.karatelabs.output.LogContext;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Public API for creating mock servers from feature files.
 * <p>
 * Usage:
 * <pre>
 * MockServer server = MockServer.feature("api.feature")
 *     .port(8080)
 *     .arg(Map.of("key", "value"))
 *     .start();
 *
 * // ... use server ...
 *
 * server.stopAsync();     // non-blocking
 * server.stopAndWait();   // blocking
 * </pre>
 */
public class MockServer implements SimpleObject {

    private static final Logger logger = LogContext.MOCK_LOGGER;

    private final HttpServer httpServer;
    private final MockHandler handler;
    private final int port;

    private MockServer(HttpServer httpServer, MockHandler handler, int port) {
        this.httpServer = httpServer;
        this.handler = handler;
        this.port = port;
    }

    /**
     * Create a builder for a mock server from a feature file path.
     */
    public static Builder feature(String path) {
        return new Builder().feature(path);
    }

    /**
     * Create a builder for a mock server from a parsed Feature.
     */
    public static Builder feature(Feature feature) {
        return new Builder().feature(feature);
    }

    /**
     * Create a builder for a mock server from a Resource.
     */
    public static Builder feature(io.karatelabs.common.Resource resource) {
        return new Builder().feature(Feature.read(resource));
    }

    /**
     * Create a builder for a mock server from a feature string.
     * Useful for inline feature definitions in tests.
     */
    public static Builder featureString(String featureContent) {
        return new Builder().featureString(featureContent);
    }

    /**
     * Get the port the server is listening on.
     */
    public int getPort() {
        return port;
    }

    /**
     * Check if the server is using SSL/TLS.
     */
    public boolean isSsl() {
        return httpServer.isSsl();
    }

    /**
     * Get the base URL for the server (e.g., http://localhost:8080 or https://localhost:8443).
     */
    public String getUrl() {
        String protocol = isSsl() ? "https" : "http";
        return protocol + "://localhost:" + port;
    }

    /**
     * Stop the mock server and wait for shutdown to complete (blocking).
     * @see #stopAsync()
     */
    public void stopAndWait() {
        logger.info("stopping mock server on port {}", port);
        httpServer.stopAndWait();
    }

    /**
     * Stop the mock server asynchronously (non-blocking).
     * @see #stopAndWait()
     */
    public void stopAsync() {
        logger.info("stopping mock server async on port {}", port);
        httpServer.stopAsync();
    }

    /**
     * Wait for the server to be stopped (blocking).
     */
    public void waitSync() {
        httpServer.waitSync();
    }

    /**
     * Get a variable from the mock handler's global context.
     */
    public Object getVariable(String name) {
        return handler.getVariable(name);
    }

    /**
     * Get the underlying MockHandler.
     */
    public MockHandler getHandler() {
        return handler;
    }

    @Override
    public Object jsGet(String key) {
        return switch (key) {
            case "port" -> port;
            case "url" -> getUrl();
            case "ssl" -> isSsl();
            case "stop" -> (Runnable) this::stopAsync;
            default -> null;
        };
    }

    /**
     * Builder for creating MockServer instances.
     */
    public static class Builder {

        private final List<Feature> features = new ArrayList<>();
        private int port = 0; // 0 = dynamic port
        private Map<String, Object> args;
        private String pathPrefix;
        private boolean ssl;
        private String certPath;
        private String keyPath;
        private boolean watch;

        private Builder() {
        }

        /**
         * Add a feature file by path.
         */
        public Builder feature(String path) {
            features.add(Feature.read(path));
            return this;
        }

        /**
         * Add a parsed Feature.
         */
        public Builder feature(Feature feature) {
            features.add(feature);
            return this;
        }

        /**
         * Add a feature from inline string content.
         */
        public Builder featureString(String featureContent) {
            features.add(Feature.read(io.karatelabs.common.Resource.text(featureContent)));
            return this;
        }

        /**
         * Set the port to listen on. 0 = dynamic port.
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Set initial arguments (variables) for the mock.
         */
        public Builder arg(Map<String, Object> args) {
            this.args = args;
            return this;
        }

        /**
         * Set a path prefix to strip from incoming requests.
         */
        public Builder pathPrefix(String prefix) {
            this.pathPrefix = prefix;
            return this;
        }

        /**
         * Enable SSL/TLS.
         */
        public Builder ssl(boolean ssl) {
            this.ssl = ssl;
            return this;
        }

        /**
         * Set the SSL certificate file path (PEM format).
         */
        public Builder certPath(String certPath) {
            this.certPath = certPath;
            return this;
        }

        /**
         * Set the SSL private key file path (PEM format).
         */
        public Builder keyPath(String keyPath) {
            this.keyPath = keyPath;
            return this;
        }

        /**
         * Enable watch mode for hot-reloading feature files when they change.
         * The server will check file modification times before each request
         * and reload features if any have changed.
         * <p>
         * Note: Watch mode only works for file-based features, not classpath resources in JARs.
         */
        public Builder watch(boolean watch) {
            this.watch = watch;
            return this;
        }

        /**
         * Start the mock server.
         */
        public MockServer start() {
            if (features.isEmpty()) {
                throw new RuntimeException("at least one feature file is required");
            }

            // Create handler - use ReloadingHandler wrapper if watch mode is enabled
            Function<HttpRequest, HttpResponse> requestHandler;
            MockHandler handler;

            if (watch) {
                ReloadingHandler reloadingHandler = new ReloadingHandler(features, args, pathPrefix);
                handler = reloadingHandler.getHandler();
                requestHandler = reloadingHandler;
                logger.info("watch mode enabled - features will be reloaded when modified");
            } else {
                handler = new MockHandler(features, args, pathPrefix);
                requestHandler = handler;
            }

            SslContext sslContext = null;
            if (ssl) {
                if (certPath != null && keyPath != null) {
                    sslContext = SslUtils.createNettySslContext(certPath, keyPath);
                } else {
                    sslContext = SslUtils.generateNettySslContext();
                }
            }

            HttpServer httpServer = HttpServer.start(port, sslContext, requestHandler);
            int actualPort = httpServer.getPort();

            String protocol = ssl ? "https" : "http";
            logger.info("mock server started on {}://localhost:{}", protocol, actualPort);
            return new MockServer(httpServer, handler, actualPort);
        }

    }

    /**
     * Handler that wraps MockHandler to support hot-reloading of feature files.
     * Checks file modification times before each request and reloads if any have changed.
     */
    private static class ReloadingHandler implements Function<HttpRequest, HttpResponse> {

        private final Map<String, Object> args;
        private final String pathPrefix;
        private final Map<Resource, Long> watchedFiles = new LinkedHashMap<>();
        private MockHandler handler;

        ReloadingHandler(List<Feature> features, Map<String, Object> args, String pathPrefix) {
            this.args = args;
            this.pathPrefix = pathPrefix;

            // Track file modification times for each feature
            for (Feature feature : features) {
                Resource resource = feature.getResource();
                if (resource.isLocalFile()) {
                    watchedFiles.put(resource, resource.getLastModified());
                    logger.debug("watching file: {} (modified: {})", resource.getRelativePath(), resource.getLastModified());
                } else {
                    logger.warn("watch mode: cannot watch non-file resource: {} (classpath resources in JARs are not watchable)",
                            resource.getRelativePath());
                }
            }

            if (watchedFiles.isEmpty()) {
                logger.warn("watch mode enabled but no watchable files found - features may be from classpath JARs");
            }

            // Initialize handler
            handler = new MockHandler(features, args, pathPrefix);
        }

        MockHandler getHandler() {
            return handler;
        }

        @Override
        public HttpResponse apply(HttpRequest request) {
            // Check if any watched file has been modified
            boolean needsReload = false;
            for (Map.Entry<Resource, Long> entry : watchedFiles.entrySet()) {
                Resource resource = entry.getKey();
                long lastKnown = entry.getValue();
                long current = resource.getLastModified();
                if (current > lastKnown) {
                    logger.info("file modified, reloading: {} (was: {}, now: {})",
                            resource.getRelativePath(), lastKnown, current);
                    needsReload = true;
                    break;
                }
            }

            if (needsReload) {
                // Reload all features - must create NEW Resource objects to avoid cached content
                List<Feature> reloadedFeatures = new ArrayList<>();
                Map<Resource, Long> newWatchedFiles = new LinkedHashMap<>();

                for (Resource oldResource : watchedFiles.keySet()) {
                    // Create fresh Resource from the file path to get new content
                    Resource freshResource = Resource.from(oldResource.getPath());
                    Feature feature = Feature.read(freshResource);
                    reloadedFeatures.add(feature);
                    // Track the new resource with current modification time
                    newWatchedFiles.put(freshResource, freshResource.getLastModified());
                }

                // Replace watched files map with new resources
                watchedFiles.clear();
                watchedFiles.putAll(newWatchedFiles);

                // Create new handler with reloaded features
                handler = new MockHandler(reloadedFeatures, args, pathPrefix);
                logger.info("reloaded {} feature(s)", reloadedFeatures.size());
            }

            return handler.apply(request);
        }

    }

}
