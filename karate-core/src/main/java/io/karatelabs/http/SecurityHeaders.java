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

/**
 * Applies security headers to HTTP responses to protect against common web vulnerabilities.
 * <p>
 * Headers applied:
 * <ul>
 *   <li><b>X-Content-Type-Options: nosniff</b> - Prevents MIME type sniffing attacks</li>
 *   <li><b>X-Frame-Options: DENY</b> - Prevents clickjacking by disallowing iframe embedding</li>
 *   <li><b>X-XSS-Protection: 1; mode=block</b> - Enables browser XSS filtering (legacy)</li>
 *   <li><b>Content-Security-Policy</b> - Controls resource loading (if configured)</li>
 *   <li><b>Strict-Transport-Security</b> - Enforces HTTPS (if configured, non-dev mode only)</li>
 *   <li><b>Referrer-Policy</b> - Controls referrer information (if configured)</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * ServerConfig config = new ServerConfig()
 *     .securityHeadersEnabled(true)
 *     .contentSecurityPolicy("default-src 'self'")
 *     .hstsEnabled(true);
 *
 * // In RequestHandler, after preparing response:
 * SecurityHeaders.apply(response, config);
 * </pre>
 */
public class SecurityHeaders {

    /**
     * Apply security headers to an HTTP response based on the server configuration.
     *
     * @param response the HTTP response to add headers to
     * @param config   the server configuration containing security settings
     */
    public static void apply(HttpResponse response, ServerConfig config) {
        if (!config.isSecurityHeadersEnabled()) {
            return;
        }

        // Always add these basic security headers
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", config.getXFrameOptions());
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Content-Security-Policy if configured
        String csp = config.getContentSecurityPolicy();
        if (csp != null && !csp.isEmpty()) {
            response.setHeader("Content-Security-Policy", csp);
        }

        // Referrer-Policy if configured
        String referrerPolicy = config.getReferrerPolicy();
        if (referrerPolicy != null && !referrerPolicy.isEmpty()) {
            response.setHeader("Referrer-Policy", referrerPolicy);
        }

        // HSTS only in production mode (not devMode)
        if (config.isHstsEnabled() && !config.isDevMode()) {
            // Default: 1 year, include subdomains
            String hstsValue = "max-age=" + config.getHstsMaxAge();
            if (config.isHstsIncludeSubDomains()) {
                hstsValue += "; includeSubDomains";
            }
            response.setHeader("Strict-Transport-Security", hstsValue);
        }
    }

    /**
     * Check if the given content type is HTML (requires security headers).
     *
     * @param contentType the content type header value
     * @return true if this is an HTML response
     */
    public static boolean isHtmlResponse(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.startsWith("text/html");
    }

}
