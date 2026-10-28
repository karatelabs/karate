package io.karatelabs.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordAuthHandlerTest {

    final List<Map<String, String>> forms = Collections.synchronizedList(new ArrayList<>());
    final Deque<HttpResponse> answers = new ArrayDeque<>();
    HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stopAsync();
        }
    }

    String start() {
        server = HttpServer.start(0, request -> {
            if (!request.pathMatches("/token")) {
                return HttpResponse.text(200, String.valueOf(request.getHeader("Authorization")));
            }
            request.processBody();
            Map<String, String> form = new HashMap<>();
            request.getParams().forEach((k, v) -> form.put(k, v.getFirst()));
            forms.add(form);
            return answers.poll();
        });
        return "http://localhost:" + server.getPort();
    }

    static Map<String, Object> config(String base) {
        Map<String, Object> map = new HashMap<>();
        map.put("url", base + "/token");
        map.put("client_id", "app");
        map.put("client_secret", "s3c");
        map.put("username", "alice");
        map.put("password", "pw");
        map.put("scope", "read");
        return map;
    }

    @Test
    void signsInWithTheResourceOwnersCredentialsOnceAndSendsBearer() throws Exception {
        String base = start();
        answers.add(HttpResponse.json("{\"access_token\":\"a1\",\"expires_in\":3600}"));
        OAuth2AuthHandler handler = new PasswordAuthHandler(config(base));
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client).url(base + "/me");
            assertEquals("a1", handler.getAccessToken(http));
            assertEquals("a1", handler.getAccessToken(http));
            assertNull(http.getHeader("Authorization"));
            http.auth(handler).method("get");
            assertEquals("Bearer a1", http.invoke().getBodyString());
        }
        assertEquals(List.of(Map.of("grant_type", "password", "username", "alice", "password", "pw",
                "client_id", "app", "client_secret", "s3c", "scope", "read")), forms);
    }

    @Test
    void anExpiredTokenIsRefreshedAndARejectedRefreshSignsInAgain() throws Exception {
        String base = start();
        answers.add(HttpResponse.json("{\"access_token\":\"a1\",\"refresh_token\":\"r1\",\"expires_in\":0}"));
        answers.add(HttpResponse.json("{\"access_token\":\"a2\",\"expires_in\":0}"));
        answers.add(HttpResponse.json(400, "{\"error\":\"invalid_grant\"}"));
        answers.add(HttpResponse.json("{\"access_token\":\"a3\",\"expires_in\":3600}"));
        OAuth2AuthHandler handler = new PasswordAuthHandler(config(base));
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client).url(base + "/me");
            assertEquals("a1", handler.getAccessToken(http));
            assertEquals("a2", handler.getAccessToken(http));
            assertEquals("a3", handler.getAccessToken(http));
            assertEquals("a3", handler.getAccessToken(http));
        }
        assertEquals(List.of("password", "refresh_token", "refresh_token", "password"), forms.stream().map(f -> f.get("grant_type")).toList());
        assertEquals("r1", forms.get(1).get("refresh_token"));
        assertEquals("r1", forms.get(2).get("refresh_token"), "a refresh answer without a refresh token keeps the old one");
        assertNull(forms.get(1).get("password"));
        assertEquals("s3c", forms.get(1).get("client_secret"));
    }

    @Test
    void aRejectedSignInFailsWithTheServersError() throws Exception {
        String base = start();
        answers.add(HttpResponse.json(400, "{\"error\":\"invalid_grant\",\"error_description\":\"bad password\"}"));
        OAuth2AuthHandler handler = new PasswordAuthHandler(config(base));
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client).url(base + "/me");
            OAuth2Exception e = assertThrows(OAuth2Exception.class, () -> handler.getAccessToken(http));
            assertTrue(e.getMessage().contains("invalid_grant - bad password"), e.getMessage());
        }
    }

}
