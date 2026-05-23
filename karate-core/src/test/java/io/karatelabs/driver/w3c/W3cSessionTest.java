/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.driver.w3c;

import io.karatelabs.driver.DriverException;
import io.karatelabs.http.HttpResponse;
import io.karatelabs.http.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class W3cSessionTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "secret";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final String SESSION_RESPONSE =
            "{\"value\":{\"sessionId\":\"mock-session-id\",\"capabilities\":{}}}";

    private static HttpServer authServer;
    private static int authPort;

    @BeforeAll
    static void startMockServers() {
        authServer = HttpServer.start(0, request -> {
            if ("/session".equals(request.getPath()) && "POST".equals(request.getMethod())) {
                String auth = request.getHeader("Authorization");
                String expected = "Basic " + Base64.getEncoder()
                        .encodeToString((USERNAME + ":" + PASSWORD).getBytes());
                if (auth == null || !auth.equals(expected)) {
                    HttpResponse r = new HttpResponse();
                    r.setStatus(401, "Unauthorized");
                    r.setHeader("WWW-Authenticate", "Basic realm=\"WebDriver\"");
                    return r;
                }
                return HttpResponse.json(SESSION_RESPONSE);
            }
            return HttpResponse.notFound("Not Found");
        });
        authPort = authServer.getPort();
    }

    @AfterAll
    static void stopMockServers() {
        if (authServer != null) {
            authServer.stopAndWait();
        }
    }

    @Test
    void testCreateSessionWithBasicAuthInUrl() {
        String url = "http://" + USERNAME + ":" + PASSWORD + "@localhost:" + authPort;
        Map<String, Object> payload = Map.of(
                "capabilities", Map.of("alwaysMatch", Map.of("browserName", "chrome"))
        );
        W3cSession session = W3cSession.create(url, payload, TIMEOUT);
        assertEquals("mock-session-id", session.getSessionId());
    }

    @Test
    void testCreateSessionFailsWithoutCredentials() {
        String url = "http://localhost:" + authPort;
        Map<String, Object> payload = Map.of(
                "capabilities", Map.of("alwaysMatch", Map.of("browserName", "chrome"))
        );
        DriverException e = assertThrows(DriverException.class, () ->
                W3cSession.create(url, payload, TIMEOUT)
        );
        assertTrue(e.getMessage().contains("401"), e.getMessage());
    }

    @Test
    void testCreateSessionFailsWithWrongCredentials() {
        String url = "http://wrong:creds@localhost:" + authPort;
        Map<String, Object> payload = Map.of(
                "capabilities", Map.of("alwaysMatch", Map.of("browserName", "chrome"))
        );
        DriverException e = assertThrows(DriverException.class, () ->
                W3cSession.create(url, payload, TIMEOUT)
        );
        assertTrue(e.getMessage().contains("401"), e.getMessage());
    }

}
