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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end WebSocket coverage of {@link HttpServer} + {@link WsClient}: the upgrade handshake,
 * a server-speaks-first opening frame caught by an options-registered listener (a listener added
 * only after {@code connect()} returns can miss it), and a binary echo above Netty's 64k default
 * receive cap (the handshaker is configured to 1 MB for proxied streams like a websockify tunnel).
 */
class WsServerClientIntegrationTest {

    private static HttpServer server;

    @BeforeAll
    static void start() {
        server = HttpServer.start(0,
                req -> HttpResponse.notFound("ws only"),
                null,
                (req, connection) -> {
                    // server speaks FIRST (the RFB/VNC shape), then echoes binary back
                    connection.send("hello:" + req.getPath());
                    connection.onBinary(connection::sendBytes);
                });
    }

    @AfterAll
    static void stop() {
        server.stopAsync();
    }

    @Test
    void serverGreetingAndLargeBinaryEchoRoundTrip() throws Exception {
        byte[] big = new byte[200 * 1024]; // > the 64k Netty default the handshaker overrides
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) i;
        }
        CountDownLatch greeting = new CountDownLatch(1);
        CountDownLatch echoed = new CountDownLatch(1);
        List<WsFrame> frames = new CopyOnWriteArrayList<>();
        WsClient client = WsClient.connect(WsClientOptions
                .builder("ws://localhost:" + server.getPort() + "/tunnel")
                .onMessage(frame -> {
                    frames.add(frame);
                    if (frame.isText()) {
                        greeting.countDown();
                    } else {
                        echoed.countDown();
                    }
                })
                .build());
        try {
            // the opening frame arrives even though no listener was added post-connect
            assertTrue(greeting.await(5, TimeUnit.SECONDS), "server-first greeting frame");
            assertEquals("hello:/tunnel", frames.get(0).getText());
            client.send(big);
            assertTrue(echoed.await(5, TimeUnit.SECONDS), "large binary echo");
            byte[] back = frames.get(frames.size() - 1).getBytes();
            assertTrue(Arrays.equals(big, back), "binary payload survives the round-trip");
        } finally {
            client.close();
        }
    }

}
