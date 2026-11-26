package io.karatelabs.http;

import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.output.LogContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AuthHandlers#of}: the handler an {@code auth} map configures, and each credential it can send, handed to the
 * caller before a request or a log line can carry it.
 */
class AuthHandlersTest {

    HttpServer server;
    final List<String> events = Collections.synchronizedList(new ArrayList<>());

    @AfterEach
    void afterEach() {
        if (server != null) {
            server.stop();
        }
        LogContext.get().setHttpLogFilter(null);
    }

    static Map<String, Object> auth(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void eachSupportedTypeBuildsItsHandler() {
        String url = "http://localhost:8080/api";
        assertInstanceOf(BasicAuthHandler.class, AuthHandlers.of(auth("type", "basic", "username", "u", "password", "p"), url, null));
        assertInstanceOf(BearerAuthHandler.class, AuthHandlers.of(auth("type", "bearer", "token", "t"), url, null));
        assertInstanceOf(DigestAuthHandler.class, AuthHandlers.of(auth("type", "digest", "username", "u", "password", "p"), url, null));
        NtlmAuthHandler ntlm = (NtlmAuthHandler) AuthHandlers.of(auth("type", "ntlm", "username", "u", "password", "p",
                "domain", "d", "workstation", "w"), url, null);
        assertEquals(List.of("u", "p", "d", "w"), List.of(ntlm.getUsername(), ntlm.getPassword(), ntlm.getDomain(), ntlm.getWorkstation()));
        assertInstanceOf(ClientCredentialsAuthHandler.class, AuthHandlers.of(auth("type", "oauth2",
                "accessTokenUrl", "/token", "clientId", "c", "clientSecret", "s"), url, null));
        assertInstanceOf(PasswordAuthHandler.class, AuthHandlers.of(auth("type", "oauth2", "grantType", "password",
                "accessTokenUrl", "/token", "clientId", "c", "clientSecret", "s", "username", "u", "password", "p"), url, null));
        assertInstanceOf(AwsSigV4AuthHandler.class, AuthHandlers.of(auth("type", "aws", "accessKey", "a", "secretKey", "s",
                "region", "us-east-1", "service", "execute-api"), url, null));
        assertInstanceOf(JwtAuthHandler.class, AuthHandlers.of(auth("type", "jwt", "algorithm", "HS256", "secret", "s"), url, null));
        assertInstanceOf(OAuth1AuthHandler.class, AuthHandlers.of(auth("type", "oauth1", "consumerKey", "k", "consumerSecret", "s"), url, null));
        assertInstanceOf(HawkAuthHandler.class, AuthHandlers.of(auth("type", "hawk", "authId", "i", "authKey", "k", "algorithm", "sha256"), url, null));
        for (String type : List.of("basic", "bearer", "digest", "ntlm", "oauth2", "aws", "jwt", "oauth1", "hawk")) {
            assertTrue(AuthHandlers.supports(type), type);
        }
    }

    @Test
    void anUnknownOrAbsentTypeBuildsNone() {
        assertNull(AuthHandlers.of(auth("type", "apikey", "key", "k"), "http://localhost", null));
        assertFalse(AuthHandlers.supports("apikey"));
        assertFalse(AuthHandlers.supports(null));
        assertNull(AuthHandlers.of(auth("token", "t"), "http://localhost", null));
        assertNull(AuthHandlers.of(Map.of(), "http://localhost", null));
        assertNull(AuthHandlers.of(null, "http://localhost", null));
    }

    @Test
    void aRelativeTokenUrlResolvesAgainstTheRequestUrl() {
        Map<String, Object> settings = AuthHandlers.oauth2Settings(auth("type", "oauth2", "accessTokenUrl", "/token",
                "clientId", "c", "clientSecret", "s"), "https://api.example.com:8443/v1/things");
        assertEquals("https://api.example.com:8443/token", settings.get("url"));
        assertEquals("client_credentials", settings.get("grant_type"));
    }

    @Test
    void aValueKnownAtConstructionIsHandedOverBeforeTheFactoryReturns() {
        List<String> seen = new ArrayList<>();
        AuthHandlers.of(auth("type", "basic", "username", "alice", "password", "pw"), "http://localhost", seen::add);
        String encoded = Base64.getEncoder().encodeToString("alice:pw".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("alice", "pw", encoded), seen);
        seen.clear();
        AuthHandlers.of(auth("type", "bearer", "token", "tok"), "http://localhost", seen::add);
        AuthHandlers.of(auth("type", "digest", "username", "u", "password", "dpw"), "http://localhost", seen::add);
        AuthHandlers.of(auth("type", "ntlm", "username", "u", "password", "npw"), "http://localhost", seen::add);
        AuthHandlers.of(auth("type", "jwt", "algorithm", "HS256", "secret", "jsecret"), "http://localhost", seen::add);
        AuthHandlers.of(auth("type", "hawk", "authId", "i", "authKey", "hkey", "algorithm", "sha256"), "http://localhost", seen::add);
        AuthHandlers.of(auth("type", "aws", "accessKey", "a", "secretKey", "asecret", "sessionToken", "atoken",
                "region", "us-east-1", "service", "s3"), "http://localhost", seen::add);
        AuthHandlers.of(auth("type", "oauth1", "consumerKey", "k", "consumerSecret", "csecret", "token", "otoken",
                "tokenSecret", "tsecret"), "http://localhost", seen::add);
        assertEquals(List.of("tok", "dpw", "npw", "jsecret", "hkey", "asecret", "atoken", "csecret", "otoken", "tsecret"), seen);
    }

    @Test
    void oauth2ClientCredentialsAreHandedOverInEveryFormTheyAreSent() {
        List<String> seen = new ArrayList<>();
        AuthHandlers.of(auth("type", "oauth2", "accessTokenUrl", "http://localhost/token", "clientId", "my app",
                "clientSecret", "s3c:r&t", "clientAuthentication", "header"), "http://localhost", seen::add);
        String basic = Base64.getEncoder().encodeToString("my+app:s3c%3Ar%26t".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("s3c:r&t", "s3c%3Ar%26t", basic), seen);
    }

    @Test
    void credentialsReachTheCallerBeforeTheFirstTransportLogLine() {
        server = HttpServer.start(0, request -> HttpResponse.json("{}"));
        LogContext.get().setHttpLogFilter(line -> {
            events.add("log");
            return line;
        });
        AuthHandler handler = AuthHandlers.of(auth("type", "basic", "username", "alice", "password", "pw"),
                "http://localhost:" + server.getPort(), value -> events.add("credential"));
        try (Http http = Http.to("http://localhost:" + server.getPort())) {
            http.auth(handler).get();
        }
        assertEquals(List.of("credential", "credential", "credential"), events.subList(0, 3));
        assertTrue(events.contains("log"), events::toString);
    }

    @Test
    void anOAuth2TokenIsHandedOverBeforeTheRequestThatCarriesIt() {
        server = HttpServer.start(0, request -> {
            if (request.pathMatches("/token")) {
                return HttpResponse.json("{\"access_token\":\"minted-1\",\"refresh_token\":\"refresh-1\"}");
            }
            events.add("request " + request.getHeader("Authorization"));
            return HttpResponse.json("{}");
        });
        String base = "http://localhost:" + server.getPort();
        AuthHandler handler = AuthHandlers.of(auth("type", "oauth2", "accessTokenUrl", "/token", "clientId", "c",
                "clientSecret", "cs"), base, events::add);
        try (Http http = Http.to(base + "/api")) {
            http.auth(handler).get();
        }
        assertEquals(List.of("cs", "minted-1", "refresh-1", "request Bearer minted-1"), events);
    }

    @Test
    void aSignedJwtIsHandedOverBeforeItsRequest() {
        server = HttpServer.start(0, request -> {
            events.add("request " + request.getHeader("Authorization"));
            return HttpResponse.json("{}");
        });
        AuthHandler handler = AuthHandlers.of(auth("type", "jwt", "algorithm", "HS256", "secret", "jsecret",
                "payload", Map.of("sub", "alice")), "http://localhost", events::add);
        try (Http http = Http.to("http://localhost:" + server.getPort())) {
            http.auth(handler).get();
        }
        assertEquals(3, events.size(), events::toString);
        assertEquals("jsecret", events.get(0));
        assertEquals("request Bearer " + events.get(1), events.get(2));
    }

    @Test
    void aRequestSignatureIsHandedOverBeforeItsRequest() {
        server = HttpServer.start(0, request -> {
            events.add("request " + request.getHeader("Authorization"));
            return HttpResponse.json("{}");
        });
        AuthHandler handler = AuthHandlers.of(auth("type", "hawk", "authId", "id", "authKey", "hkey", "algorithm", "sha256"),
                "http://localhost", events::add);
        try (Http http = Http.to("http://localhost:" + server.getPort())) {
            http.auth(handler).get();
        }
        assertEquals(3, events.size(), events::toString);
        assertTrue(events.get(2).contains("mac=\"" + events.get(1) + "\""), events::toString);
    }

    @Test
    void aFeatureWhoseAuthNamesAnUnknownTypeSendsItsRequestUnsigned() {
        server = HttpServer.start(0, request -> {
            events.add(String.valueOf(request.getHeader("Authorization")));
            return HttpResponse.json("{}");
        });
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
                Feature: unknown auth

                Scenario: sent unsigned
                * configure auth = { type: 'apikey', key: 'k' }
                * url 'http://localhost:%d'
                * method get
                * status 200
                """.formatted(server.getPort()));
        assertPassed(sr);
        assertEquals(List.of("null"), events);
    }


    @Test
    void anOAuth1TokenIsHandedOverInTheFormItsHeaderCarries() {
        server = HttpServer.start(0, request -> {
            events.add("request " + request.getHeader("Authorization"));
            return HttpResponse.json("{}");
        });
        for (boolean raw : new boolean[]{false, true}) {
            events.clear();
            AuthHandler handler = AuthHandlers.of(auth("type", "oauth1", "consumerKey", "k", "consumerSecret", "cs",
                    "token", " a+b ", "disableHeaderEncoding", raw), "http://localhost", events::add);
            try (Http http = Http.to("http://localhost:" + server.getPort())) {
                http.auth(handler).get();
            }
            String header = events.getLast();
            java.util.regex.Matcher token = java.util.regex.Pattern.compile("oauth_token=\"([^\"]*)\"").matcher(header);
            java.util.regex.Matcher signature = java.util.regex.Pattern.compile("oauth_signature=\"([^\"]*)\"").matcher(header);
            assertTrue(token.find() && signature.find(), header);
            assertEquals(raw ? "a+b" : "a%2Bb", token.group(1));
            assertTrue(events.indexOf(token.group(1)) >= 0 && events.indexOf(token.group(1)) < events.size() - 1, events::toString);
            assertTrue(events.indexOf(signature.group(1)) >= 0, events::toString);
        }
    }

    @Test
    void aMintedRefreshTokenIsHandedOverInItsFormEncodedForm() {
        server = HttpServer.start(0, request -> HttpResponse.json("{\"access_token\":\"t1\",\"refresh_token\":\"r+1/=\"}"));
        String base = "http://localhost:" + server.getPort();
        AuthHandler handler = AuthHandlers.of(auth("type", "oauth2", "grantType", "password", "accessTokenUrl", "/token",
                "clientId", "c", "clientSecret", "cs", "username", "u", "password", "p"), base, events::add);
        try (Http http = Http.to(base + "/api")) {
            http.auth(handler).get();
        }
        assertTrue(events.contains("r+1/=") && events.contains("r%2B1%2F%3D"), events::toString);
    }

}
