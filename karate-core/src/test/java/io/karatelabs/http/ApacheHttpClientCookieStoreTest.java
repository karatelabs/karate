package io.karatelabs.http;

import io.karatelabs.core.KarateConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ApacheHttpClientCookieStoreTest {

    HttpServer server;
    final Map<String, String> cookieSeen = new ConcurrentHashMap<>();

    @BeforeEach
    void start() {
        server = HttpServer.start(0, request -> {
            String path = request.getPath();
            cookieSeen.put(path, String.valueOf(request.getHeader("Cookie")));
            HttpResponse response = HttpResponse.json("{}");
            switch (path) {
                case "/login" -> response.setHeader("Set-Cookie", "session=abc; Path=/");
                case "/hop" -> {
                    response.setStatus(302);
                    response.setHeader("Location", "/plain");
                    response.setHeader("Set-Cookie", "hop=1; Path=/");
                }
                case "/dead-hop" -> {
                    response.setStatus(302);
                    response.setHeader("Location", "http://127.0.0.1:1/nope");
                    response.setHeader("Set-Cookie", "leak=1; Path=/");
                }
                default -> {
                }
            }
            return response;
        });
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    HttpResponse get(HttpClient client, String path) {
        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost:" + server.getPort() + path);
        request.setMethod("GET");
        return client.invoke(request);
    }

    @Test
    void aSuppliedStoreKeepsItsCookiesAcrossRequestsAndRebuilds() throws Exception {
        BasicCookieStore jar = new BasicCookieStore();
        try (ApacheHttpClient client = new ApacheHttpClient(jar)) {
            assertSame(jar, client.getCookieStore());
            get(client, "/login");
            assertEquals(List.of("session"), jar.getCookies().stream().map(c -> c.getName()).toList());
            KarateConfig config = new KarateConfig();
            config.configure("readTimeout", 12345);
            client.apply(config);
            get(client, "/api");
            assertEquals("session=abc", cookieSeen.get("/api"));
            assertEquals(1, jar.getCookies().size(), "a supplied store is never cleared");
        }
    }

    @Test
    void onlyThisExchangesRedirectCookiesMergeIntoSetCookie() throws Exception {
        try (ApacheHttpClient client = new ApacheHttpClient(new BasicCookieStore())) {
            HttpResponse hop = get(client, "/hop");
            assertEquals(200, hop.getStatus());
            assertTrue(hop.getHeaderValues("Set-Cookie").stream().anyMatch(v -> v.startsWith("hop=1")), "a redirect hop's cookie reaches the final response");
            assertEquals("hop=1", cookieSeen.get("/plain"));
            HttpResponse plain = get(client, "/plain");
            assertNull(plain.getHeaderValues("Set-Cookie"), "a cookie from an earlier exchange is not this response's Set-Cookie");
        }
    }

    @Test
    void aFailedRequestLeavesNoCookieForTheNextOne() throws Exception {
        try (ApacheHttpClient client = new ApacheHttpClient()) {
            assertNull(client.getCookieStore());
            assertThrows(RuntimeException.class, () -> get(client, "/dead-hop"));
            get(client, "/next");
            assertEquals("null", cookieSeen.get("/next"));
        }
    }

}
