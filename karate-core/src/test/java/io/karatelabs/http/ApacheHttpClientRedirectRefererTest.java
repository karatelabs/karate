package io.karatelabs.http;

import io.karatelabs.core.KarateConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ApacheHttpClientRedirectRefererTest {

    HttpServer server;
    String base;
    final Map<String, String> refererSeen = new ConcurrentHashMap<>();

    @BeforeEach
    void start() {
        server = HttpServer.start(0, request -> {
            String path = request.getPath();
            refererSeen.put(path, String.valueOf(request.getHeader("Referer")));
            HttpResponse response = HttpResponse.json("{}");
            switch (path) {
                case "/a" -> {
                    response.setStatus(302);
                    response.setHeader("Location", "/b");
                }
                case "/b" -> {
                    response.setStatus(301);
                    response.setHeader("Location", base + "/c?x=1");
                }
                default -> {
                }
            }
            return response;
        });
        base = "http://localhost:" + server.getPort();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    HttpRequest get(String url) {
        HttpRequest request = new HttpRequest();
        request.setUrl(url);
        request.setMethod("GET");
        return request;
    }

    @Test
    void eachFollowedHopCarriesThePreviousUrlAsReferer() throws Exception {
        try (ApacheHttpClient client = new ApacheHttpClient()) {
            KarateConfig config = new KarateConfig();
            config.configure("redirectReferer", true);
            client.apply(config);
            HttpRequest request = get(base + "/a?q=1");
            HttpResponse response = client.invoke(request);
            assertEquals(200, response.getStatus());
            assertEquals("null", refererSeen.get("/a"));
            assertEquals(base + "/a?q=1", refererSeen.get("/b"));
            assertEquals(base + "/b", refererSeen.get("/c"));
            assertEquals(base + "/b", response.getRequest().getHeader("Referer"));
        }
    }

    @Test
    void noRefererByDefault() throws Exception {
        try (ApacheHttpClient client = new ApacheHttpClient()) {
            client.apply(new KarateConfig());
            HttpResponse response = client.invoke(get(base + "/a"));
            assertEquals(200, response.getStatus());
            assertEquals("null", refererSeen.get("/b"));
            assertEquals("null", refererSeen.get("/c"));
        }
    }

}
