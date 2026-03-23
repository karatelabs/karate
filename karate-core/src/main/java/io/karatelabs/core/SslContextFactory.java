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

import io.karatelabs.common.FileUtils;
import io.karatelabs.common.Resource;
import io.karatelabs.output.LogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Factory for creating SSLContext instances from SslConfig.
 */
public class SslContextFactory {

    private static final Logger logger = LogContext.RUNTIME_LOGGER;

    /**
     * Create an SSLContext for client use (connecting to HTTPS servers).
     */
    public static SSLContext createClientContext(SslConfig config) {
        try {
            if (config.isTrustAll()) {
                return createTrustAllContext(config.getAlgorithm());
            }

            TrustManager[] trustManagers = loadTrustManagers(config);
            KeyManager[] keyManagers = loadKeyManagers(config);

            SSLContext ctx = SSLContext.getInstance(config.getAlgorithm());
            ctx.init(keyManagers, trustManagers, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("failed to create client SSL context: " + e.getMessage(), e);
        }
    }

    /**
     * Create an SSLContext for server use (accepting HTTPS connections).
     */
    public static SSLContext createServerContext(SslConfig config) {
        try {
            if (config.getCertPath() == null) {
                // Generate self-signed certificate
                return SslUtils.generateSelfSigned();
            }
            return loadFromPem(config.getCertPath(), config.getKeyPath(), config.getAlgorithm());
        } catch (Exception e) {
            throw new RuntimeException("failed to create server SSL context: " + e.getMessage(), e);
        }
    }

    /**
     * Create a trust-all SSLContext (accepts any certificate).
     */
    private static SSLContext createTrustAllContext(String algorithm) throws Exception {
        TrustManager[] trustManagers = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };

        SSLContext ctx = SSLContext.getInstance(algorithm != null ? algorithm : "TLS");
        ctx.init(null, trustManagers, new SecureRandom());
        return ctx;
    }

    /**
     * Load TrustManagers from config (custom trust store).
     */
    private static TrustManager[] loadTrustManagers(SslConfig config) throws Exception {
        if (config.getTrustStore() == null) {
            return null; // Use default trust store
        }

        String type = config.getTrustStoreType() != null ? config.getTrustStoreType() : "JKS";
        KeyStore trustStore = KeyStore.getInstance(type);

        byte[] data = loadResource(config.getTrustStore());
        char[] password = config.getTrustStorePassword() != null
                ? config.getTrustStorePassword().toCharArray() : null;

        try (InputStream is = new ByteArrayInputStream(data)) {
            trustStore.load(is, password);
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf.getTrustManagers();
    }

    /**
     * Load KeyManagers from config (client certificate for mTLS).
     */
    private static KeyManager[] loadKeyManagers(SslConfig config) throws Exception {
        if (config.getKeyStore() == null) {
            return null; // No client certificate
        }

        String type = config.getKeyStoreType() != null ? config.getKeyStoreType() : "PKCS12";
        KeyStore keyStore = KeyStore.getInstance(type);

        byte[] data = loadResource(config.getKeyStore());
        char[] password = config.getKeyStorePassword() != null
                ? config.getKeyStorePassword().toCharArray() : null;

        try (InputStream is = new ByteArrayInputStream(data)) {
            keyStore.load(is, password);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);
        return kmf.getKeyManagers();
    }

    /**
     * Load SSLContext from PEM certificate and key files.
     */
    private static SSLContext loadFromPem(String certPath, String keyPath, String algorithm) throws Exception {
        byte[] certData = loadResource(certPath);
        byte[] keyData = loadResource(keyPath);

        // Parse certificate
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert;
        try (InputStream is = new ByteArrayInputStream(certData)) {
            cert = cf.generateCertificate(is);
        }

        // Parse private key
        PrivateKey privateKey = loadPrivateKey(keyData);

        // Create KeyStore with certificate chain
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", privateKey, new char[0], new Certificate[]{cert});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        SSLContext ctx = SSLContext.getInstance(algorithm != null ? algorithm : "TLS");
        ctx.init(kmf.getKeyManagers(), null, new SecureRandom());
        return ctx;
    }

    /**
     * Load a private key from PEM format.
     */
    private static PrivateKey loadPrivateKey(byte[] keyData) throws Exception {
        String keyString = new String(keyData);

        // Remove PEM headers/footers and decode
        String privateKeyPEM = keyString
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);

        // Try RSA first, then EC
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception e) {
            try {
                return KeyFactory.getInstance("EC").generatePrivate(keySpec);
            } catch (Exception e2) {
                throw new RuntimeException("failed to load private key: unsupported algorithm", e2);
            }
        }
    }

    /**
     * Load resource from path (supports classpath: prefix).
     */
    private static byte[] loadResource(String path) {
        Resource resource = Resource.path(path);
        return FileUtils.toBytes(resource.getText());
    }

}
