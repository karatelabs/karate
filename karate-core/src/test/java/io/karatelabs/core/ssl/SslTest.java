/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.core.ssl;

import io.karatelabs.core.MockServer;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SSL/TLS tests for Karate HTTP client.
 * Tests trustStore, keyStore, and mTLS (mutual TLS) configurations.
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Self-signed certificate generation not supported on Windows")
class SslTest {

    static MockServer mockServer;
    static MockServer httpServer; // plain HTTP (no SSL)
    static MockServer mtlsServer;
    static File clientKeyStoreFile;
    static File clientTrustStoreFile;

    @BeforeAll
    @SuppressWarnings("deprecation")
    static void beforeAll() throws Exception {
        // Start plain HTTP mock server (no SSL)
        httpServer = MockServer.featureString(
                "Feature: HTTP Mock\n" +
                "Scenario: pathMatches('/test')\n" +
                "* def response = { success: true }\n"
        ).start();

        // Start mock server with SSL using self-signed certificate (no client auth)
        mockServer = MockServer.featureString(
                "Feature: SSL Mock\n" +
                "Scenario: pathMatches('/test')\n" +
                "* def response = { success: true }\n"
        ).ssl(true).start();

        // Generate server and client certificates for mTLS
        SelfSignedCertificate serverCert = SelfSignedCertificate.builder().fqdn("localhost").build();
        SelfSignedCertificate clientCert = SelfSignedCertificate.builder().fqdn("client").build();

        // Create server SSL context requiring client certificate
        SslContext mtlsSslContext = SslContextBuilder
                .forServer(serverCert.certificate(), serverCert.privateKey())
                .clientAuth(ClientAuth.REQUIRE)
                .trustManager(clientCert.certificate())
                .build();

        // Start mTLS mock server
        mtlsServer = MockServer.featureString(
                "Feature: mTLS Mock\n" +
                "Scenario: pathMatches('/test')\n" +
                "* def response = { success: true }\n"
        ).sslContext(mtlsSslContext).start();

        // Create PKCS12 keystore with client cert + private key (for ApacheHttpClient)
        clientKeyStoreFile = File.createTempFile("client-keystore", ".p12");
        clientKeyStoreFile.deleteOnExit();
        createPkcs12KeyStore(clientCert.certificate(), clientCert.privateKey(), clientKeyStoreFile, "test123");

        // Create PKCS12 truststore with server cert (for client to trust server)
        clientTrustStoreFile = File.createTempFile("client-truststore", ".p12");
        clientTrustStoreFile.deleteOnExit();
        createPkcs12TrustStore(serverCert.certificate(), clientTrustStoreFile, "test123");
    }

    @Test
    void testSslTrustAll() {
        int port = mockServer.getPort();
        SuiteResult results = Runner.path("classpath:ssl/ssl-trust-all.feature")
                .systemProperty("ssl.port", port + "")
                .parallel(1);
        assertEquals(0, results.getScenarioFailedCount(), String.join("\n", results.getErrors()));
    }

    @Test
    void testSslTrustStore() {
        int port = mockServer.getPort();
        SuiteResult results = Runner.path("classpath:ssl/ssl-truststore.feature")
                .systemProperty("ssl.port", port + "")
                .parallel(1);
        assertEquals(0, results.getScenarioFailedCount(), String.join("\n", results.getErrors()));
    }

    @Test
    void testSslKeyStore() {
        int port = mockServer.getPort();
        SuiteResult results = Runner.path("classpath:ssl/ssl-keystore.feature")
                .systemProperty("ssl.port", port + "")
                .parallel(1);
        assertEquals(0, results.getScenarioFailedCount(), String.join("\n", results.getErrors()));
    }

    @Test
    void testSslMtls() {
        int port = mtlsServer.getPort();
        SuiteResult results = Runner.path("classpath:ssl/ssl-mtls.feature")
                .systemProperty("mtls.port", port + "")
                .systemProperty("mtls.clientKeyStore", clientKeyStoreFile.getAbsolutePath())
                .systemProperty("mtls.clientTrustStore", clientTrustStoreFile.getAbsolutePath())
                .parallel(1);
        assertEquals(0, results.getScenarioFailedCount(), String.join("\n", results.getErrors()));
    }

    @Test
    void testSslMtlsNoCertFails() {
        // Connecting to an mTLS server WITHOUT a client certificate should fail
        int port = mtlsServer.getPort();
        SuiteResult results = Runner.path("classpath:ssl/ssl-mtls-no-cert.feature")
                .systemProperty("mtls.port", port + "")
                .parallel(1);
        assertEquals(1, results.getScenarioFailedCount(),
                "Expected failure when connecting to mTLS server without client certificate");
    }

    @Test
    void testSslMtlsOverride() {
        // Test that a second configure ssl overrides the first (simulates karate-config.js + Background)
        int port = mtlsServer.getPort();
        SuiteResult results = Runner.path("classpath:ssl/ssl-mtls-override.feature")
                .systemProperty("mtls.port", port + "")
                .systemProperty("mtls.clientKeyStore", clientKeyStoreFile.getAbsolutePath())
                .systemProperty("mtls.clientTrustStore", clientTrustStoreFile.getAbsolutePath())
                .parallel(1);
        assertEquals(0, results.getScenarioFailedCount(), String.join("\n", results.getErrors()));
    }

    @Test
    void testSslMtlsJsConfigure() {
        // Test karate.configure('ssl', {...}) from JavaScript (karate-config.js pattern)
        int port = mtlsServer.getPort();
        SuiteResult results = Runner.path("classpath:ssl/ssl-mtls-js-configure.feature")
                .systemProperty("mtls.port", port + "")
                .systemProperty("mtls.clientKeyStore", clientKeyStoreFile.getAbsolutePath())
                .systemProperty("mtls.clientTrustStore", clientTrustStoreFile.getAbsolutePath())
                .parallel(1);
        assertEquals(0, results.getScenarioFailedCount(), String.join("\n", results.getErrors()));
    }

    @Test
    void testSslMtlsMidFlow() {
        // Start with non-SSL request, then configure SSL mid-flow - confirms HTTP client rebuild
        SuiteResult results = Runner.path("classpath:ssl/ssl-mtls-mid-flow.feature")
                .systemProperty("http.port", httpServer.getPort() + "")
                .systemProperty("mtls.port", mtlsServer.getPort() + "")
                .systemProperty("mtls.clientKeyStore", clientKeyStoreFile.getAbsolutePath())
                .systemProperty("mtls.clientTrustStore", clientTrustStoreFile.getAbsolutePath())
                .parallel(1);
        assertEquals(0, results.getScenarioFailedCount(), String.join("\n", results.getErrors()));
    }

    @AfterAll
    static void afterAll() {
        if (httpServer != null) {
            httpServer.stopAndWait();
        }
        if (mockServer != null) {
            mockServer.stopAndWait();
        }
        if (mtlsServer != null) {
            mtlsServer.stopAndWait();
        }
    }

    /**
     * Create a PKCS12 keystore containing a certificate and private key.
     */
    private static void createPkcs12KeyStore(File certFile, File keyFile, File output, String password) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert;
        try (FileInputStream fis = new FileInputStream(certFile)) {
            cert = cf.generateCertificate(fis);
        }
        PrivateKey privateKey = loadPrivateKey(keyFile);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("client", privateKey, password.toCharArray(), new Certificate[]{cert});
        try (FileOutputStream fos = new FileOutputStream(output)) {
            ks.store(fos, password.toCharArray());
        }
    }

    /**
     * Create a PKCS12 truststore containing a trusted certificate.
     */
    private static void createPkcs12TrustStore(File certFile, File output, String password) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert;
        try (FileInputStream fis = new FileInputStream(certFile)) {
            cert = cf.generateCertificate(fis);
        }
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setCertificateEntry("server", cert);
        try (FileOutputStream fos = new FileOutputStream(output)) {
            ks.store(fos, password.toCharArray());
        }
    }

    /**
     * Load a private key from a PEM file.
     */
    private static PrivateKey loadPrivateKey(File keyFile) throws Exception {
        byte[] keyBytes = java.nio.file.Files.readAllBytes(keyFile.toPath());
        String keyString = new String(keyBytes, java.nio.charset.StandardCharsets.UTF_8);
        String pem = keyString
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = java.util.Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        try {
            return java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception e) {
            return java.security.KeyFactory.getInstance("EC").generatePrivate(keySpec);
        }
    }

}
