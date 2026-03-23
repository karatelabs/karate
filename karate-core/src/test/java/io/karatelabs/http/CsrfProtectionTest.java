package io.karatelabs.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class CsrfProtectionTest {

    private Session session;

    @BeforeEach
    void setUp() {
        session = new Session("test-session-id", new HashMap<>(),
                System.currentTimeMillis(), System.currentTimeMillis(),
                System.currentTimeMillis() + 3600000);
    }

    // Token generation tests

    @Test
    void testGenerateToken() {
        String token1 = CsrfProtection.generateToken();
        String token2 = CsrfProtection.generateToken();

        assertNotNull(token1);
        assertNotNull(token2);
        assertNotEquals(token1, token2); // Should be unique
        assertTrue(token1.length() > 20); // Should be reasonably long (Base64 of 32 bytes)
    }

    @Test
    void testGenerateTokenIsUrlSafe() {
        // Generate multiple tokens and verify they're URL-safe Base64
        for (int i = 0; i < 10; i++) {
            String token = CsrfProtection.generateToken();
            // URL-safe Base64 should not contain +, /, or =
            assertFalse(token.contains("+"), "Token should not contain +");
            assertFalse(token.contains("/"), "Token should not contain /");
            assertFalse(token.contains("="), "Token should not contain padding");
        }
    }

    @Test
    void testGetOrCreateToken() {
        String token1 = CsrfProtection.getOrCreateToken(session);
        String token2 = CsrfProtection.getOrCreateToken(session);

        assertNotNull(token1);
        assertEquals(token1, token2); // Same session should return same token
    }

    @Test
    void testGetOrCreateTokenStoresInSession() {
        String token = CsrfProtection.getOrCreateToken(session);

        assertEquals(token, session.getMember(CsrfProtection.SESSION_KEY));
    }

    @Test
    void testGetOrCreateTokenNullSession() {
        String token = CsrfProtection.getOrCreateToken(null);

        assertNull(token);
    }

    @Test
    void testGetOrCreateTokenTemporarySession() {
        String token = CsrfProtection.getOrCreateToken(Session.TEMPORARY);

        assertNull(token);
    }

    // Validation tests

    @Test
    void testValidateWithHeaderToken() {
        String token = CsrfProtection.getOrCreateToken(session);

        HttpRequest request = new HttpRequest();
        request.putHeader("X-CSRF-Token", token);

        assertTrue(CsrfProtection.validate(request, session));
    }

    @Test
    void testValidateWithHtmxHeader() {
        String token = CsrfProtection.getOrCreateToken(session);

        HttpRequest request = new HttpRequest();
        request.putHeader("HX-CSRF-Token", token);

        assertTrue(CsrfProtection.validate(request, session));
    }

    @Test
    void testValidateWithFormField() {
        String token = CsrfProtection.getOrCreateToken(session);

        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost/?_csrf=" + token);

        assertTrue(CsrfProtection.validate(request, session));
    }

    @Test
    void testValidateInvalidToken() {
        CsrfProtection.getOrCreateToken(session);

        HttpRequest request = new HttpRequest();
        request.putHeader("X-CSRF-Token", "invalid-token");

        assertFalse(CsrfProtection.validate(request, session));
    }

    @Test
    void testValidateMissingToken() {
        CsrfProtection.getOrCreateToken(session);

        HttpRequest request = new HttpRequest();

        assertFalse(CsrfProtection.validate(request, session));
    }

    @Test
    void testValidateNullSession() {
        HttpRequest request = new HttpRequest();
        request.putHeader("X-CSRF-Token", "some-token");

        assertFalse(CsrfProtection.validate(request, null));
    }

    @Test
    void testValidateNoTokenInSession() {
        HttpRequest request = new HttpRequest();
        request.putHeader("X-CSRF-Token", "some-token");

        assertFalse(CsrfProtection.validate(request, session));
    }

    @Test
    void testValidateTemporarySession() {
        HttpRequest request = new HttpRequest();
        request.putHeader("X-CSRF-Token", "some-token");

        assertFalse(CsrfProtection.validate(request, Session.TEMPORARY));
    }

    // requiresValidation tests

    @Test
    void testRequiresValidationPost() {
        assertTrue(CsrfProtection.requiresValidation("POST"));
        assertTrue(CsrfProtection.requiresValidation("post"));
    }

    @Test
    void testRequiresValidationPut() {
        assertTrue(CsrfProtection.requiresValidation("PUT"));
        assertTrue(CsrfProtection.requiresValidation("put"));
    }

    @Test
    void testRequiresValidationPatch() {
        assertTrue(CsrfProtection.requiresValidation("PATCH"));
        assertTrue(CsrfProtection.requiresValidation("patch"));
    }

    @Test
    void testRequiresValidationDelete() {
        assertTrue(CsrfProtection.requiresValidation("DELETE"));
        assertTrue(CsrfProtection.requiresValidation("delete"));
    }

    @Test
    void testRequiresValidationGet() {
        assertFalse(CsrfProtection.requiresValidation("GET"));
        assertFalse(CsrfProtection.requiresValidation("get"));
    }

    @Test
    void testRequiresValidationHead() {
        assertFalse(CsrfProtection.requiresValidation("HEAD"));
    }

    @Test
    void testRequiresValidationOptions() {
        assertFalse(CsrfProtection.requiresValidation("OPTIONS"));
    }

    @Test
    void testRequiresValidationNull() {
        assertFalse(CsrfProtection.requiresValidation(null));
    }

    // getTokenFromRequest tests

    @Test
    void testGetTokenFromRequestHeader() {
        HttpRequest request = new HttpRequest();
        request.putHeader("X-CSRF-Token", "token-from-header");

        assertEquals("token-from-header", CsrfProtection.getTokenFromRequest(request));
    }

    @Test
    void testGetTokenFromRequestHtmxHeader() {
        HttpRequest request = new HttpRequest();
        request.putHeader("HX-CSRF-Token", "token-from-htmx");

        assertEquals("token-from-htmx", CsrfProtection.getTokenFromRequest(request));
    }

    @Test
    void testGetTokenFromRequestFormField() {
        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost/submit?_csrf=token-from-form");

        assertEquals("token-from-form", CsrfProtection.getTokenFromRequest(request));
    }

    @Test
    void testGetTokenFromRequestPriority() {
        // X-CSRF-Token header should take priority over form field
        HttpRequest request = new HttpRequest();
        request.putHeader("X-CSRF-Token", "header-token");
        request.setUrl("http://localhost/submit?_csrf=form-token");

        assertEquals("header-token", CsrfProtection.getTokenFromRequest(request));
    }

    @Test
    void testGetTokenFromRequestNone() {
        HttpRequest request = new HttpRequest();

        assertNull(CsrfProtection.getTokenFromRequest(request));
    }

    // CsrfToken template object tests

    @Test
    void testCreateTemplateToken() {
        CsrfProtection.CsrfToken csrfToken = CsrfProtection.createTemplateToken(session);

        assertNotNull(csrfToken);
        assertNotNull(csrfToken.getToken());
        assertEquals("X-CSRF-Token", csrfToken.getHeaderName());
        assertEquals("_csrf", csrfToken.getFieldName());
    }

    @Test
    void testCsrfTokenJsGet() {
        CsrfProtection.CsrfToken csrfToken = CsrfProtection.createTemplateToken(session);

        assertEquals(csrfToken.getToken(), csrfToken.jsGet("token"));
        assertEquals("X-CSRF-Token", csrfToken.jsGet("headerName"));
        assertEquals("_csrf", csrfToken.jsGet("fieldName"));
        assertNull(csrfToken.jsGet("unknown"));
    }

    @Test
    void testCsrfTokenToMap() {
        CsrfProtection.CsrfToken csrfToken = CsrfProtection.createTemplateToken(session);

        var map = csrfToken.toMap();

        assertEquals(csrfToken.getToken(), map.get("token"));
        assertEquals("X-CSRF-Token", map.get("headerName"));
        assertEquals("_csrf", map.get("fieldName"));
    }

    @Test
    void testCreateTemplateTokenNullSession() {
        CsrfProtection.CsrfToken csrfToken = CsrfProtection.createTemplateToken(null);

        assertNotNull(csrfToken);
        assertNull(csrfToken.getToken());
    }

    // Timing attack prevention test

    @Test
    void testConstantTimeComparison() {
        // This is a basic test to ensure validation doesn't short-circuit
        // A proper timing attack test would require measuring execution time
        String token = CsrfProtection.getOrCreateToken(session);

        HttpRequest request1 = new HttpRequest();
        request1.putHeader("X-CSRF-Token", "a"); // Very short wrong token

        HttpRequest request2 = new HttpRequest();
        request2.putHeader("X-CSRF-Token", token.substring(0, token.length() - 1) + "X"); // Almost right

        // Both should fail, and the comparison should be constant-time
        assertFalse(CsrfProtection.validate(request1, session));
        assertFalse(CsrfProtection.validate(request2, session));
    }

}
