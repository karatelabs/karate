package io.karatelabs.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** {@link HttpClient#abort()} from another thread ends the call in flight promptly, with an exception. */
class ApacheHttpClientAbortTest {

    HttpServer server;
    final CountDownLatch arrived = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void afterEach() {
        release.countDown();
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void abortEndsTheCallInFlight() throws Exception {
        server = HttpServer.start(0, request -> {
            arrived.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return HttpResponse.json("{}");
        });
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.url("http://localhost:" + server.getPort() + "/slow");
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicLong finishedAt = new AtomicLong();
            Thread thread = new Thread(() -> {
                try {
                    http.invoke("get");
                } catch (Throwable t) {
                    failure.set(t);
                }
                finishedAt.set(System.nanoTime());
            });
            thread.start();
            assertTrue(arrived.await(5, TimeUnit.SECONDS));
            long at = System.nanoTime();
            client.abort();
            thread.join(5000);
            assertFalse(thread.isAlive(), "the call did not end");
            long took = (finishedAt.get() - at) / 1_000_000;
            assertTrue(took < 1000, "took " + took + " ms");
            assertNotNull(failure.get(), "an aborted call fails");
        }
    }

    @Test
    void abortBeforeTheRequestIsAssignedStillAbortsIt() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        server = HttpServer.start(0, request -> {
            hits.incrementAndGet();
            return HttpResponse.json("{}");
        });
        try (ApacheHttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.url("http://localhost:" + server.getPort() + "/fast");
            // lands in the window between the caller's check and the request's assignment
            client.beforeAssign = client::abort;
            assertThrows(RuntimeException.class, () -> http.invoke("get"));
            assertEquals(0, hits.get(), "an aborted call is not sent");
            // the abort belonged to that invocation: the next one runs
            client.beforeAssign = null;
            http.url("http://localhost:" + server.getPort() + "/fast");
            assertEquals(200, http.invoke("get").getStatus());
            assertEquals(1, hits.get());
        }
    }

}
