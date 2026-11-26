package io.karatelabs.http;

import io.karatelabs.output.LogContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Http#auth}: a handler signs the next request only, and that request's Host is the one the client writes
 * from the URI.
 */
class HttpAuthTest {

    HttpServer server;
    final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());
    final List<String> hosts = Collections.synchronizedList(new ArrayList<>());

    @AfterEach
    void afterEach() {
        if (server != null) {
            server.stop();
        }
        LogContext.get().setHttpLogFilter(null);
    }

    String echo() {
        server = HttpServer.start(0, request -> {
            authorizations.add(String.valueOf(request.getHeader("Authorization")));
            hosts.add(request.getHeader("Host"));
            return HttpResponse.json("{}");
        });
        return "http://localhost:" + server.getPort();
    }

    @Test
    void aBearerHandlerSignsTheNextRequestOnly() {
        String base = echo();
        try (Http http = Http.to(base)) {
            http.auth(new BearerAuthHandler("tok")).get();
            http.get();
        }
        assertEquals(List.of("Bearer tok", "null"), authorizations);
    }

    @Test
    void theNextRequestsUriIsKnownBeforeItIsSent() {
        try (Http http = Http.to("http://Example.com:8080/api/")) {
            http.path("users", "a b").param("q", "1");
            assertEquals("http://Example.com:8080/api/users/a%20b?q=1", http.getUri());
        }
    }

    @Test
    void aSignedRequestSendsTheHostOfItsUri() {
        String base = echo();
        try (Http http = Http.to(base)) {
            http.auth(new BearerAuthHandler("tok")).header("Host", "other.example").get();
            http.header("Host", "other.example").get();
        }
        assertEquals(List.of("localhost:" + server.getPort(), "other.example"), hosts);
    }

    @Test
    void aHandlerIsDroppedWhenItsRequestThrows() {
        String base = echo();
        Map<String, Object> config = new HashMap<>();
        config.put("url", base + "/token");
        config.put("client_id", "c");
        config.put("client_secret", "s");
        AuthHandler failing = new ClientCredentialsAuthHandler(config) {
            @Override
            public OAuth2Token getToken(HttpRequestBuilder builder) {
                throw new OAuth2Exception("no token");
            }
        };
        try (Http http = Http.to(base)) {
            assertThrows(OAuth2Exception.class, () -> http.auth(failing).get());
            http.get();
        }
        assertEquals(List.of("null"), authorizations);
    }

    @Test
    void aDigestHandlerAnswersOneChallengeAndTheNextRequestCarriesNoCredentials() {
        server = HttpServer.start(0, request -> {
            String auth = request.getHeader("Authorization");
            authorizations.add(String.valueOf(auth));
            if (auth != null && auth.startsWith("Digest ")
                    && DigestAuthTest.verify(request.getMethod(), request.getPathRaw(), auth.substring(7), "MD5")) {
                return HttpResponse.json("{}");
            }
            HttpResponse response = new HttpResponse();
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", "Digest realm=\"" + DigestAuthTest.REALM + "\", qop=\"auth\", algorithm=MD5"
                    + ", nonce=\"" + DigestAuthTest.NONCE + "\", opaque=\"" + DigestAuthTest.OPAQUE + "\"");
            return response;
        });
        try (Http http = Http.to("http://localhost:" + server.getPort() + "/secure").quiet()) {
            assertEquals(200, http.auth(new DigestAuthHandler("alice", "s3cret")).get().getStatus());
            assertEquals(401, http.get().getStatus());
        }
        assertEquals(3, authorizations.size(), authorizations::toString);
        assertEquals("null", authorizations.get(0));
        assertTrue(authorizations.get(1).startsWith("Digest "), authorizations.get(1));
        assertEquals("null", authorizations.get(2));
    }

    @Test
    void anNtlmHandlerAnswersOneHandshakeAndTheNextRequestCarriesNoCredentials() {
        server = HttpServer.start(0, request -> {
            String auth = request.getHeader("Authorization");
            authorizations.add(String.valueOf(auth));
            if (auth != null && auth.startsWith("NTLM TlRMTVNTUAAD")) {
                return HttpResponse.json("{}");
            }
            HttpResponse response = new HttpResponse();
            response.setStatus(401);
            if (auth != null && auth.startsWith("NTLM TlRMTVNTUAAB")) {
                response.setHeader("WWW-Authenticate", "NTLM " + NtlmAuthTest.type2());
            } else {
                response.setHeader("WWW-Authenticate", "NTLM");
            }
            return response;
        });
        try (Http http = Http.to("http://localhost:" + server.getPort()).quiet()) {
            assertEquals(200, http.auth(new NtlmAuthHandler("alice", "s3cret", "corp", "ws")).get().getStatus());
            assertEquals(401, http.get().getStatus());
        }
        assertEquals(4, authorizations.size(), authorizations::toString);
        assertTrue(authorizations.get(1).startsWith("NTLM TlRMTVNTUAAB"), authorizations.get(1));
        assertTrue(authorizations.get(2).startsWith("NTLM TlRMTVNTUAAD"), authorizations.get(2));
        assertEquals("null", authorizations.get(3));
    }

    @Test
    void closeReleasesTheClient() throws Exception {
        String base = echo();
        Http http = Http.to(base);
        http.get();
        http.close();
        Field client = Http.class.getDeclaredField("client");
        client.setAccessible(true);
        Field released = ApacheHttpClient.class.getDeclaredField("released");
        released.setAccessible(true);
        assertTrue((boolean) released.get(client.get(http)));
        Field httpClient = ApacheHttpClient.class.getDeclaredField("httpClient");
        httpClient.setAccessible(true);
        assertNull(httpClient.get(client.get(http)));
    }

    @Test
    void anOAuth2TokenIsReusedUntilA401RejectsIt() {
        AtomicInteger fetches = new AtomicInteger();
        server = HttpServer.start(0, request -> {
            if (request.pathMatches("/token")) {
                return HttpResponse.json("{\"access_token\":\"t" + fetches.incrementAndGet() + "\"}");
            }
            String auth = request.getHeader("Authorization");
            authorizations.add(auth);
            return "Bearer t1".equals(auth) && authorizations.size() == 2 ? HttpResponse.json(401, "{}") : HttpResponse.json("{}");
        });
        String base = "http://localhost:" + server.getPort();
        AuthHandler handler = AuthHandlers.of(Map.of("type", "oauth2", "accessTokenUrl", "/token", "clientId", "c",
                "clientSecret", "s"), base, null);
        for (int i = 0; i < 3; i++) {
            try (Http http = Http.to(base + "/api").quiet()) {
                http.auth(handler).get();
            }
        }
        assertEquals(List.of("Bearer t1", "Bearer t1", "Bearer t2"), authorizations);
        assertEquals(2, fetches.get());
    }

    @Test
    void aTokenFailureSendsNothing() {
        server = HttpServer.start(0, request -> {
            if (request.pathMatches("/token")) {
                return HttpResponse.json(401, "{\"access_token\":\"leaked\"}");
            }
            authorizations.add(String.valueOf(request.getHeader("Authorization")));
            return HttpResponse.json("{}");
        });
        String base = "http://localhost:" + server.getPort();
        AuthHandler handler = AuthHandlers.of(Map.of("type", "oauth2", "accessTokenUrl", "/token", "clientId", "c",
                "clientSecret", "s"), base, null, true);
        try (Http http = Http.to(base + "/api")) {
            OAuth2Exception e = assertThrows(OAuth2Exception.class, () -> http.auth(handler).get());
            assertFalse(e.getMessage().contains("leaked"), e.getMessage());
        }
        assertEquals(List.of(), authorizations);
    }

    @Test
    void aConfidentialTokenExchangeIsLoggedWithoutHeadersOrBody() {
        server = HttpServer.start(0, request -> request.pathMatches("/token")
                ? HttpResponse.json("{\"access_token\":\"minted-token\"}") : HttpResponse.json("{}"));
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        LogContext.get().setHttpLogFilter(line -> {
            lines.add(line);
            return line;
        });
        String base = "http://localhost:" + server.getPort();
        AuthHandler handler = AuthHandlers.of(Map.of("type", "oauth2", "accessTokenUrl", "/token", "clientId", "c",
                "clientSecret", "client-secret", "clientAuthentication", "header"), base, null, true);
        try (Http http = Http.to(base + "/api")) {
            http.auth(handler).get();
        }
        List<String> token = lines.stream().filter(l -> l.contains("/token")).toList();
        assertFalse(token.isEmpty(), lines::toString);
        for (String line : token) {
            assertFalse(line.contains("minted-token") || line.contains("client-secret") || line.contains("Basic "), line);
        }
    }

    @Test
    void theFacadesErrorLinePassesTheFilter() {
        server = HttpServer.start(0, request -> HttpResponse.json(500, "{\"echo\":\"secret-value\"}"));
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        LogContext.get().setHttpLogFilter(line -> {
            lines.add(line);
            return line.replace("secret-value", "***");
        });
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(Http.class);
        ch.qos.logback.classic.Level level = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.WARN);
        try (Http http = Http.to("http://localhost:" + server.getPort())) {
            http.get();
        } finally {
            logger.setLevel(level);
        }
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("http response code: 500") && l.contains("secret-value")), lines::toString);
    }


    @Test
    void aRequestThatFailsToBuildLeavesNoAuthForTheNext() {
        String base = echo();
        try (Http http = Http.to("http://bad host/")) {
            http.auth(new BearerAuthHandler("tok")).path("x");
            assertThrows(RuntimeException.class, http::get);
            http.url(base).get();
        }
        assertEquals(List.of("null"), authorizations);
    }

    @Test
    void anArmedChallengeHandlerWinsForItsRequestAndTheConfiguredAuthIsKept() {
        server = HttpServer.start(0, request -> {
            String auth = request.getHeader("Authorization");
            authorizations.add(String.valueOf(auth));
            if (auth != null && auth.startsWith("Digest ")
                    && DigestAuthTest.verify(request.getMethod(), request.getPathRaw(), auth.substring(7), "MD5")) {
                return HttpResponse.json("{}");
            }
            HttpResponse response = new HttpResponse();
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", "Digest realm=\"" + DigestAuthTest.REALM + "\", qop=\"auth\", algorithm=MD5"
                    + ", nonce=\"" + DigestAuthTest.NONCE + "\", opaque=\"" + DigestAuthTest.OPAQUE + "\"");
            return response;
        });
        try (Http http = Http.to("http://localhost:" + server.getPort() + "/secure").quiet()) {
            http.auth(new DigestAuthHandler("alice", "s3cret"));
            http.configure("auth", Map.of("type", "digest", "username", "alice", "password", "wrong"));
            assertEquals(200, http.get().getStatus());
            int before = authorizations.size();
            assertEquals(401, http.get().getStatus());
            assertTrue(authorizations.subList(before, authorizations.size()).stream().anyMatch(a -> a.startsWith("Digest ")),
                    authorizations::toString);
        }
    }

}
