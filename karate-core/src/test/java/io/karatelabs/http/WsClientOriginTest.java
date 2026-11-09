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
package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class WsClientOriginTest {

    private static List<String> handshakeOrigins(Function<String, WsClientOptions.Builder> options) throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<List<String>> request = CompletableFuture.supplyAsync(() -> {
                try (Socket socket = server.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    List<String> lines = new ArrayList<>();
                    for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                        lines.add(line);
                    }
                    OutputStream out = socket.getOutputStream();
                    out.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    return lines;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            String url = "ws://127.0.0.1:" + server.getLocalPort() + "/ws";
            assertThrows(WsException.class, () -> WsClient.connect(options.apply(url).disablePing().build()));
            return request.get(5, TimeUnit.SECONDS).stream()
                    .filter(l -> l.regionMatches(true, 0, "Origin:", 0, 7))
                    .map(l -> l.substring(7).trim())
                    .toList();
        }
    }

    @Test
    void theHandshakeSendsAnOriginByDefault() throws Exception {
        List<String> origins = handshakeOrigins(WsClientOptions::builder);
        assertEquals(1, origins.size(), origins.toString());
        assertTrue(origins.get(0).startsWith("http://127.0.0.1:"), origins.get(0));
    }

    @Test
    void aCallersOwnOriginIsSentAsGiven() throws Exception {
        List<String> origins = handshakeOrigins(url -> WsClientOptions.builder(url).header("Origin", "http://example.test"));
        assertEquals(List.of("http://example.test"), origins);
    }

    @Test
    void withoutAGeneratedOriginOnlyACallersOwnIsSent() throws Exception {
        assertEquals(List.of(), handshakeOrigins(url -> WsClientOptions.builder(url).generateOriginHeader(false)));
        List<String> origins = handshakeOrigins(url -> WsClientOptions.builder(url)
                .generateOriginHeader(false).header("Origin", "http://example.test"));
        assertEquals(List.of("http://example.test"), origins);
    }

}
