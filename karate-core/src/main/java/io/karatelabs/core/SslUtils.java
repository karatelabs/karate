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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * SSL utilities for creating SSL contexts and self-signed certificates.
 * A self-signed certificate comes from this JDK's keytool, else Netty's SelfSignedCertificate.
 */
public class SslUtils {

    private static final Logger logger = LogContext.RUNTIME_LOGGER;

    private static final int VALIDITY_DAYS = 365;
    private static final String ALIAS = "server";
    private static final char[] PASSWORD = "karate".toCharArray();
    private static final long KEYTOOL_MS = 60_000;

    record SelfSigned(PrivateKey key, X509Certificate cert) {
    }

    /**
     * Generate a self-signed certificate and return an SSLContext.
     */
    public static SSLContext generateSelfSigned() {
        try {
            SelfSigned s = selfSigned();
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setKeyEntry(ALIAS, s.key(), new char[0], new Certificate[]{s.cert()});
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
            SelfSigned s = selfSigned();
            return SslContextBuilder.forServer(s.key(), s.cert()).build();
        } catch (Exception e) {
            throw new RuntimeException("failed to generate Netty SSL context: " + e.getMessage(), e);
        }
    }

    /** {@code bin/keytool} under {@code javaHome}, {@code keytool.exe} on Windows (Netty's own lookup misses it there). */
    static Path keytool(String javaHome, String osName) {
        return Path.of(javaHome, "bin", osName.startsWith("Windows") ? "keytool.exe" : "keytool");
    }

    static SelfSigned selfSigned() throws Exception {
        Path keytool = keytool(System.getProperty("java.home"), System.getProperty("os.name", ""));
        return Files.isRegularFile(keytool) ? fromKeytool(keytool) : fromNetty();
    }

    private static SelfSigned fromKeytool(Path keytool) throws Exception {
        Path dir = Files.createTempDirectory("karate-ssl");
        Path store = dir.resolve("server.p12");
        Path log = dir.resolve("keytool.log");
        try {
            Process p = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", ALIAS, "-keyalg", "RSA", "-keysize", "2048",
                    "-validity", String.valueOf(VALIDITY_DAYS), "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                    "-storetype", "PKCS12", "-keystore", store.toString(), "-storepass", new String(PASSWORD),
                    "-keypass", new String(PASSWORD)).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!p.waitFor(KEYTOOL_MS, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                throw new IOException("keytool did not finish in " + KEYTOOL_MS + " ms");
            }
            if (p.exitValue() != 0) {
                throw new IOException("keytool exited " + p.exitValue() + ": " + Files.readString(log).strip());
            }
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(store)) {
                ks.load(in, PASSWORD);
            }
            return new SelfSigned((PrivateKey) ks.getKey(ALIAS, PASSWORD), (X509Certificate) ks.getCertificate(ALIAS));
        } finally {
            Files.deleteIfExists(store);
            Files.deleteIfExists(log);
            Files.deleteIfExists(dir);
        }
    }

    private static SelfSigned fromNetty() throws Exception {
        java.util.Date notBefore = new java.util.Date();
        java.util.Date notAfter = new java.util.Date(notBefore.getTime() + (86400000L * VALIDITY_DAYS));
        @SuppressWarnings("deprecation")
        SelfSignedCertificate ssc = new SelfSignedCertificate("localhost", notBefore, notAfter);
        try {
            return new SelfSigned(loadPrivateKeyFromFile(ssc.privateKey()), ssc.cert());
        } finally {
            ssc.delete();
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
