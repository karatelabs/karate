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
package io.karatelabs.core;

import io.karatelabs.output.LogContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.security.KeyStore;
import java.security.cert.Certificate;

/**
 * SSL utilities for creating SSL contexts and self-signed certificates.
 * Uses Netty's SelfSignedCertificate utility.
 */
public class SslUtils {

    private static final Logger logger = LogContext.RUNTIME_LOGGER;

    private static final int VALIDITY_DAYS = 365;

    /**
     * Generate a self-signed certificate and return an SSLContext.
     */
    public static SSLContext generateSelfSigned() {
        try {
            @SuppressWarnings("deprecation")
            SelfSignedCertificate ssc = SelfSignedCertificate.builder().fqdn("localhost").build();

            // Create KeyStore from the self-signed certificate
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);

            // Load certificate and private key from files
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            Certificate cert;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(ssc.certificate())) {
                cert = cf.generateCertificate(fis);
            }

            // Load private key
            java.security.PrivateKey privateKey = loadPrivateKeyFromFile(ssc.privateKey());

            keyStore.setKeyEntry("server", privateKey, new char[0], new Certificate[]{cert});

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, new char[0]);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, new java.security.SecureRandom());

            logger.info("generated self-signed certificate for localhost (valid {} days)", VALIDITY_DAYS);
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("failed to generate self-signed certificate: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a Netty SslContext for server use.
     */
    public static SslContext generateNettySslContext() {
        try {
            @SuppressWarnings("deprecation")
            SelfSignedCertificate ssc = SelfSignedCertificate.builder().fqdn("localhost").build();
            return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } catch (Exception e) {
            throw new RuntimeException("failed to generate Netty SSL context: " + e.getMessage(), e);
        }
    }

    /**
     * Create a Netty SslContext from PEM files.
     */
    public static SslContext createNettySslContext(File certFile, File keyFile) {
        try {
            return SslContextBuilder.forServer(certFile, keyFile).build();
        } catch (Exception e) {
            throw new RuntimeException("failed to create SSL context from files: " + e.getMessage(), e);
        }
    }

    /**
     * Create a Netty SslContext from PEM file paths.
     */
    public static SslContext createNettySslContext(String certPath, String keyPath) {
        return createNettySslContext(new File(certPath), new File(keyPath));
    }

    /**
     * Load private key from a PEM file.
     */
    private static java.security.PrivateKey loadPrivateKeyFromFile(File keyFile) throws Exception {
        byte[] keyBytes = java.nio.file.Files.readAllBytes(keyFile.toPath());
        String keyString = new String(keyBytes, java.nio.charset.StandardCharsets.UTF_8);

        // Remove PEM headers/footers and decode
        String privateKeyPEM = keyString
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = java.util.Base64.getDecoder().decode(privateKeyPEM);
        java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(decoded);

        // Try RSA first, then EC
        try {
            return java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception e) {
            try {
                return java.security.KeyFactory.getInstance("EC").generatePrivate(keySpec);
            } catch (Exception e2) {
                throw new RuntimeException("failed to load private key: unsupported algorithm", e2);
            }
        }
    }

}
