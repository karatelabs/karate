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
package io.karatelabs.gatling;

import io.karatelabs.core.KarateConfig;
import io.karatelabs.http.HttpClient;
import io.karatelabs.http.HttpRequest;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code configure readTimeout} must bound a request made through a POOLED client.
 *
 * <p><b>It did not, and the failure was unbounded rather than merely wrong.</b> The read timeout
 * reaches the transport through the connection manager, and a client sharing a manager it did not
 * build gets whatever that manager was configured with — which, for the pooled manager, was no
 * socket timeout at all. Measured before the fix: a 1000 ms {@code readTimeout} against a server
 * that stalled for 4 s returned <em>successfully</em> after 4057 ms, having waited without limit.
 *
 * <p>That is the worst shape this could take for the thing pooling was built for. A soak runs for
 * hours against a server that may stall; a virtual user that waits forever is not a slow user, it
 * is a user that never comes back, and the run reports fewer iterations rather than an error.
 *
 * <p>The fix sets both timeouts on the per-request {@code RequestConfig} as well as on the manager.
 * Request-level values win where both exist, so it is a no-op for an unpooled client and the whole
 * behaviour for a pooled one.
 */
class PooledTimeoutTest {

    /** Accepts immediately, then stalls before replying — so connect is fast and only the read is slow. */
    private static Thread stallingResponder(ServerSocket server, long stallMillis) {
        Thread responder = new Thread(() -> {
            try (Socket socket = server.accept()) {
                socket.getInputStream().read(new byte[2048]);
                Thread.sleep(stallMillis);
                OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n"
                        + "Connection: close\r\n\r\nhi").getBytes());
                out.flush();
            } catch (Exception ignored) {
                // the socket closes with the server when the test ends
            }
        });
        responder.setDaemon(true);
        return responder;
    }

    @Test
    void testReadTimeoutBoundsARequestThroughAPooledClient() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            stallingResponder(server, 4000).start();
            PooledHttpClientFactory factory = new PooledHttpClientFactory(4, 4);
            try {
                HttpClient client = factory.create();
                KarateConfig config = new KarateConfig();
                config.configure("readTimeout", 500);
                client.apply(config);

                HttpRequest request = new HttpRequest();
                request.setUrl("http://localhost:" + server.getLocalPort() + "/slow");
                request.setMethod("GET");

                long started = System.currentTimeMillis();
                assertThrows(RuntimeException.class, () -> client.invoke(request),
                        "a pooled client must honour readTimeout — without it the request waits "
                                + "for as long as the server cares to stall");
                long elapsed = System.currentTimeMillis() - started;
                // Generous upper bound: the point is that it returned at all, well short of the
                // 4s stall, not that it landed precisely on 500ms.
                assertTrue(elapsed < 3000,
                        "should have given up near the 500ms readTimeout, but waited " + elapsed
                                + "ms — the timeout is not reaching the pooled transport");
            } finally {
                factory.close();
            }
        }
    }
}
