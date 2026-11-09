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
package io.karatelabs.driver.cdp;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CdpClientOriginTest {

    @Test
    void theHandshakeCarriesNoOriginSoAnAttachedBrowserAcceptsIt() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<List<String>> request = CompletableFuture.supplyAsync(() -> {
                try (Socket socket = server.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    List<String> lines = new ArrayList<>();
                    for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                        lines.add(line);
                    }
                    // what Chrome answers a handshake whose Origin --remote-allow-origins does not allow
                    OutputStream out = socket.getOutputStream();
                    out.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    return lines;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            String url = "ws://127.0.0.1:" + server.getLocalPort() + "/devtools/browser/x";
            assertThrows(RuntimeException.class, () -> CdpClient.connect(url, Duration.ofSeconds(5)));
            List<String> lines = request.get(5, TimeUnit.SECONDS);
            assertTrue(lines.get(0).startsWith("GET /devtools/browser/x "), lines.get(0));
            assertTrue(lines.stream().noneMatch(l -> l.regionMatches(true, 0, "Origin:", 0, 7)), String.join("\n", lines));
        }
    }

}
