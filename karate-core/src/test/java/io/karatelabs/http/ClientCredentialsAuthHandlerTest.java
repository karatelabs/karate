package io.karatelabs.http;

import io.karatelabs.common.Json;
import io.karatelabs.common.ResourceType;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** A token request to a server whose {@code /token} answers {@code answer}: the paths asked for and the lines logged. */
    record Exchange(List<String> paths, String log, String token, OAuth2Exception failure) {
    }

    static Exchange acquire(HttpResponse answer, boolean confidential) {
        List<String> paths = new ArrayList<>();
        HttpServer server = HttpServer.start(0, request -> {
            paths.add(request.getPath());
            return request.pathMatches("/token") ? answer : HttpResponse.json("{\"access_token\":\"redirected\"}");
        });
        io.karatelabs.output.LogContext log = io.karatelabs.output.LogContext.get();
        log.collect();
        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> auth = new HashMap<>();
            auth.put("type", "oauth2");
            auth.put("accessTokenUrl", "http://localhost:" + server.getPort() + "/token");
            auth.put("clientId", "demo-client");
            auth.put("clientSecret", "demo-secret");
            HttpRequestBuilder http = new HttpRequestBuilder(client).url("http://localhost:" + server.getPort() + "/test");
            OAuth2AuthHandler handler = (OAuth2AuthHandler) AuthHandlers.of(auth, http.getUrl(), null, confidential);
            try {
                String token = handler.getAccessToken(http);
                return new Exchange(paths, log.collect(), token, null);
            } catch (OAuth2Exception e) {
                return new Exchange(paths, log.collect(), null, e);
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            server.stopAsync();
        }
    }

    static HttpResponse redirect() {
        HttpResponse redirect = new HttpResponse();
        redirect.setStatus(307);
        redirect.setHeader("Location", "/elsewhere");
        return redirect;
    }

    @Test
    void aTokenRequestFollowsARedirectAndLogsItsExchangeByDefault() {
        Exchange exchange = acquire(redirect(), false);
        assertEquals("redirected", exchange.token());
        assertEquals(List.of("/token", "/elsewhere"), exchange.paths());
        assertTrue(exchange.log().contains("client_secret=demo-secret"), exchange.log());
        assertTrue(exchange.log().contains("redirected"), exchange.log());
    }

    @Test
    void aConfidentialTokenRequestFollowsNoRedirectAndLogsNoHeaderOrBody() {
        Exchange exchange = acquire(redirect(), true);
        assertNotNull(exchange.failure());
        assertEquals(List.of("/token"), exchange.paths());
        Exchange ok = acquire(HttpResponse.json("{\"access_token\":\"minted\"}"), true);
        assertEquals("minted", ok.token());
        assertTrue(ok.log().contains("/token"), ok.log());
        assertFalse(ok.log().contains("demo-secret") || ok.log().contains("minted"), ok.log());
    }

    @Test
    void aNon2xxAnswerFailsEvenWithATokenInItsBody() {
        for (boolean confidential : new boolean[]{false, true}) {
            Exchange exchange = acquire(HttpResponse.json(400, "{\"access_token\":\"foo\"}"), confidential);
            assertNotNull(exchange.failure());
            assertEquals(List.of("/token"), exchange.paths());
        }
    }

    @Test
    void aBlankOrMissingAccessTokenFails() {
        for (String body : List.of("{\"access_token\":\" \"}", "{\"access_token\":42}", "{\"token_type\":\"Bearer\"}")) {
            assertNotNull(acquire(HttpResponse.json(body), false).failure(), body);
        }
    }


    @Test
    void aConfidentialFailureCarriesNoTextFromTheServer() {
        HttpResponse refused = HttpResponse.json(400, "{\"error\":\"invalid_client\",\"error_description\":\"SERVER-TEXT\"}");
        assertTrue(acquire(refused, false).failure().getMessage().contains("SERVER-TEXT"));
        OAuth2Exception e = acquire(refused, true).failure();
        assertFalse(e.getMessage().contains("SERVER-TEXT") || e.getMessage().contains("invalid_client"), e.getMessage());
        assertTrue(e.getMessage().contains("HTTP 400"), e.getMessage());
    }

    static OAuth2Exception unreachable(boolean confidential) {
        HttpServer server = HttpServer.start(0, request -> HttpResponse.json("{}"));
        int port = server.getPort();
        server.stop();
        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> auth = new HashMap<>();
            auth.put("type", "oauth2");
            auth.put("accessTokenUrl", "http://localhost:" + port + "/token");
            auth.put("clientId", "demo-client");
            auth.put("clientSecret", "demo-secret");
            HttpRequestBuilder http = new HttpRequestBuilder(client).url("http://localhost:" + port + "/test");
            OAuth2AuthHandler handler = (OAuth2AuthHandler) AuthHandlers.of(auth, http.getUrl(), null, confidential);
            return assertThrows(OAuth2Exception.class, () -> handler.getAccessToken(http));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void aTransportFailureIsReportedAsBeforeUnlessConfidential() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ClientCredentialsAuthHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ch.qos.logback.classic.Level level = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.WARN);
        try {
            OAuth2Exception plain = unreachable(false);
            assertTrue(plain.getMessage().startsWith("client credentials auth request failed: "), plain.getMessage());
            assertTrue(appender.list.stream().anyMatch(ev -> ev.getFormattedMessage().startsWith("client credentials auth request failed: ")));
            appender.list.clear();
            OAuth2Exception confidential = unreachable(true);
            assertNull(confidential.getCause());
            assertEquals("OAuth2 client_credentials token request failed: the request was not answered", confidential.getMessage());
            assertTrue(appender.list.isEmpty(), appender.list::toString);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(level);
        }
    }

}
