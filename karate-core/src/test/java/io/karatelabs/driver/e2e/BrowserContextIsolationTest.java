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
package io.karatelabs.driver.e2e;

import io.karatelabs.driver.PageLoadStrategy;
import io.karatelabs.driver.cdp.CdpDriver;
import io.karatelabs.driver.cdp.CdpDriverOptions;
import io.karatelabs.driver.e2e.support.SharedChromeContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the isolation contract that makes it safe to run pooled drivers in parallel
 * against ONE browser.
 * <p>
 * Slots used to be a bare tab in the browser's DEFAULT context. Tabs there share a
 * single cookie jar, so the isolation was an illusion, and the pooled reset made it
 * actively destructive: {@code clearCookies()} is {@code Network.clearBrowserCookies},
 * which is browser-context-wide, so every scenario acquiring a driver wiped the cookies
 * of every scenario running in parallel with it. No amount of per-tab clearing fixes
 * that — there is only one jar to clear — which is why each driver now owns an incognito
 * context.
 * </p>
 *
 * @see CdpDriver#connectNewContext
 */
class BrowserContextIsolationTest {

    private static SharedChromeContainer shared;
    private static CdpDriver alice;
    private static CdpDriver bob;

    @BeforeAll
    static void setup() {
        shared = SharedChromeContainer.getInstance();
        String browserUrl = shared.getChrome().getBrowserCdpUrl();
        alice = CdpDriver.connectNewContext(browserUrl, options());
        bob = CdpDriver.connectNewContext(browserUrl, options());
    }

    private static CdpDriverOptions options() {
        return CdpDriverOptions.builder()
                .timeout(30000)
                .pageLoadStrategy(PageLoadStrategy.DOMCONTENT_AND_FRAMES)
                .build();
    }

    @AfterAll
    static void cleanup() {
        if (alice != null) {
            alice.quit();
        }
        if (bob != null) {
            bob.quit();
        }
    }

    private static String url(String path) {
        return shared.getHostAccessUrl() + path;
    }

    private static void setCookie(CdpDriver driver, String name, String value) {
        driver.script("document.cookie = '" + name + "=" + value + "; path=/'");
    }

    private static String readCookie(CdpDriver driver) {
        return (String) driver.script("document.cookie");
    }

    @Test
    void testDriversInSeparateContextsDoNotShareCookies() {
        alice.setUrl(url("/index.html"));
        bob.setUrl(url("/index.html"));

        setCookie(alice, "who", "alice");
        assertTrue(readCookie(alice).contains("who=alice"), "alice must see her own cookie");
        assertFalse(readCookie(bob).contains("who=alice"),
                "bob must NOT see alice's cookie — separate contexts, separate jars");
    }

    /**
     * The regression that mattered: this is exactly what PooledDriverProvider.resetDriver()
     * does on every acquire, so in the shared default context it fired constantly and
     * silently logged out whatever scenario happened to be running in parallel.
     */
    @Test
    void testClearCookiesDoesNotWipeAnotherDriversCookies() {
        alice.setUrl(url("/index.html"));
        bob.setUrl(url("/index.html"));

        setCookie(alice, "session", "keep-me");
        assertTrue(readCookie(alice).contains("session=keep-me"));

        bob.clearCookies();

        assertTrue(readCookie(alice).contains("session=keep-me"),
                "another driver's clearCookies() must not touch alice's jar — this is the"
                        + " pooled reset, and it runs while other scenarios are mid-flight");
    }

    /**
     * {@code Target.getTargets} is browser-wide, so tab enumeration has to be filtered by
     * browser context or a driver counts its siblings' tabs. That is what forced
     * tab-switch.feature to be serialized: its assertions are of the form
     * {@code getPages().length == initialCount + 1}, which any parallel slot opening a tab
     * would break.
     */
    @Test
    void testTabEnumerationIsScopedToOwnContext() {
        alice.setUrl(url("/tab-main"));
        bob.setUrl(url("/tab-main"));

        int aliceBefore = alice.getPages().size();
        int bobBefore = bob.getPages().size();

        alice.click("#open-tab");

        // the new tab shows up asynchronously — poll alice rather than sleep
        long deadline = System.currentTimeMillis() + 5000;
        while (alice.getPages().size() == aliceBefore && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }

        assertEquals(aliceBefore + 1, alice.getPages().size(), "alice must see the tab she opened");
        assertEquals(bobBefore, bob.getPages().size(),
                "bob must NOT see alice's new tab — an unscoped count is what made the tab"
                        + " scenarios race each other");
    }
}
