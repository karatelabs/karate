package io.karatelabs.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A 401 drops only the token generation its request was signed with: a late 401 for an older request does not
 * drop a renewed token, even when the renewal returned the same access token string.
 */
class OAuth2TokenGenerationTest {

    final AtomicInteger fetches = new AtomicInteger();
    final AtomicBoolean rejectOnce = new AtomicBoolean(true);
    final CountDownLatch slowEntered = new CountDownLatch(1);
    final CountDownLatch releaseSlow = new CountDownLatch(1);
    HttpServer server;

    @AfterEach
    void stop() {
        releaseSlow.countDown();
        if (server != null) {
            server.stopAsync();
        }
    }

    @Test
    void aLate401ForAnOlderGenerationKeepsTheRenewedToken() throws Exception {
        server = HttpServer.start(0, request -> {
            if (request.pathMatches("/token")) {
                fetches.incrementAndGet();
                return HttpResponse.json("{\"access_token\":\"same\",\"expires_in\":3600}");
            }
            if (request.pathMatches("/slow")) {
                slowEntered.countDown();
                try {
                    releaseSlow.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return HttpResponse.text(401, "late");
            }
            if (request.pathMatches("/revoke") && rejectOnce.getAndSet(false)) {
                return HttpResponse.text(401, "revoked");
            }
            return HttpResponse.text(200, "ok");
        });
        String base = "http://localhost:" + server.getPort();
        Map<String, Object> config = new HashMap<>();
        config.put("url", base + "/token");
        config.put("client_id", "app");
        config.put("client_secret", "s3c");
        OAuth2AuthHandler handler = new ClientCredentialsAuthHandler(config);
        try (HttpClient slowClient = new ApacheHttpClient(); HttpClient client = new ApacheHttpClient()) {
            // signed with the first generation, answered 401 only after the token has been renewed
            CompletableFuture<HttpResponse> late = CompletableFuture.supplyAsync(() ->
                    new HttpRequestBuilder(slowClient).url(base + "/slow").auth(handler).invoke("get"));
            slowEntered.await(10, TimeUnit.SECONDS);
            assertEquals(401, new HttpRequestBuilder(client).url(base + "/revoke").auth(handler).invoke("get").getStatus());
            assertEquals(200, new HttpRequestBuilder(client).url(base + "/revoke").auth(handler).invoke("get").getStatus());
            assertEquals(2, fetches.get(), "the 401 dropped the first generation");
            releaseSlow.countDown();
            assertEquals(401, late.get(10, TimeUnit.SECONDS).getStatus());
            assertEquals(200, new HttpRequestBuilder(client).url(base + "/ok").auth(handler).invoke("get").getStatus());
        }
        assertEquals(2, fetches.get(), "the late 401 was for the first generation, so the renewed token stands");
    }

}
