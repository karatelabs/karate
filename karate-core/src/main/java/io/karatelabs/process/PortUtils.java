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
package io.karatelabs.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Utilities for waiting on ports and HTTP endpoints.
 */
public final class PortUtils {

    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    private PortUtils() {
    }

    /**
     * Wait for a TCP port to become available.
     *
     * @param host       Host to connect to
     * @param port       Port number
     * @param attempts   Maximum number of attempts
     * @param intervalMs Interval between attempts in milliseconds
     * @param stillAlive Supplier that returns false if we should stop waiting (e.g., process died)
     * @return true if port is available, false if timed out or process died
     */
    public static boolean waitForPort(String host, int port, int attempts, int intervalMs, BooleanSupplier stillAlive) {
        for (int i = 0; i < attempts; i++) {
            if (stillAlive != null && !stillAlive.getAsBoolean()) {
                logger.warn("process died while waiting for port {}:{}", host, port);
                return false;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1000);
                logger.debug("port {}:{} is available after {} attempts", host, port, i + 1);
                return true;
            } catch (Exception e) {
                logger.trace("port {}:{} not yet available (attempt {})", host, port, i + 1);
                sleep(intervalMs);
            }
        }
        logger.warn("port {}:{} not available after {} attempts", host, port, attempts);
        return false;
    }

    /**
     * Wait for an HTTP endpoint to return 200.
     */
    public static boolean waitForHttp(String url, int attempts, int intervalMs, BooleanSupplier stillAlive) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        for (int i = 0; i < attempts; i++) {
            if (stillAlive != null && !stillAlive.getAsBoolean()) {
                logger.warn("process died while waiting for HTTP {}", url);
                return false;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    logger.debug("HTTP {} is available after {} attempts", url, i + 1);
                    return true;
                }
                logger.trace("HTTP {} returned {} (attempt {})", url, response.statusCode(), i + 1);
            } catch (Exception e) {
                logger.trace("HTTP {} not available (attempt {}): {}", url, i + 1, e.getMessage());
            }
            sleep(intervalMs);
        }
        logger.warn("HTTP {} not available after {} attempts", url, attempts);
        return false;
    }

    /**
     * Find a free port.
     */
    public static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException("failed to find free port", e);
        }
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
