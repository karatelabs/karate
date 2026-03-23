package io.karatelabs.http;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE (Proof Key for Code Exchange) code verifier and challenge generator.
 * RFC 7636: https://datatracker.ietf.org/doc/html/rfc7636
 */
public class PkceGenerator {

    private final String verifier;
    private final String challenge;
    private final String method;

    private PkceGenerator(String verifier, String challenge, String method) {
        this.verifier = verifier;
        this.challenge = challenge;
        this.method = method;
    }

    /**
     * Create PKCE with S256 method (recommended)
     */
    public static PkceGenerator create() {
        return create("S256");
    }

    /**
     * Create PKCE with specified method
     * @param method "S256" (recommended) or "plain"
     */
    public static PkceGenerator create(String method) {
        String verifier = generateCodeVerifier();
        String challenge = generateCodeChallenge(verifier, method);
        return new PkceGenerator(verifier, challenge, method);
    }

    /**
     * Generate random code verifier (43-128 characters)
     */
    private static String generateCodeVerifier() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return base64UrlEncode(bytes);
    }

    /**
     * Generate code challenge from verifier
     */
    private static String generateCodeChallenge(String verifier, String method) {
        if ("plain".equals(method)) {
            return verifier;
        }
        if ("S256".equals(method)) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
                return base64UrlEncode(hash);
            } catch (Exception e) {
                throw new OAuth2Exception("Failed to generate code challenge", e);
            }
        }
        throw new IllegalArgumentException("Unsupported PKCE method: " + method);
    }

    /**
     * Base64-URL encoding without padding
     */
    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(data);
    }

    public String getVerifier() { return verifier; }
    public String getChallenge() { return challenge; }
    public String getMethod() { return method; }
}
