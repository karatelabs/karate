package io.karatelabs.http;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PkceGeneratorTest {

    static final Logger logger = LoggerFactory.getLogger(PkceGeneratorTest.class);

    @Test
    void testCreateWithDefaultS256Method() {
        PkceGenerator pkce = PkceGenerator.create();

        assertNotNull(pkce.getVerifier());
        assertNotNull(pkce.getChallenge());
        assertEquals("S256", pkce.getMethod());
    }

    @Test
    void testCreateWithS256Method() {
        PkceGenerator pkce = PkceGenerator.create("S256");

        assertNotNull(pkce.getVerifier());
        assertNotNull(pkce.getChallenge());
        assertEquals("S256", pkce.getMethod());

        // Verifier and challenge should be different for S256
        assertNotEquals(pkce.getVerifier(), pkce.getChallenge());
    }

    @Test
    void testCreateWithPlainMethod() {
        PkceGenerator pkce = PkceGenerator.create("plain");

        assertNotNull(pkce.getVerifier());
        assertNotNull(pkce.getChallenge());
        assertEquals("plain", pkce.getMethod());

        // For plain method, verifier and challenge should be the same
        assertEquals(pkce.getVerifier(), pkce.getChallenge());
    }

    @Test
    void testUnsupportedMethod() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            PkceGenerator.create("unsupported");
        });

        assertTrue(exception.getMessage().contains("Unsupported PKCE method"));
    }

    @Test
    void testVerifierLength() {
        PkceGenerator pkce = PkceGenerator.create();
        String verifier = pkce.getVerifier();

        // RFC 7636: code_verifier = 43-128 characters
        assertTrue(verifier.length() >= 43 && verifier.length() <= 128,
            "Verifier length should be between 43 and 128 characters, got: " + verifier.length());
    }

    @Test
    void testVerifierCharacters() {
        PkceGenerator pkce = PkceGenerator.create();
        String verifier = pkce.getVerifier();

        // RFC 7636: code_verifier uses unreserved characters [A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~"
        // Base64-URL encoding uses [A-Z] / [a-z] / [0-9] / "-" / "_"
        assertTrue(verifier.matches("[A-Za-z0-9_-]+"),
            "Verifier should only contain Base64-URL characters");
    }

    @Test
    void testChallengeLength() {
        PkceGenerator pkce = PkceGenerator.create("S256");
        String challenge = pkce.getChallenge();

        // SHA-256 hash is 32 bytes, Base64-URL encoded is 43 characters
        assertEquals(43, challenge.length(),
            "S256 challenge should be 43 characters (32 bytes Base64-URL encoded)");
    }

    @Test
    void testChallengeCharacters() {
        PkceGenerator pkce = PkceGenerator.create();
        String challenge = pkce.getChallenge();

        // Base64-URL encoding uses [A-Z] / [a-z] / [0-9] / "-" / "_"
        assertTrue(challenge.matches("[A-Za-z0-9_-]+"),
            "Challenge should only contain Base64-URL characters");
    }

    @Test
    void testVerifierUniqueness() {
        Set<String> verifiers = new HashSet<>();

        // Generate 100 verifiers and ensure they're all unique
        for (int i = 0; i < 100; i++) {
            PkceGenerator pkce = PkceGenerator.create();
            verifiers.add(pkce.getVerifier());
        }

        assertEquals(100, verifiers.size(), "All verifiers should be unique");
    }

    @Test
    void testChallengeUniqueness() {
        Set<String> challenges = new HashSet<>();

        // Generate 100 challenges and ensure they're all unique
        for (int i = 0; i < 100; i++) {
            PkceGenerator pkce = PkceGenerator.create();
            challenges.add(pkce.getChallenge());
        }

        assertEquals(100, challenges.size(), "All challenges should be unique");
    }

    @Test
    void testS256ChallengeIsCorrectHash() throws Exception {
        PkceGenerator pkce = PkceGenerator.create("S256");
        String verifier = pkce.getVerifier();
        String challenge = pkce.getChallenge();

        // Manually compute the expected challenge
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String expectedChallenge = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(hash);

        assertEquals(expectedChallenge, challenge,
            "S256 challenge should be Base64-URL(SHA256(verifier))");
    }

    @Test
    void testPlainChallengeEqualsVerifier() {
        PkceGenerator pkce = PkceGenerator.create("plain");

        assertEquals(pkce.getVerifier(), pkce.getChallenge(),
            "Plain method: challenge should equal verifier");
    }

    @Test
    void testNoPaddingInBase64Url() {
        PkceGenerator pkce = PkceGenerator.create();
        String verifier = pkce.getVerifier();
        String challenge = pkce.getChallenge();

        // Base64-URL without padding should not contain '='
        assertFalse(verifier.contains("="), "Verifier should not contain padding");
        assertFalse(challenge.contains("="), "Challenge should not contain padding");
    }

    @Test
    void testMultipleInstancesProduceDifferentValues() {
        PkceGenerator pkce1 = PkceGenerator.create();
        PkceGenerator pkce2 = PkceGenerator.create();

        assertNotEquals(pkce1.getVerifier(), pkce2.getVerifier(),
            "Different instances should produce different verifiers");
        assertNotEquals(pkce1.getChallenge(), pkce2.getChallenge(),
            "Different instances should produce different challenges");
    }

    @Test
    void testGettersReturnCorrectValues() {
        PkceGenerator pkce = PkceGenerator.create("S256");

        assertNotNull(pkce.getVerifier());
        assertNotNull(pkce.getChallenge());
        assertEquals("S256", pkce.getMethod());

        // Getters should return the same value on multiple calls
        String verifier1 = pkce.getVerifier();
        String verifier2 = pkce.getVerifier();
        assertEquals(verifier1, verifier2, "Verifier getter should be consistent");
    }
}
