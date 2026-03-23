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
package io.karatelabs.driver.e2e.support;

import io.karatelabs.common.FileUtils;
import io.karatelabs.common.ResourceType;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpResponse;
import io.karatelabs.http.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple HTTP server for serving test HTML pages during E2E testing.
 * Pages are loaded from classpath resources under io/karatelabs/driver/pages/.
 */
public class TestPageServer {

    private static final Logger logger = LoggerFactory.getLogger(TestPageServer.class);
    private static final String PAGES_BASE = "io/karatelabs/driver/pages/";

    private final HttpServer server;
    private final Map<String, byte[]> pageCache = new HashMap<>();

    private TestPageServer(HttpServer server) {
        this.server = server;
    }

    /**
     * Start the test page server on an available port.
     */
    public static TestPageServer start() {
        return start(0);
    }

    /**
     * Start the test page server on the specified port (0 for auto-assign).
     */
    public static TestPageServer start(int port) {
        TestPageServer instance = new TestPageServer(null);
        HttpServer server = HttpServer.start(port, instance::handle);
        return new TestPageServer(server);
    }

    /**
     * Get the port the server is running on.
     */
    public int getPort() {
        return server.getPort();
    }

    /**
     * Get the base URL for accessing pages from the host.
     */
    public String getBaseUrl() {
        return "http://localhost:" + getPort();
    }

    /**
     * Stop the server asynchronously.
     */
    public void stopAsync() {
        if (server != null) {
            server.stopAsync();
        }
    }

    /**
     * Stop the server and wait for shutdown.
     */
    public void stopAndWait() {
        if (server != null) {
            server.stopAndWait();
        }
    }

    private HttpResponse handle(HttpRequest request) {
        String path = request.getPath();
        logger.debug("test page request: {} {}", request.getMethod(), path);

        HttpResponse response = new HttpResponse();

        // Normalize path
        if (path.equals("/")) {
            path = "/index.html";
        }
        if (!path.endsWith(".html") && !path.endsWith(".css") && !path.endsWith(".js")) {
            // Try adding .html
            if (!path.contains(".")) {
                path = path + ".html";
            }
        }

        // Try to load the page
        String resourcePath = PAGES_BASE + path.substring(1); // Remove leading /
        byte[] content = loadResource(resourcePath);

        if (content == null) {
            response.setStatus(404);
            response.setBody("Not Found: " + path);
            return response;
        }

        // Determine content type
        ResourceType resourceType = getResourceType(path);
        response.setBody(content, resourceType);
        return response;
    }

    private byte[] loadResource(String resourcePath) {
        // Check cache first
        byte[] cached = pageCache.get(resourcePath);
        if (cached != null) {
            return cached;
        }

        // Load from classpath
        try {
            InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(resourcePath);
            if (is == null) {
                logger.debug("resource not found: {}", resourcePath);
                return null;
            }
            byte[] content = FileUtils.toBytes(is);
            pageCache.put(resourcePath, content);
            return content;
        } catch (Exception e) {
            logger.warn("failed to load resource: {}", resourcePath, e);
            return null;
        }
    }

    private ResourceType getResourceType(String path) {
        ResourceType rt = ResourceType.fromFileExtension(path);
        return rt != null ? rt : ResourceType.TEXT;
    }

}
