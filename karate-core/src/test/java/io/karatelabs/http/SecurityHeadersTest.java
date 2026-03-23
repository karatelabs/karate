package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityHeadersTest {

    @Test
    void testApplyDefaultHeaders() {
        ServerConfig config = new ServerConfig();
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("1; mode=block", response.getHeader("X-XSS-Protection"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
    }

    @Test
    void testApplyDisabled() {
        ServerConfig config = new ServerConfig().securityHeadersEnabled(false);
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        assertNull(response.getHeader("X-Content-Type-Options"));
        assertNull(response.getHeader("X-Frame-Options"));
        assertNull(response.getHeader("X-XSS-Protection"));
    }

    @Test
    void testApplyWithCsp() {
        ServerConfig config = new ServerConfig()
                .contentSecurityPolicy("default-src 'self'; script-src 'self' https://cdn.example.com");
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        assertEquals("default-src 'self'; script-src 'self' https://cdn.example.com",
                response.getHeader("Content-Security-Policy"));
    }

    @Test
    void testApplyWithoutCsp() {
        ServerConfig config = new ServerConfig();
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        assertNull(response.getHeader("Content-Security-Policy"));
    }

    @Test
    void testApplyWithEmptyCsp() {
        ServerConfig config = new ServerConfig().contentSecurityPolicy("");
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        assertNull(response.getHeader("Content-Security-Policy"));
    }

    @Test
    void testApplyCustomXFrameOptions() {
        ServerConfig config = new ServerConfig().xFrameOptions("SAMEORIGIN");
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        assertEquals("SAMEORIGIN", response.getHeader("X-Frame-Options"));
    }

    @Test
    void testApplyCustomReferrerPolicy() {
        ServerConfig config = new ServerConfig().referrerPolicy("no-referrer");
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
    }

    @Test
    void testApplyNoReferrerPolicy() {
        ServerConfig config = new ServerConfig().referrerPolicy(null);
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        assertNull(response.getHeader("Referrer-Policy"));
    }

    @Test
    void testApplyHstsInProductionMode() {
        ServerConfig config = new ServerConfig()
                .hstsEnabled(true)
                .devMode(false);
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        String hsts = response.getHeader("Strict-Transport-Security");
        assertNotNull(hsts);
        assertTrue(hsts.contains("max-age=31536000"));
        assertTrue(hsts.contains("includeSubDomains"));
    }

    @Test
    void testApplyHstsNotInDevMode() {
        ServerConfig config = new ServerConfig()
                .hstsEnabled(true)
                .devMode(true);
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        assertNull(response.getHeader("Strict-Transport-Security"));
    }

    @Test
    void testApplyHstsDisabled() {
        ServerConfig config = new ServerConfig()
                .hstsEnabled(false)
                .devMode(false);
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        assertNull(response.getHeader("Strict-Transport-Security"));
    }

    @Test
    void testApplyHstsCustomMaxAge() {
        ServerConfig config = new ServerConfig()
                .hstsEnabled(true)
                .hstsMaxAge(86400) // 1 day
                .devMode(false);
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        String hsts = response.getHeader("Strict-Transport-Security");
        assertTrue(hsts.contains("max-age=86400"));
    }

    @Test
    void testApplyHstsWithoutSubDomains() {
        ServerConfig config = new ServerConfig()
                .hstsEnabled(true)
                .hstsIncludeSubDomains(false)
                .devMode(false);
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        String hsts = response.getHeader("Strict-Transport-Security");
        assertEquals("max-age=31536000", hsts);
        assertFalse(hsts.contains("includeSubDomains"));
    }

    @Test
    void testIsHtmlResponseTrue() {
        assertTrue(SecurityHeaders.isHtmlResponse("text/html"));
        assertTrue(SecurityHeaders.isHtmlResponse("text/html; charset=utf-8"));
    }

    @Test
    void testIsHtmlResponseFalse() {
        assertFalse(SecurityHeaders.isHtmlResponse("application/json"));
        assertFalse(SecurityHeaders.isHtmlResponse("text/plain"));
        assertFalse(SecurityHeaders.isHtmlResponse("text/css"));
        assertFalse(SecurityHeaders.isHtmlResponse("application/javascript"));
    }

    @Test
    void testIsHtmlResponseNull() {
        assertFalse(SecurityHeaders.isHtmlResponse(null));
    }

    @Test
    void testApplyFullConfig() {
        ServerConfig config = new ServerConfig()
                .securityHeadersEnabled(true)
                .contentSecurityPolicy("default-src 'self'")
                .xFrameOptions("SAMEORIGIN")
                .referrerPolicy("same-origin")
                .hstsEnabled(true)
                .hstsMaxAge(63072000) // 2 years
                .hstsIncludeSubDomains(true)
                .devMode(false);
        HttpResponse response = new HttpResponse();

        SecurityHeaders.apply(response, config);

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("SAMEORIGIN", response.getHeader("X-Frame-Options"));
        assertEquals("1; mode=block", response.getHeader("X-XSS-Protection"));
        assertEquals("default-src 'self'", response.getHeader("Content-Security-Policy"));
        assertEquals("same-origin", response.getHeader("Referrer-Policy"));
        assertEquals("max-age=63072000; includeSubDomains", response.getHeader("Strict-Transport-Security"));
    }

}
