package io.karatelabs.http;

import io.karatelabs.common.Json;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientCredentialsAuthHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(ClientCredentialsAuthHandlerTest.class);

    @Test
    void testOauth() {
        HttpServer server = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            if (request.pathMatches("/token")) {
                response.setBody(Json.of("{ access_token: 'foo' }").asMap());
            } else {
                String authHeader = request.getHeader("authorization");
                response.setBody(authHeader);
            }
            return response;
        });
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            Map<String, Object> map = new HashMap<>();
            map.put("url", "http://localhost:" + server.getPort() + "/token");
            map.put("client_id", "demo-client");
            map.put("client_secret", "demo-secret");
            map.put("scope", "profile");
            ClientCredentialsAuthHandler handler = new ClientCredentialsAuthHandler(map);
            http.auth(handler);
            http.url("http://localhost:" + server.getPort() + "/test");
            http.method("get");
            HttpResponse response = http.invoke();
            String body = response.getBodyString();
            assertEquals("Bearer foo", body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
