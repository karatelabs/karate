package io.karatelabs.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ApacheHttpClientUserAgentTest {

    HttpServer server;
    final List<String> seen = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() {
        server = HttpServer.start(0, request -> {
            seen.add(String.valueOf(request.getHeader("User-Agent")));
            if ("/hop".equals(request.getPath())) {
                HttpResponse response = HttpResponse.text(302, "");
                response.setHeader("Location", "/final");
                return response;
            }
            return HttpResponse.text(200, "ok");
        });
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    HttpRequest request(String path) {
        HttpRequest request = new HttpRequest();
        request.setMethod("GET");
        request.setUrl("http://localhost:" + server.getPort() + path);
        return request;
    }

    @Test
    void theClientsUserAgentIsSentByDefault() throws Exception {
        try (ApacheHttpClient client = new ApacheHttpClient()) {
            client.invoke(request("/"));
        }
        assertTrue(seen.getFirst().startsWith("Apache-HttpClient/"), seen.getFirst());
    }

    @Test
    void noDefaultUserAgentSendsNone() throws Exception {
        try (ApacheHttpClient client = new ApacheHttpClient()) {
            HttpRequest request = request("/hop");
            request.setDefaultUserAgent(false);
            assertEquals(200, client.invoke(request).getStatus());
            client.invoke(request("/"));
        }
        assertEquals("null", seen.get(0));
        assertEquals("null", seen.get(1), "nor on a followed redirect");
        assertTrue(seen.get(2).startsWith("Apache-HttpClient/"), "the next request is unaffected: " + seen.get(2));
    }

    @Test
    void noDefaultUserAgentStillSendsTheRequestsOwn() throws Exception {
        try (ApacheHttpClient client = new ApacheHttpClient()) {
            HttpRequest request = request("/");
            request.putHeader("User-Agent", "mine");
            request.setDefaultUserAgent(false);
            client.invoke(request);
        }
        assertEquals(List.of("mine"), seen);
    }

}
