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

import io.karatelabs.common.KarateLifecycle;
import io.karatelabs.common.KarateLifecycle.Outcome;
import io.karatelabs.common.KarateLifecycle.StopResult;
import io.karatelabs.common.Stoppable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A started server is visible through the one registry an embedding application is expected
 * to use, and disappears from it once stopped.
 */
class HttpServerLifecycleTest {

    @AfterEach
    void resetRegistry() {
        // the register is process-wide and one-way (RUNNING -> STOPPED): without this, every
        // server started by a later test in this JVM would be stopped the moment it registers
        KarateLifecycle.reset();
    }

    private static boolean isRegistered(HttpServer server) {
        for (Stoppable stoppable : KarateLifecycle.running()) {
            if (stoppable == server) {
                return true;
            }
        }
        return false;
    }

    @Test
    void serverAppearsInRunningAndLeavesOnStop() {
        HttpServer server = HttpServer.start(0, req -> HttpResponse.text("ok"));
        try {
            assertTrue(isRegistered(server));
            assertEquals("http-server", server.lifecycleKind());
            assertEquals("http-server:" + server.getPort(), server.lifecycleName());
        } finally {
            server.stopAndWait();
        }
        assertFalse(isRegistered(server));
    }

    @Test
    void shutdownAllDrainsAStartedServer() {
        HttpServer server = HttpServer.start(0, req -> HttpResponse.text("ok"));
        assertTrue(isRegistered(server));
        // the register is process-wide, so a server another test left running would show up here too
        List<StopResult> results = KarateLifecycle.shutdownAll(Duration.ofSeconds(10));
        StopResult mine = results.stream()
                .filter(r -> r.name().equals("http-server:" + server.getPort()))
                .findFirst().orElseThrow(() -> new AssertionError("not in summary: " + results));
        assertEquals(Outcome.STOPPED, mine.outcome());
        assertEquals("http-server", mine.kind());
        assertFalse(isRegistered(server));
        // stop() is blocking, so the socket is already gone by the time shutdownAll returns
        assertThrows(IOException.class, () -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", server.getPort()), 2000);
            }
        });
    }

}
