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

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WsClientOptionsTest {

    @Test
    void testDefaultsForWs() {
        WsClientOptions options = WsClientOptions.builder("ws://localhost:8080/path").build();
        assertEquals(URI.create("ws://localhost:8080/path"), options.getUri());
        assertEquals("localhost", options.getHost());
        assertEquals(8080, options.getPort());
        assertFalse(options.isSsl());
        assertFalse(options.isCompression());
        assertEquals(HttpUtils.MEGABYTE, options.getMaxPayloadSize());
        assertEquals(Duration.ofSeconds(30), options.getConnectTimeout());
        assertEquals(Duration.ofSeconds(30), options.getPingInterval());
        assertTrue(options.isTrustAllCerts());
        assertTrue(options.getHeaders().isEmpty());
    }

    @Test
    void testDefaultsForWss() {
        WsClientOptions options = WsClientOptions.builder("wss://secure.example.com").build();
        assertTrue(options.isSsl());
        assertEquals("secure.example.com", options.getHost());
        assertEquals(443, options.getPort());
    }

    @Test
    void testDefaultPortForWs() {
        WsClientOptions options = WsClientOptions.builder("ws://example.com/ws").build();
        assertEquals(80, options.getPort());
    }

    @Test
    void testDefaultPortForWss() {
        WsClientOptions options = WsClientOptions.builder("wss://example.com/ws").build();
        assertEquals(443, options.getPort());
    }

    @Test
    void testCustomOptions() {
        WsClientOptions options = WsClientOptions.builder("ws://localhost:9222")
                .compression(true)
                .maxPayloadSize(2 * HttpUtils.MEGABYTE)
                .connectTimeout(Duration.ofSeconds(10))
                .pingInterval(Duration.ofSeconds(15))
                .trustAllCerts(false)
                .build();

        assertTrue(options.isCompression());
        assertEquals(2 * HttpUtils.MEGABYTE, options.getMaxPayloadSize());
        assertEquals(Duration.ofSeconds(10), options.getConnectTimeout());
        assertEquals(Duration.ofSeconds(15), options.getPingInterval());
        assertFalse(options.isTrustAllCerts());
    }

    @Test
    void testDisablePing() {
        WsClientOptions options = WsClientOptions.builder("ws://localhost:9222")
                .disablePing()
                .build();
        assertNull(options.getPingInterval());
    }

    @Test
    void testHeaders() {
        WsClientOptions options = WsClientOptions.builder("ws://localhost:9222")
                .headers(Map.of("Authorization", "Bearer token"))
                .build();

        assertEquals("Bearer token", options.getHeaders().get("Authorization"));
    }

    @Test
    void testHeadersImmutable() {
        WsClientOptions options = WsClientOptions.builder("ws://localhost:9222")
                .header("Key", "Value")
                .build();

        assertThrows(UnsupportedOperationException.class, () -> {
            options.getHeaders().put("New", "Header");
        });
    }

    @Test
    void testBuilderFromURI() {
        URI uri = URI.create("wss://example.com:9443/api");
        WsClientOptions options = WsClientOptions.builder(uri).build();

        assertEquals(uri, options.getUri());
        assertEquals("example.com", options.getHost());
        assertEquals(9443, options.getPort());
        assertTrue(options.isSsl());
    }

    @Test
    void testNullUriThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            WsClientOptions.builder((URI) null);
        });
    }

}
