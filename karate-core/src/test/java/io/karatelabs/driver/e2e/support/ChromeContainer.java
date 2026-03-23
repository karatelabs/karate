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

import io.karatelabs.driver.cdp.CdpDriver;
import io.karatelabs.driver.cdp.CdpDriverOptions;
import net.minidev.json.JSONValue;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Testcontainers wrapper for Chrome browser using chromedp/headless-shell (~200MB).
 * Provides Docker-based Chrome for reproducible, isolated E2E testing.
 *
 * <h2>Networking: Local vs GitHub Actions</h2>
 * <p>
 * The container needs to access test servers running on the host machine.
 * This works differently depending on the environment:
 * </p>
 * <ul>
 *   <li><b>macOS/Windows Docker Desktop:</b> Uses a Linux VM, so "host.docker.internal"
 *       is automatically available and resolves to the host machine.</li>
 *   <li><b>Linux (including GitHub Actions):</b> Docker runs natively without a VM.
 *       "host.docker.internal" requires explicit configuration via --add-host flag
 *       (supported in Docker 20.10+). We also rely on testcontainers' exposeHostPorts()
 *       which creates a SOCKS proxy container and makes "host.testcontainers.internal"
 *       available.</li>
 * </ul>
 *
 * <h2>Usage Requirements</h2>
 * <p>
 * Before starting this container, callers MUST call:
 * {@code Testcontainers.exposeHostPorts(port)} for each host port the container needs
 * to access. This sets up the network proxy that makes host ports accessible.
 * </p>
 *
 * @see org.testcontainers.Testcontainers#exposeHostPorts(int...)
 */
public class ChromeContainer extends GenericContainer<ChromeContainer> {

    private static final String IMAGE = "chromedp/headless-shell:latest";
    private static final int CDP_PORT = 9222;

    public ChromeContainer() {
        super(DockerImageName.parse(IMAGE));
        withExposedPorts(CDP_PORT);
        // Pass additional Chrome flags to allow WebSocket connections from any origin
        // The chromedp/headless-shell image's entrypoint script appends these to Chrome
        withCommand("--remote-allow-origins=*");
        waitingFor(Wait.forHttp("/json/version").forPort(CDP_PORT));
        withStartupTimeout(Duration.ofMinutes(2));

        // Linux/GitHub Actions: Add host.docker.internal mapping to host-gateway
        // This makes host.docker.internal work on native Linux Docker (20.10+)
        // On Docker Desktop (macOS/Windows), this is a no-op since it's already available
        withExtraHost("host.docker.internal", "host-gateway");
    }

    /**
     * Get the WebSocket URL for CDP connection.
     */
    public String getCdpUrl() {
        String host = getHost();
        int port = getMappedPort(CDP_PORT);
        return fetchWebSocketUrl(host, port);
    }

    /**
     * Create a CdpDriver connected to this container.
     */
    public CdpDriver createDriver() {
        return CdpDriver.connect(getCdpUrl());
    }

    /**
     * Create a CdpDriver connected to this container with custom options.
     */
    public CdpDriver createDriver(CdpDriverOptions options) {
        return CdpDriver.connect(getCdpUrl(), options);
    }

    /**
     * Get the base URL for accessing services running on the host from inside the container.
     * Use this for navigating to test pages served by TestPageServer.
     *
     * <p>This uses "host.testcontainers.internal" which is set up by testcontainers when
     * {@code Testcontainers.exposeHostPorts(port)} is called. This hostname resolves to
     * a SOCKS proxy container that forwards traffic to the host machine.</p>
     *
     * <p><b>Important:</b> The port must have been exposed via {@code exposeHostPorts()}
     * BEFORE the container was started, otherwise the connection will fail.</p>
     *
     * @param hostPort the port number on the host machine to connect to
     * @return URL that can be used from within the container to access the host service
     */
    public String getHostAccessUrl(int hostPort) {
        // host.testcontainers.internal is set up by Testcontainers.exposeHostPorts()
        // It routes through a SOCKS proxy container to reach the host machine
        // This works on all platforms: macOS, Windows, and Linux (including GitHub Actions)
        return "http://host.testcontainers.internal:" + hostPort;
    }

    @SuppressWarnings("unchecked")
    private String fetchWebSocketUrl(String host, int port) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        try {
            // Always create a new tab/target for each caller
            // This enables parallel execution - each thread gets its own page
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + host + ":" + port + "/json/new?about:blank"))
                    .timeout(Duration.ofSeconds(10))
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> newTarget = (Map<String, Object>) JSONValue.parse(response.body());
                if (newTarget != null) {
                    String wsUrl = (String) newTarget.get("webSocketDebuggerUrl");
                    if (wsUrl != null) {
                        return wsUrl.replace("localhost", host).replace("127.0.0.1", host);
                    }
                }
            }

            throw new RuntimeException("Failed to create new tab in Chrome container");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch WebSocket URL: " + e.getMessage(), e);
        }
    }

}
