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

import io.karatelabs.markup.RootResourceResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the OAuth response_mode=form_post failure mode.
 * <p>
 * Microsoft / Azure AD via MSAL4J &gt;=1.18 deprecates {@code ResponseMode.QUERY}
 * and force-overrides it to {@code FORM_POST}. The IdP then POSTs the OAuth
 * callback (code+state in form body) cross-site to the configured redirect_uri.
 * Two things break with the framework defaults:
 * <ol>
 *   <li>Browsers drop {@code SameSite=Lax} cookies on cross-site top-level
 *       POSTs — the session cookie set during signin initiation does not come
 *       back with the IdP's POST. Fix: opt in to
 *       {@code sessionSameSite(SameSite.NONE)}.</li>
 *   <li>Even if the cookie does come back, the framework's CSRF middleware
 *       returns 403 because the IdP doesn't send our CSRF token. Fix: register
 *       the callback path via {@code csrfExemptPaths(...)} — the OAuth
 *       {@code state} parameter validation is the CSRF defense for that path.</li>
 * </ol>
 * <p>
 * Tests use minimal {@link InMemoryTestHarness} setups built per-test so each
 * scenario can configure {@link ServerConfig} differently.
 */
class SameSiteAndCsrfExemptTest {

    private static InMemoryTestHarness harness(ServerConfig config) {
        RootResourceResolver resolver = new RootResourceResolver("classpath:demo");
        return new InMemoryTestHarness(new ServerRequestHandler(config, resolver));
    }

    private static ServerConfig baseConfig() {
        return new ServerConfig()
                .sessionStore(new InMemorySessionStore())
                .sessionExpirySeconds(600)
                .apiPrefix("/api/")
                .staticPrefix("/pub/")
                .devMode(true);
    }

    private static String setCookie(HttpResponse response) {
        return response.getHeader("Set-Cookie");
    }

    // =================================================================================================================
    // SameSite attribute on the session cookie
    // =================================================================================================================

    @Test
    void testSessionCookieDefaultsToSameSiteLax() {
        // The framework default — works for OAuth response_mode=query (the IdP
        // redirects back with a 302 GET) and for ordinary in-app form posts.
        InMemoryTestHarness h = harness(baseConfig().csrfEnabled(false));

        HttpResponse response = h.get("/api/session?action=init");

        String cookie = setCookie(response);
        assertNotNull(cookie, "session init must Set-Cookie");
        assertTrue(cookie.contains("SameSite=Lax"), "default should be SameSite=Lax: " + cookie);
        // dev mode → no Secure flag (so localhost http works)
        assertFalse(cookie.contains("Secure"), "Secure not set in dev mode for Lax: " + cookie);
    }

    @Test
    void testSessionSameSiteNoneEmitsNoneAndSecure() {
        // Apps that expect cross-site POST callbacks (OAuth form_post, federated
        // signin) opt in to None. Browsers reject SameSite=None cookies that
        // lack Secure, so the framework adds Secure even in dev mode.
        InMemoryTestHarness h = harness(baseConfig()
                .csrfEnabled(false)
                .sessionSameSite(SameSite.NONE));

        HttpResponse response = h.get("/api/session?action=init");

        String cookie = setCookie(response);
        assertNotNull(cookie);
        assertTrue(cookie.contains("SameSite=None"), "should emit SameSite=None: " + cookie);
        assertTrue(cookie.contains("Secure"),
                "SameSite=None requires Secure even in dev mode: " + cookie);
        assertFalse(cookie.contains("SameSite=Lax"), "must not also emit Lax: " + cookie);
    }

    @Test
    void testSessionSameSiteStrictEmitsStrict() {
        // Strict is supported for callers who explicitly want the maximum
        // protection and accept that OAuth callbacks won't work.
        InMemoryTestHarness h = harness(baseConfig()
                .csrfEnabled(false)
                .sessionSameSite(SameSite.STRICT));

        HttpResponse response = h.get("/api/session?action=init");

        String cookie = setCookie(response);
        assertNotNull(cookie);
        assertTrue(cookie.contains("SameSite=Strict"), "should emit SameSite=Strict: " + cookie);
    }

    @Test
    void testSessionSameSiteNullDefaultsToLax() {
        // Defensive: passing null shouldn't NPE — falls back to the safe default.
        InMemoryTestHarness h = harness(baseConfig()
                .csrfEnabled(false)
                .sessionSameSite(null));

        HttpResponse response = h.get("/api/session?action=init");

        String cookie = setCookie(response);
        assertNotNull(cookie);
        assertTrue(cookie.contains("SameSite=Lax"), "null should fall back to Lax: " + cookie);
    }

    // =================================================================================================================
    // csrfExemptPaths skips CSRF validation
    // =================================================================================================================

    @Test
    void testCsrfExemptPathSkipsValidationForCrossSitePost() {
        // Reproduces the OAuth form_post case: a POST arrives with the session
        // cookie attached (SameSite=None made that possible) but with no
        // _csrf token in the body — only `code` and `state` from the IdP.
        // Without the exemption, validateCsrf would 403 the request before
        // the template runs and the OAuth callback handler never sees the body.
        InMemoryTestHarness h = harness(baseConfig()
                .sessionSameSite(SameSite.NONE)
                .csrfExemptPaths("/api/session"));

        // 1. Establish a real (non-temporary) session so CSRF middleware would
        //    normally fire on subsequent unsafe requests.
        HttpResponse init = h.get("/api/session?action=init");
        String cookie = setCookie(init);
        assertNotNull(cookie);
        String sessionCookie = cookie.split(";")[0]; // "karate.sid=xyz"

        // 2. POST cross-site-style: session cookie attached, no CSRF token.
        //    With the exemption, the request reaches the API handler (200).
        HttpResponse post = h.request()
                .path("/api/session?action=log&message=oauth-callback")
                .header("Cookie", sessionCookie)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("code=abc123&state=xyz789")
                .post();

        assertEquals(200, post.getStatus(),
                "csrfExemptPaths should let the POST through; got body: " + post.getBodyString());
    }

    @Test
    void testCsrfStillEnforcedOnNonExemptPaths() {
        // Same setup as above but POST a different path — CSRF still applies
        // and we should get a 403. Confirms the exemption is path-scoped, not
        // a global escape hatch.
        InMemoryTestHarness h = harness(baseConfig()
                .sessionSameSite(SameSite.NONE)
                .csrfExemptPaths("/some/other/path"));

        HttpResponse init = h.get("/api/session?action=init");
        String sessionCookie = setCookie(init).split(";")[0];

        HttpResponse post = h.request()
                .path("/api/session?action=set&key=x&value=y")
                .header("Cookie", sessionCookie)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("")
                .post();

        assertEquals(403, post.getStatus(),
                "non-exempt path must still 403 without a CSRF token; got body: " + post.getBodyString());
    }

    @Test
    void testCsrfExemptPathExactMatchNotPrefix() {
        // The exemption is exact-match (matches rawPaths semantics). A path
        // with a trailing segment must not inherit the parent's exemption.
        InMemoryTestHarness h = harness(baseConfig()
                .sessionSameSite(SameSite.NONE)
                .csrfExemptPaths("/api"));

        HttpResponse init = h.get("/api/session?action=init");
        String sessionCookie = setCookie(init).split(";")[0];

        HttpResponse post = h.request()
                .path("/api/session?action=set&key=x&value=y")
                .header("Cookie", sessionCookie)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("")
                .post();

        assertEquals(403, post.getStatus(),
                "/api exemption must not cover /api/session (exact match only)");
    }

    // =================================================================================================================
    // Cookie clear on explicit context.close() (signout)
    // =================================================================================================================

    @Test
    void testExplicitCloseEmitsCookieClear() {
        // signout flow: a template calls context.close(), which deletes the
        // server-side session and sets ctx.session=null. saveSession then sees
        // wasExplicitlyClosed()==true and emits Set-Cookie with Max-Age=0 so
        // the browser drops its stale cookie too. Without this, the cookie
        // lingers; the user is signed out from the app's view but a security
        // audit can't tell signout from "tab still open with stale cookie."
        InMemoryTestHarness h = harness(baseConfig()
                .csrfEnabled(false)
                .sessionSameSite(SameSite.NONE));

        // Establish a real (non-temporary) session so close() has something
        // to delete — context.close() with no session would short-circuit.
        HttpResponse init = h.get("/api/session?action=init");
        String sessionCookie = setCookie(init).split(";")[0];

        // Sanity: before close, the next request still gets the same session.
        assertNotNull(sessionCookie);

        // Trigger context.close() via the demo's action=close handler.
        HttpResponse close = h.request()
                .path("/api/session?action=close")
                .header("Cookie", sessionCookie)
                .get();

        String clearCookie = setCookie(close);
        assertNotNull(clearCookie, "explicit close must emit Set-Cookie clear: " + close.getBodyString());
        assertTrue(clearCookie.startsWith("karate.sid=;"),
                "cookie must be cleared (empty value): " + clearCookie);
        assertTrue(clearCookie.contains("Max-Age=0"),
                "cookie must include Max-Age=0 to expire immediately: " + clearCookie);
        // Match attributes so the browser actually drops the cookie.
        assertTrue(clearCookie.contains("SameSite=None"),
                "clear must mirror SameSite of original cookie: " + clearCookie);
        assertTrue(clearCookie.contains("Secure"),
                "clear must include Secure (None requires it): " + clearCookie);
    }

    @Test
    void testNoSessionRequestDoesNotEmitClear() {
        // A request that never had a session and didn't call close() must NOT
        // emit a clear (would noisily reset cookies on every static-ish request).
        InMemoryTestHarness h = harness(baseConfig().csrfEnabled(false));

        HttpResponse response = h.get("/api/items"); // no session-touching action

        String setCookie = setCookie(response);
        // Either no Set-Cookie at all, or a normal save (not a Max-Age=0 clear).
        if (setCookie != null) {
            assertFalse(setCookie.contains("Max-Age=0"),
                    "non-close request must not emit cookie clear: " + setCookie);
        }
    }

    @Test
    void testCsrfExemptPathsNullClearsExemptions() {
        // Defensive: setting null/empty must clear, not throw NPE.
        InMemoryTestHarness h = harness(baseConfig()
                .csrfExemptPaths("/foo")
                .csrfExemptPaths((String[]) null));

        HttpResponse init = h.get("/api/session?action=init");
        String sessionCookie = setCookie(init).split(";")[0];

        HttpResponse post = h.request()
                .path("/foo")
                .header("Cookie", sessionCookie)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("")
                .post();

        // /foo is no longer exempt (cleared) AND has no CSRF token → 403.
        // (The path itself doesn't exist, but CSRF runs before routing.)
        assertEquals(403, post.getStatus());
    }

}
