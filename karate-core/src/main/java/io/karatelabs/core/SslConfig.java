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

import java.util.Map;

/**
 * SSL/TLS configuration for both client and server contexts.
 * <p>
 * Client configuration (for HTTP client):
 * <ul>
 *   <li>trustAll - Trust all server certificates (for self-signed)</li>
 *   <li>keyStore/keyStorePassword/keyStoreType - Client certificate for mTLS</li>
 *   <li>trustStore/trustStorePassword/trustStoreType - Custom trust store</li>
 * </ul>
 * <p>
 * Server configuration (for mock server):
 * <ul>
 *   <li>certPath - PEM certificate file path</li>
 *   <li>keyPath - PEM private key file path</li>
 *   <li>clientAuth - Require client certificate (mTLS)</li>
 * </ul>
 */
public class SslConfig {

    // Trust configuration (client)
    private boolean trustAll;
    private String trustStore;
    private String trustStorePassword;
    private String trustStoreType;

    // Client certificate (mTLS)
    private String keyStore;
    private String keyStorePassword;
    private String keyStoreType;

    // Algorithm
    private String algorithm = "TLS";

    // Server-side (for MockServer)
    private String certPath;
    private String keyPath;
    private boolean clientAuth;
    private String trustStorePath; // For server-side client cert validation

    /**
     * Create a trust-all configuration (for self-signed certificates).
     */
    public static SslConfig trustAll() {
        SslConfig config = new SslConfig();
        config.setTrustAll(true);
        return config;
    }

    /**
     * Create an SslConfig from a map (for karate.configure('ssl', {...})).
     */
    @SuppressWarnings("unchecked")
    public static SslConfig fromMap(Map<String, Object> map) {
        SslConfig config = new SslConfig();

        if (map.containsKey("trustAll")) {
            config.setTrustAll(Boolean.TRUE.equals(map.get("trustAll")));
        }

        if (map.containsKey("keyStore")) {
            config.setKeyStore((String) map.get("keyStore"));
        }
        if (map.containsKey("keyStorePassword")) {
            config.setKeyStorePassword((String) map.get("keyStorePassword"));
        }
        if (map.containsKey("keyStoreType")) {
            config.setKeyStoreType((String) map.get("keyStoreType"));
        }

        if (map.containsKey("trustStore")) {
            config.setTrustStore((String) map.get("trustStore"));
        }
        if (map.containsKey("trustStorePassword")) {
            config.setTrustStorePassword((String) map.get("trustStorePassword"));
        }
        if (map.containsKey("trustStoreType")) {
            config.setTrustStoreType((String) map.get("trustStoreType"));
        }

        if (map.containsKey("algorithm")) {
            config.setAlgorithm((String) map.get("algorithm"));
        }

        // Server-side options
        if (map.containsKey("cert")) {
            config.setCertPath((String) map.get("cert"));
        }
        if (map.containsKey("key")) {
            config.setKeyPath((String) map.get("key"));
        }

        return config;
    }

    // Getters and setters

    public boolean isTrustAll() {
        return trustAll;
    }

    public void setTrustAll(boolean trustAll) {
        this.trustAll = trustAll;
    }

    public String getTrustStore() {
        return trustStore;
    }

    public void setTrustStore(String trustStore) {
        this.trustStore = trustStore;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public String getTrustStoreType() {
        return trustStoreType;
    }

    public void setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType;
    }

    public String getKeyStore() {
        return keyStore;
    }

    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getCertPath() {
        return certPath;
    }

    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }

    public String getKeyPath() {
        return keyPath;
    }

    public void setKeyPath(String keyPath) {
        this.keyPath = keyPath;
    }

    public boolean isClientAuth() {
        return clientAuth;
    }

    public void setClientAuth(boolean clientAuth) {
        this.clientAuth = clientAuth;
    }

    public String getTrustStorePath() {
        return trustStorePath;
    }

    public void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

}
