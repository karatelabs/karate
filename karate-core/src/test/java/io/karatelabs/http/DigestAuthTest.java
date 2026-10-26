package io.karatelabs.http;

import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.http.ApacheHttpClientProxySslE2eTest.FakeProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code configure auth = { type: 'digest' }} against a server that issues a real Digest challenge
 * and recomputes the response hash (RFC 7616, qop=auth) before answering 200.
 */
class DigestAuthTest {

    static final String REALM = "karate";
    static final String NONCE = "dcd98b7102dd2f0e8b11d0f600bfb0c093";
    static final String OPAQUE = "5ccc069c403ebaf9f0171e9517f40e41";
    static final Pattern PARAM = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]*))");

    HttpServer server;
    HttpServer redirector;
    final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());

    @AfterEach
    void afterEach() {
        if (server != null) {
            server.stop();
        }
        if (redirector != null) {
            redirector.stop();
        }
    }

    /**
     * @param algorithm the Digest algorithm to challenge with, or null to send a Basic challenge
     */
    int startServer(String algorithm) {
        server = HttpServer.start(0, request -> {
            String auth = request.getHeader("Authorization");
            if (auth != null) {
                authorizations.add(auth);
            }
            if (algorithm != null && auth != null && auth.startsWith("Digest ")
                    && verify(request.getMethod(), request.getPathRaw(), auth.substring(7), algorithm)) {
                return HttpResponse.json("{ \"ok\": true }");
            }
            HttpResponse response = new HttpResponse();
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", algorithm == null
                    ? "Basic realm=\"" + REALM + "\""
                    : "Digest realm=\"" + REALM + "\", qop=\"auth\", algorithm=" + algorithm
                    + ", nonce=\"" + NONCE + "\", opaque=\"" + OPAQUE + "\"");
            return response;
        });
        return server.getPort();
    }

    static boolean verify(String method, String path, String header, String algorithm) {
        Map<String, String> p = new HashMap<>();
        Matcher m = PARAM.matcher(header);
        while (m.find()) {
            p.put(m.group(1), m.group(2) != null ? m.group(2) : m.group(3));
        }
        if (!"alice".equals(p.get("username")) || !REALM.equals(p.get("realm"))
                || !NONCE.equals(p.get("nonce")) || !OPAQUE.equals(p.get("opaque"))
                || !path.equals(p.get("uri")) || !"auth".equals(p.get("qop"))
                || !algorithm.equalsIgnoreCase(p.get("algorithm"))) {
            return false;
        }
        String ha1 = hash(algorithm, "alice:" + REALM + ":s3cret");
        String ha2 = hash(algorithm, method + ":" + p.get("uri"));
        String expected = hash(algorithm, ha1 + ":" + NONCE + ":" + p.get("nc") + ":" + p.get("cnonce") + ":auth:" + ha2);
        return expected.equals(p.get("response"));
    }

    static String hash(String algorithm, String value) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    ScenarioRuntime run(int port, String password, String status) {
        return runFeature(new ApacheHttpClient(), """
                Feature: digest auth

                Scenario: challenge and retry
                * configure auth = { type: 'digest', username: 'alice', password: '%s' }
                * url 'http://localhost:%d'
                * path 'secure'
                * param q = 'one two'
                * method get
                * status %s
                """.formatted(password, port, status));
    }

    @Test
    void testMd5ChallengeAnswered() {
        ScenarioRuntime sr = run(startServer("MD5"), "s3cret", "200\n* match response == { ok: true }");
        assertPassed(sr);
        assertEquals(1, authorizations.size(), "one challenge, one answer: " + authorizations);
        assertTrue(authorizations.get(0).contains("qop=auth"), authorizations.get(0));
    }

    @Test
    void testSha256ChallengeAnswered() {
        ScenarioRuntime sr = run(startServer("SHA-256"), "s3cret", "200");
        assertPassed(sr);
        assertTrue(authorizations.get(0).contains("algorithm=SHA-256"), authorizations.get(0));
    }

    @Test
    void testWrongPasswordSurfacesThe401() {
        ScenarioRuntime sr = run(startServer("MD5"), "wrong", "401");
        assertPassed(sr);
        assertEquals(1, authorizations.size(), "one failed answer, no retry loop: " + authorizations);
        assertTrue(authorizations.get(0).startsWith("Digest "), authorizations.get(0));
    }

    @Test
    void testBasicChallengeGetsNoPassword() {
        ScenarioRuntime sr = run(startServer(null), "s3cret", "401");
        assertPassed(sr);
        assertEquals(List.of(), authorizations);
    }

    @Test
    void testProxyBasicChallengeGetsNoPassword() throws Exception {
        try (FakeProxy proxy = new FakeProxy()) {
            proxy.requireProxyAuth = true;
            ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
                    Feature: digest auth behind a proxy

                    Scenario: the proxy's Basic challenge is not answered with the digest account
                    * configure proxy = 'http://127.0.0.1:%d'
                    * configure auth = { type: 'digest', username: 'alice', password: 's3cret' }
                    * url 'http://example.test'
                    * path 'secure'
                    * method get
                    * status 407
                    """.formatted(proxy.port()));
            assertPassed(sr);
            assertEquals(List.of(), proxy.proxyAuthHeaders);
        }
    }

    @Test
    void testRedirectedHostGetsNoPassword() {
        int port = startServer("MD5");
        redirector = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            response.setStatus(302);
            response.setHeader("Location", "http://127.0.0.1:" + port + "/secure");
            return response;
        });
        ScenarioRuntime sr = run(redirector.getPort(), "s3cret", "401");
        assertPassed(sr);
        assertEquals(List.of(), authorizations);
    }

    @Test
    void testCurlExport() {
        DigestAuthHandler handler = new DigestAuthHandler("alice", "s3cret");
        assertEquals("digest", handler.getType());
        assertEquals("--digest -u 'alice:s3cret'", handler.toCurlArgument("sh"));
    }

}
