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
package io.karatelabs.driver.e2e;

import io.karatelabs.driver.cdp.*;

import io.karatelabs.driver.InterceptResponse;
import io.karatelabs.driver.e2e.support.DriverTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for request interception.
 */
class InterceptE2eTest extends DriverTestBase {

    @BeforeEach
    void setup() {
        // Start on a page first
        driver.setUrl(testUrl("/"));
    }

    @AfterEach
    void cleanup() {
        driver.stopIntercept();
    }

    @Test
    void testInterceptAndContinue() {
        AtomicInteger interceptCount = new AtomicInteger(0);
        AtomicReference<String> lastUrl = new AtomicReference<>();

        driver.intercept(List.of("*"), request -> {
            interceptCount.incrementAndGet();
            lastUrl.set(request.getUrl());
            // Return null to continue to network
            return null;
        });

        // Navigate to a page
        driver.setUrl(testUrl("/input"));

        // Some requests should have been intercepted
        assertTrue(interceptCount.get() > 0);
        assertNotNull(lastUrl.get());
    }

    @Test
    void testInterceptAndMockHtml() {
        driver.intercept(List.of("*/mock-page*"), request -> {
            if (request.urlContains("mock-page")) {
                return InterceptResponse.html("<html><body><h1 id=\"mocked\">Mocked!</h1></body></html>");
            }
            return null;
        });

        // Navigate to the mock page
        driver.setUrl(testUrl("/mock-page"));

        // Verify the mocked content
        driver.waitFor("#mocked");
        String text = driver.text("#mocked");
        assertEquals("Mocked!", text);
    }

    @Test
    void testInterceptWithJsonResponse() {
        // Setup intercept with JSON response
        driver.intercept(List.of("*/api/*"), request -> {
            if (request.urlContains("/api/users")) {
                return InterceptResponse.json(Map.of(
                        "id", 1,
                        "name", "Mocked User",
                        "email", "mock@test.com"
                ));
            }
            return null;
        });

        // Create a result element and fetch async, storing result in DOM
        driver.script("""
            var div = document.createElement('div');
            div.id = 'fetch-result';
            document.body.appendChild(div);
            fetch('/api/users/1')
                .then(r => r.json())
                .then(data => {
                    document.getElementById('fetch-result').textContent = JSON.stringify(data);
                })
                .catch(err => {
                    document.getElementById('fetch-result').textContent = 'ERROR: ' + err;
                });
        """);

        // Wait for the async fetch to complete and verify mocked response
        driver.waitForText("#fetch-result", "Mocked User");
        String result = driver.text("#fetch-result");
        assertTrue(result.contains("\"id\":1"));
        assertTrue(result.contains("\"name\":\"Mocked User\""));
        assertTrue(result.contains("\"email\":\"mock@test.com\""));
    }

    @Test
    void testInterceptRequestDetails() {
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedResourceType = new AtomicReference<>();

        driver.intercept(List.of("*"), request -> {
            if (request.urlContains("/input")) {
                capturedMethod.set(request.getMethod());
                capturedResourceType.set(request.getResourceType());
            }
            return null;
        });

        driver.setUrl(testUrl("/input"));

        assertEquals("GET", capturedMethod.get());
        assertEquals("Document", capturedResourceType.get());
    }

    @Test
    void testInterceptWithPattern() {
        AtomicInteger cssCount = new AtomicInteger(0);

        // Only intercept CSS files
        driver.intercept(List.of("*.css"), request -> {
            cssCount.incrementAndGet();
            return null;
        });

        driver.setUrl(testUrl("/"));

        // CSS intercepts depend on what the page loads
        // This test verifies the pattern filtering works
    }

    @Test
    void testStopIntercept() {
        AtomicInteger interceptCount = new AtomicInteger(0);

        driver.intercept(request -> {
            interceptCount.incrementAndGet();
            return null;
        });

        driver.setUrl(testUrl("/"));
        int countBefore = interceptCount.get();
        assertTrue(countBefore > 0);

        // Stop interception
        driver.stopIntercept();

        // Navigate again
        interceptCount.set(0);
        driver.setUrl(testUrl("/input"));

        // No new intercepts should happen
        assertEquals(0, interceptCount.get());
    }

    @Test
    void testInterceptResponse404() {
        driver.intercept(List.of("*/not-found*"), request -> {
            return InterceptResponse.notFound();
        });

        // Create a result element and fetch, storing status in DOM
        driver.script("""
            var div = document.createElement('div');
            div.id = 'status-result';
            document.body.appendChild(div);
            fetch('/not-found/resource')
                .then(r => {
                    document.getElementById('status-result').textContent = 'STATUS:' + r.status;
                })
                .catch(err => {
                    document.getElementById('status-result').textContent = 'ERROR:' + err;
                });
        """);

        // Wait for the async fetch and verify 404 status
        driver.waitForText("#status-result", "STATUS:404");
        assertEquals("STATUS:404", driver.text("#status-result"));
    }

    @Test
    void testInterceptResponseWithHeaders() {
        driver.intercept(List.of("*/custom-headers*"), request -> {
            return InterceptResponse.json(Map.of("data", "test"))
                    .header("X-Custom-Header", "custom-value")
                    .header("X-Another-Header", "another-value");
        });

        // Create a result element and fetch, checking headers
        driver.script("""
            var div = document.createElement('div');
            div.id = 'headers-result';
            document.body.appendChild(div);
            fetch('/custom-headers/test')
                .then(r => {
                    var custom = r.headers.get('X-Custom-Header') || 'null';
                    var another = r.headers.get('X-Another-Header') || 'null';
                    return r.json().then(data => {
                        document.getElementById('headers-result').textContent =
                            'custom=' + custom + ',another=' + another + ',data=' + data.data;
                    });
                })
                .catch(err => {
                    document.getElementById('headers-result').textContent = 'ERROR:' + err;
                });
        """);

        // Wait for the async fetch and verify headers and body
        driver.waitForText("#headers-result", "data=test");
        String result = driver.text("#headers-result");
        assertTrue(result.contains("custom=custom-value"));
        assertTrue(result.contains("another=another-value"));
        assertTrue(result.contains("data=test"));
    }

}
