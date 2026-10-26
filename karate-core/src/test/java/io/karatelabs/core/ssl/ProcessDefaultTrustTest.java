package io.karatelabs.core.ssl;

import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.http.ApacheHttpClient;
import io.karatelabs.http.HttpResponse;
import io.karatelabs.http.HttpServer;
import io.karatelabs.test.LogSilencer;
import io.netty.handler.ssl.SslContextBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

import static io.karatelabs.core.TestUtils.*;

/**
 * Both servers' certificates are signed by a test CA that only an in-test SSLContext.setDefault()
 * trusts, so a pass proves the client verified against the process default and nothing else. The
 * second certificate names other.test, not localhost.
 */
class ProcessDefaultTrustTest {

    static final char[] PASSWORD = "karate-test".toCharArray();

    static HttpServer server;
    static HttpServer otherHost;
    static SSLContext caOnly;
    static SSLContext original;

    @BeforeAll
    static void beforeAll() throws Exception {
        server = start("ssl/test-ca-server.p12");
        otherHost = start("ssl/test-ca-other-host.p12");
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(load("ssl/test-ca-truststore.p12"));
        caOnly = SSLContext.getInstance("TLS");
        caOnly.init(null, tmf.getTrustManagers(), null);
        original = SSLContext.getDefault();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
        otherHost.stop();
    }

    static HttpServer start(String keyStore) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(load(keyStore), PASSWORD);
        return HttpServer.start(0, SslContextBuilder.forServer(kmf).build(),
                request -> HttpResponse.json("{ \"ok\": true }"));
    }

    @AfterEach
    void restoreDefault() {
        SSLContext.setDefault(original);
    }

    static KeyStore load(String path) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = ProcessDefaultTrustTest.class.getClassLoader().getResourceAsStream(path)) {
            ks.load(is, PASSWORD);
        }
        return ks;
    }

    static ScenarioRuntime get(String sslConfig) {
        return get(sslConfig, server);
    }

    static ScenarioRuntime get(String sslConfig, HttpServer target) {
        return LogSilencer.silenced("karate.http", () -> runFeature(new ApacheHttpClient(), """
                Feature: process default trust

                Scenario: get over https
                %s
                * url 'https://localhost:%d'
                * method get
                * status 200
                """.formatted(sslConfig, target.getPort())));
    }

    @Test
    void testVerifyingClientTrustsTheProcessDefault() {
        SSLContext.setDefault(caOnly);
        assertPassed(get("* configure ssl = { trustAll: false }"));
    }

    @Test
    void testVerifyingClientFailsWithoutTheCa() {
        assertFailedWith(get("* configure ssl = { trustAll: false }"), "PKIX path building failed");
    }

    @Test
    void testVerifyingClientChecksTheHostname() {
        SSLContext.setDefault(caOnly);
        assertFailedWith(get("* configure ssl = { trustAll: false }", otherHost),
                "doesn't match any of the subject alternative names: [other.test]");
    }

    @Test
    void testNoSslConfigTrustsAll() {
        assertPassed(get(""));
        assertPassed(get("", otherHost));
    }

}
