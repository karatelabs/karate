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
 * SameSite cookie attribute values per RFC 6265bis.
 * <p>
 * Used by {@link ServerConfig#sessionSameSite(SameSite)} to control whether
 * the session cookie is attached on cross-site navigations.
 * <ul>
 *   <li>{@link #STRICT} — never sent on any cross-site request. Strongest CSRF
 *       defense, but breaks OAuth callbacks (the IdP redirects back to your
 *       /signin?code=... and the browser drops the cookie).</li>
 *   <li>{@link #LAX} — sent on top-level navigations using safe methods (GET).
 *       This is the framework default and works for OAuth callbacks that use
 *       {@code response_mode=query}, where the IdP issues a 302 GET back.</li>
 *   <li>{@link #NONE} — sent on all cross-site requests, including top-level
 *       POSTs. Required for OAuth/OIDC flows that use {@code response_mode=form_post}
 *       (e.g. Microsoft / Azure AD via MSAL4J &gt;= 1.18, which deprecated QUERY
 *       and force-overrides to FORM_POST). Implies {@code Secure} — the browser
 *       refuses {@code SameSite=None} cookies that lack {@code Secure}, so
 *       {@code Secure} is added automatically by {@code ServerRequestHandler}
 *       outside dev mode. Pair with explicit CSRF tokens (already enforced by
 *       {@link CsrfProtection}) since SameSite is no longer a defense layer.</li>
 * </ul>
 */
public enum SameSite {

    STRICT("Strict"),
    LAX("Lax"),
    NONE("None");

    private final String headerValue;

    SameSite(String headerValue) {
        this.headerValue = headerValue;
    }

    /** The exact attribute value to emit in the {@code Set-Cookie} header. */
    public String headerValue() {
        return headerValue;
    }

}
