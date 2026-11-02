package io.karatelabs.core;

import io.karatelabs.http.HttpResponse;
import io.karatelabs.http.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code configure auth} with an OAuth2 grant fetches its token once and reuses it until it expires,
 * across the requests of a scenario and across parallel scenarios that configure the same auth.
 */
class ConfigureAuthOAuth2CacheTest {

    @TempDir
    Path tempDir;

    HttpServer server;
    final AtomicInteger fetches = new AtomicInteger();
    final Set<String> revoked = ConcurrentHashMap.newKeySet();
    volatile int expiresIn = 3600;
    volatile long tokenDelayMillis = 0;

    @BeforeEach
    void start() {
        server = HttpServer.start(0, request -> {
            if (request.pathMatches("/token")) {
                int n = fetches.incrementAndGet();
                if (tokenDelayMillis > 0) {
                    try {
                        Thread.sleep(tokenDelayMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return HttpResponse.json("{\"access_token\":\"t" + n + "\",\"expires_in\":" + expiresIn + "}");
            }
            String authorization = String.valueOf(request.getHeader("Authorization"));
            if (revoked.contains(authorization)) {
                return HttpResponse.text(401, "revoked");
            }
            return HttpResponse.text(200, authorization);
        });
    }

    @AfterEach
    void stop() {
        server.stopAsync();
    }

    String auth(String grant) {
        String common = "type: 'oauth2', accessTokenUrl: 'http://localhost:" + server.getPort() + "/token', clientId: 'app', clientSecret: 's3c'";
        return "password".equals(grant)
                ? "{ " + common + ", grantType: 'password', username: 'alice', password: 'pw' }"
                : "{ " + common + ", grantType: 'client_credentials' }";
    }

    SuiteResult run(int threads, String feature) throws Exception {
        Path file = tempDir.resolve("auth.feature");
        Files.writeString(file, feature.replace("BASE", "http://localhost:" + server.getPort()));
        return Runner.path(file.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputHtmlReport(false)
                .parallel(threads);
    }

    static void assertPassed(SuiteResult result) {
        assertEquals(0, result.getScenarioFailedCount(), String.valueOf(result.getErrors()));
    }

    @Test
    void manyRequestsFetchOneToken() throws Exception {
        for (String grant : new String[]{"client_credentials", "password"}) {
            fetches.set(0);
            SuiteResult result = run(1, """
                    Feature:
                    Scenario:
                    * url 'BASE'
                    * configure auth = AUTH
                    * path '/a'
                    * method get
                    * match response == 'Bearer t1'
                    * path '/b'
                    * method get
                    * match response == 'Bearer t1'
                    * path '/c'
                    * method get
                    * match response == 'Bearer t1'
                    """.replace("AUTH", auth(grant)));
            assertPassed(result);
            assertEquals(1, fetches.get(), grant);
        }
    }

    @Test
    void anExpiredTokenIsFetchedAgain() throws Exception {
        expiresIn = 1;
        SuiteResult result = run(1, """
                Feature:
                Scenario:
                * url 'BASE'
                * configure auth = AUTH
                * path '/a'
                * method get
                * match response == 'Bearer t1'
                * path '/a'
                * method get
                * match response == 'Bearer t1'
                * eval java.lang.Thread.sleep(1100)
                * path '/a'
                * method get
                * match response == 'Bearer t2'
                """.replace("AUTH", auth("client_credentials")));
        assertPassed(result);
        assertEquals(2, fetches.get());
    }

    @Test
    void parallelScenariosConfiguringTheSameAuthShareOneFetch() throws Exception {
        tokenDelayMillis = 300;
        StringBuilder feature = new StringBuilder("""
                Feature:
                Background:
                * url 'BASE'
                * configure auth = AUTH
                """.replace("AUTH", auth("client_credentials")));
        for (int i = 0; i < 8; i++) {
            feature.append("""
                    Scenario: s%d
                    * path '/a'
                    * method get
                    * match response == 'Bearer t1'
                    """.formatted(i));
        }
        SuiteResult result = run(8, feature.toString());
        assertPassed(result);
        assertEquals(8, result.getScenarioPassedCount());
        assertEquals(1, fetches.get());
    }

    @Test
    void aRelativeTokenUrlGetsEachServerItsOwnToken() throws Exception {
        java.util.List<String> otherSeen = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        HttpServer other = HttpServer.start(0, request -> {
            if (request.pathMatches("/token")) {
                return HttpResponse.json("{\"access_token\":\"other\",\"expires_in\":3600}");
            }
            String authorization = String.valueOf(request.getHeader("Authorization"));
            otherSeen.add(authorization);
            return HttpResponse.text(200, authorization);
        });
        try {
            SuiteResult result = run(1, """
                    Feature:
                    Scenario:
                    * configure auth = { type: 'oauth2', grantType: 'client_credentials', accessTokenUrl: '/token', clientId: 'app', clientSecret: 's3c' }
                    * url 'BASE'
                    * path '/a'
                    * method get
                    * match response == 'Bearer t1'
                    * url 'OTHER'
                    * path '/a'
                    * method get
                    * match response == 'Bearer other'
                    """.replace("OTHER", "http://localhost:" + other.getPort()));
            assertPassed(result);
            assertEquals(1, fetches.get());
            assertEquals(java.util.List.of("Bearer other"), otherSeen);
        } finally {
            other.stopAsync();
        }
    }

    @Test
    void anotherAuthConfigFetchesItsOwnToken() throws Exception {
        SuiteResult result = run(1, """
                Feature:
                Scenario:
                * url 'BASE'
                * configure auth = AUTH
                * path '/a'
                * method get
                * match response == 'Bearer t1'
                * configure auth = OTHER
                * path '/a'
                * method get
                * match response == 'Bearer t2'
                """.replace("AUTH", auth("client_credentials")).replace("OTHER", auth("password")));
        assertPassed(result);
        assertEquals(2, fetches.get());
    }

    @Test
    void aTokenTheServerRejectsIsFetchedAgainOnTheNextRequest() throws Exception {
        revoked.add("Bearer t1");
        SuiteResult result = run(1, """
                Feature:
                Scenario:
                * url 'BASE'
                * configure auth = AUTH
                * path '/a'
                * method get
                * status 401
                * path '/a'
                * method get
                * status 200
                * match response == 'Bearer t2'
                """.replace("AUTH", auth("client_credentials")));
        assertPassed(result);
        assertEquals(2, fetches.get());
    }

}
