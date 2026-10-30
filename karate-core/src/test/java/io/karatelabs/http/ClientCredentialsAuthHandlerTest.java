package io.karatelabs.http;

import io.karatelabs.common.Json;
import io.karatelabs.common.ResourceType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClientCredentialsAuthHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(ClientCredentialsAuthHandlerTest.class);

    @Test
    void testOauth() {
        HttpServer server = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            if (request.pathMatches("/token")) {
                response.setBody(Json.toBytes(Json.of("{ access_token: 'foo' }").asMap()), ResourceType.JSON);
            } else {
                String authHeader = request.getHeader("authorization");
                response.setBody(authHeader, ResourceType.TEXT);
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

    @Test
    void accessTokenIsFetchedOnceAndLeavesTheRequestUnsigned() {
        AtomicInteger fetches = new AtomicInteger();
        HttpServer server = HttpServer.start(0, request -> {
            fetches.incrementAndGet();
            HttpResponse response = new HttpResponse();
            response.setBody(Json.toBytes(Json.of("{ access_token: 'foo' }").asMap()), ResourceType.JSON);
            return response;
        });
        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> map = new HashMap<>();
            map.put("url", "http://localhost:" + server.getPort() + "/token");
            map.put("client_id", "demo-client");
            map.put("client_secret", "demo-secret");
            OAuth2AuthHandler handler = new ClientCredentialsAuthHandler(map);
            HttpRequestBuilder http = new HttpRequestBuilder(client).url("http://localhost:" + server.getPort() + "/test");
            assertEquals("foo", handler.getAccessToken(http));
            assertEquals("foo", handler.getAccessToken(http));
            assertEquals(1, fetches.get());
            assertNull(http.getHeader("Authorization"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            server.stopAsync();
        }
    }

    /** The token request's Authorization header and form fields, for the given {@code client_authentication}. */
    static List<Object> tokenRequest(String clientAuthentication) {
        List<Object> seen = new ArrayList<>();
        HttpServer server = HttpServer.start(0, request -> {
            request.processBody();
            Map<String, String> form = new HashMap<>();
            request.getParams().forEach((k, v) -> form.put(k, v.getFirst()));
            seen.add(request.getHeader("Authorization"));
            seen.add(form);
            return HttpResponse.json("{\"access_token\":\"foo\"}");
        });
        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> map = new HashMap<>();
            map.put("url", "http://localhost:" + server.getPort() + "/token");
            map.put("client_id", "my app");
            map.put("client_secret", "s3c:r&t");
            if (clientAuthentication != null) {
                map.put("client_authentication", clientAuthentication);
            }
            HttpRequestBuilder http = new HttpRequestBuilder(client).url("http://localhost:" + server.getPort() + "/test");
            assertEquals("foo", new ClientCredentialsAuthHandler(map).getAccessToken(http));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            server.stopAsync();
        }
        return seen;
    }

    @Test
    void headerClientAuthenticationSendsBasicAndNoCredentialsInTheForm() {
        List<Object> seen = tokenRequest("header");
        String basic = "Basic " + Base64.getEncoder().encodeToString("my+app:s3c%3Ar%26t".getBytes(StandardCharsets.UTF_8));
        assertEquals(basic, seen.get(0));
        assertEquals(Map.of("grant_type", "client_credentials"), seen.get(1));
    }

    @Test
    void bodyClientAuthenticationOrNoneSendsTheCredentialsInTheForm() {
        for (String mode : new String[]{"body", null}) {
            List<Object> seen = tokenRequest(mode);
            assertNull(seen.get(0), mode);
            assertEquals(Map.of("grant_type", "client_credentials", "client_id", "my app", "client_secret", "s3c:r&t"), seen.get(1), mode);
        }
    }

}
