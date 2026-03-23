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
package io.karatelabs.core.mock;

import io.karatelabs.core.MockServer;
import io.karatelabs.http.ApacheHttpClient;
import io.karatelabs.http.Http;
import io.karatelabs.http.HttpClient;
import io.karatelabs.http.HttpRequestBuilder;
import io.karatelabs.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MockServer using real HTTPS connections.
 * Uses SSL to also verify SSL functionality works.
 * Disabled on Windows due to Netty SSL certificate generation issues.
 */
@DisabledOnOs(OS.WINDOWS)
class MockServerTest {

    private static MockServer server;
    private static HttpClient client;

    @BeforeAll
    static void startServer() {
        server = MockServer.featureString("""
            Feature: Integration Test Mock

            Background:
              * def users = {}
              * def counter = { value: 0 }
              * configure cors = true

            Scenario: pathMatches('/users/{id}') && methodIs('get')
              * def user = users[pathParams.id]
              * def response = user ? user : { error: 'not found' }
              * def responseStatus = user ? 200 : 404

            Scenario: pathMatches('/users') && methodIs('post')
              * counter.value = counter.value + 1
              * def id = counter.value + ''
              * users[id] = request
              * users[id].id = id
              * def response = users[id]
              * def responseStatus = 201

            Scenario: pathMatches('/users') && methodIs('get')
              * def response = users
              * def responseStatus = 200

            Scenario: pathMatches('/echo')
              * def response = { method: requestMethod, path: requestPath, params: requestParams }

            Scenario:
              * def responseStatus = 404
              * def response = { error: 'not found' }
            """)
            .port(0)
            .ssl(true)
            .start();

        client = new ApacheHttpClient();
        client.config("ssl", true);
    }

    @AfterAll
    static void stopServer() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // ignore
            }
        }
        if (server != null) {
            server.stopAsync();
        }
    }

    private HttpResponse request(String method, String path) {
        return request(method, path, null);
    }

    private HttpResponse request(String method, String path, Object body) {
        HttpRequestBuilder builder = new HttpRequestBuilder(client);
        builder.url(server.getUrl()).path(path).method(method);
        if (body != null) {
            builder.body(body);
        }
        return builder.invoke();
    }

    @Test
    void testCreateAndGetUser() {
        // Create user
        HttpResponse createResponse = request("POST", "/users", Map.of("name", "John", "email", "john@example.com"));
        assertEquals(201, createResponse.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) createResponse.getBodyConverted();
        assertNotNull(created.get("id"));
        assertEquals("John", created.get("name"));

        // Get user
        String id = created.get("id").toString();
        HttpResponse getResponse = request("GET", "/users/" + id);
        assertEquals(200, getResponse.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> fetched = (Map<String, Object>) getResponse.getBodyConverted();
        assertEquals(id, fetched.get("id"));
        assertEquals("John", fetched.get("name"));
    }

    @Test
    void testGetNonExistentUser() {
        HttpResponse response = request("GET", "/users/999");
        assertEquals(404, response.getStatus());
    }

    @Test
    void testListUsers() {
        // Create a user first
        request("POST", "/users", Map.of("name", "Jane"));

        // List all users
        HttpResponse response = request("GET", "/users");
        assertEquals(200, response.getStatus());

        Object body = response.getBodyConverted();
        assertNotNull(body);
    }

    @Test
    void testEchoEndpoint() {
        HttpResponse response = request("GET", "/echo");
        assertEquals(200, response.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBodyConverted();
        assertEquals("GET", body.get("method"));
        assertEquals("/echo", body.get("path"));
    }

    @Test
    void testCatchAll() {
        HttpResponse response = request("GET", "/unknown/path");
        assertEquals(404, response.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBodyConverted();
        assertEquals("not found", body.get("error"));
    }

    @Test
    void testServerProperties() {
        assertTrue(server.getPort() > 0);
        assertTrue(server.isSsl());
        assertEquals("https://localhost:" + server.getPort(), server.getUrl());
    }

    // ========== Http.to() API Tests ==========

    @Test
    void testHttpToGet() {
        HttpResponse response = Http.to(server.getUrl())
                .configure("ssl", true)
                .path("/echo")
                .get();

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBodyConverted();
        assertEquals("GET", body.get("method"));
        assertEquals("/echo", body.get("path"));
    }

    @Test
    void testHttpToPost() {
        HttpResponse response = Http.to(server.getUrl())
                .configure("ssl", true)
                .path("/users")
                .postJson("{\"name\": \"Alice\", \"email\": \"alice@test.com\"}");

        assertEquals(201, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBodyConverted();
        assertEquals("Alice", body.get("name"));
        assertNotNull(body.get("id"));
    }

    @Test
    void testHttpToWithParams() {
        HttpResponse response = Http.to(server.getUrl())
                .configure("ssl", true)
                .path("/echo")
                .param("foo", "bar")
                .param("count", "42")
                .get();

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBodyConverted();
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        // Params are returned as lists
        assertTrue(params.get("foo").toString().contains("bar"));
        assertTrue(params.get("count").toString().contains("42"));
    }

    @Test
    void testHttpToWithHeader() {
        HttpResponse response = Http.to(server.getUrl())
                .configure("ssl", true)
                .path("/echo")
                .header("X-Custom-Header", "test-value")
                .get();

        assertEquals(200, response.getStatus());
    }

}
