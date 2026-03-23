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
package io.karatelabs.http;

import io.karatelabs.js.SimpleObject;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * CSRF (Cross-Site Request Forgery) protection utilities.
 * <p>
 * CSRF tokens are stored in the session and validated on state-changing requests
 * (POST, PUT, PATCH, DELETE). The token can be sent via:
 * <ul>
 *   <li>Form field: {@code <input type="hidden" name="_csrf" th:value="csrf.token">}</li>
 *   <li>Request header: {@code X-CSRF-Token: <token>}</li>
 *   <li>HTMX header: {@code HX-CSRF-Token: <token>} (sent automatically if configured)</li>
 * </ul>
 * <p>
 * Usage in templates:
 * <pre>
 * &lt;form method="post"&gt;
 *     &lt;input type="hidden" name="_csrf" th:value="csrf.token"&gt;
 *     ...
 * &lt;/form&gt;
 *
 * &lt;!-- For HTMX requests, add to hx-headers or configure globally --&gt;
 * &lt;meta name="csrf-token" th:content="csrf.token"&gt;
 * &lt;script&gt;
 *     document.body.addEventListener('htmx:configRequest', (e) =&gt; {
 *         e.detail.headers['X-CSRF-Token'] = document.querySelector('meta[name="csrf-token"]').content;
 *     });
 * &lt;/script&gt;
 * </pre>
 */
public class CsrfProtection {

    /** Session key where the CSRF token is stored */
    public static final String SESSION_KEY = "_csrf_token";

    /** Default form field name for CSRF token */
    public static final String FORM_FIELD_NAME = "_csrf";

    /** Default HTTP header name for CSRF token */
    public static final String HEADER_NAME = "X-CSRF-Token";

    /** HTMX-specific header name */
    public static final String HTMX_HEADER_NAME = "HX-CSRF-Token";

    /** HTTP methods that require CSRF validation */
    private static final Set<String> PROTECTED_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_LENGTH = 32; // 256 bits

    /**
     * Generate a new cryptographically secure CSRF token.
     *
     * @return a Base64-encoded random token
     */
    public static String generateToken() {
        byte[] bytes = new byte[TOKEN_LENGTH];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Get or create a CSRF token for the given session.
     * If a token already exists in the session, it is returned.
     * Otherwise, a new token is generated and stored.
     *
     * @param session the session to get/store the token in
     * @return the CSRF token, or null if session is null
     */
    public static String getOrCreateToken(Session session) {
        if (session == null || session.isTemporary()) {
            return null;
        }
        String token = (String) session.getMember(SESSION_KEY);
        if (token == null) {
            token = generateToken();
            session.putMember(SESSION_KEY, token);
        }
        return token;
    }

    /**
     * Validate a CSRF token from a request against the session token.
     *
     * @param request the HTTP request containing the token
     * @param session the session containing the expected token
     * @return true if the token is valid, false otherwise
     */
    public static boolean validate(HttpRequest request, Session session) {
        if (session == null || session.isTemporary()) {
            return false;
        }

        String expectedToken = (String) session.getMember(SESSION_KEY);
        if (expectedToken == null) {
            return false;
        }

        // Try to get token from various sources
        String providedToken = getTokenFromRequest(request);
        if (providedToken == null) {
            return false;
        }

        // Constant-time comparison to prevent timing attacks
        return constantTimeEquals(expectedToken, providedToken);
    }

    /**
     * Extract CSRF token from request (header or form field).
     *
     * @param request the HTTP request
     * @return the token if found, null otherwise
     */
    public static String getTokenFromRequest(HttpRequest request) {
        // 1. Try X-CSRF-Token header
        String token = request.getHeader(HEADER_NAME);
        if (token != null && !token.isEmpty()) {
            return token;
        }

        // 2. Try HX-CSRF-Token header (HTMX)
        token = request.getHeader(HTMX_HEADER_NAME);
        if (token != null && !token.isEmpty()) {
            return token;
        }

        // 3. Try form field (from parsed body)
        token = request.getParam(FORM_FIELD_NAME);
        if (token != null && !token.isEmpty()) {
            return token;
        }

        return null;
    }

    /**
     * Check if the request method requires CSRF validation.
     *
     * @param method the HTTP method
     * @return true if CSRF validation is required
     */
    public static boolean requiresValidation(String method) {
        return method != null && PROTECTED_METHODS.contains(method.toUpperCase());
    }

    /**
     * Create a template-accessible object containing CSRF token information.
     * This can be added to template variables as "csrf".
     *
     * @param session the session to get the token from
     * @return a SimpleObject with token, headerName, and fieldName properties
     */
    public static CsrfToken createTemplateToken(Session session) {
        String token = getOrCreateToken(session);
        return new CsrfToken(token);
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * CSRF token object exposed to templates.
     * Access in templates as: csrf.token, csrf.headerName, csrf.fieldName
     */
    public static class CsrfToken implements SimpleObject {

        private final String token;

        public CsrfToken(String token) {
            this.token = token;
        }

        public String getToken() {
            return token;
        }

        public String getHeaderName() {
            return HEADER_NAME;
        }

        public String getFieldName() {
            return FORM_FIELD_NAME;
        }

        @Override
        public Object jsGet(String key) {
            return switch (key) {
                case "token" -> token;
                case "headerName" -> HEADER_NAME;
                case "fieldName" -> FORM_FIELD_NAME;
                default -> null;
            };
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of(
                    "token", token != null ? token : "",
                    "headerName", HEADER_NAME,
                    "fieldName", FORM_FIELD_NAME
            );
        }
    }

}
