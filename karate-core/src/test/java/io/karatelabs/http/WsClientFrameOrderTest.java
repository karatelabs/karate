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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>A listener sees this connection's frames in the order the wire delivered them.</b>
 *
 * <p>WebSocket orders frames (RFC 6455 §1.5) and a listener is entitled to see them that way. Callbacks run
 * off the event loop so a slow listener cannot stall I/O — but handing each frame straight to a shared
 * cached pool discarded the ordering silently, and the symptom was easy to misread: a downstream consumer
 * observed its messages shuffled and it was written off as "interleaving under load".</p>
 *
 * <p>Sequence numbers rather than a set, and enough frames that a shuffle is near-certain rather than
 * occasional — this is the assertion the shuffle was hidden from.</p>
 */
class WsClientFrameOrderTest {

    private static final int FRAMES = 400;

    static HttpServer server;
    static String url;

    @BeforeAll
    static void start() {
        server = HttpServer.start(0,
                req -> HttpResponse.notFound("ws only"),
                null,
                (req, conn) -> conn.onMessage(conn::send));   // text echo
        url = "ws://localhost:" + server.getPort() + "/echo";
    }

    @AfterAll
    static void stop() {
        server.stopAsync();
    }

    @Test
    void framesReachTheListenerInWireOrder() throws Exception {
        List<String> seen = new ArrayList<>();       // deliberately NOT thread-safe: serialised or bust
        CountDownLatch all = new CountDownLatch(FRAMES);
        WsClient client = WsClient.connect(WsClientOptions.builder(url)
                .onMessage(frame -> {
                    seen.add(frame.getText());
                    all.countDown();
                })
                .build());
        try {
            for (int i = 0; i < FRAMES; i++) {
                client.send(String.valueOf(i));
            }
            assertTrue(all.await(20, TimeUnit.SECONDS), "every echo arrived: got " + seen.size());
        } finally {
            client.close();
        }
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < FRAMES; i++) {
            expected.add(String.valueOf(i));
        }
        // not a set: the ORDER is the contract the transport gives us and we must not throw away
        assertEquals(expected, seen);
    }
}
