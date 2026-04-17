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

import io.karatelabs.driver.e2e.support.DriverTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for tab tracking: target="_blank" clicks, window.open(),
 * {@code drainOpenedTargets()} event queue, and {@code switchPageById()}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TabE2eTest extends DriverTestBase {

    @BeforeEach
    void resetToMainTab() {
        // Drain any events from previous tests so each test starts empty
        driver.drainOpenedTargets();
        driver.setUrl(testUrl("/tab-main"));
    }

    @Test
    @Order(1)
    void testTargetBlankLinkIsDrained() {
        // Sanity: queue starts empty after the navigate
        assertTrue(driver.drainOpenedTargets().isEmpty(),
                "Fresh nav should not leave anything in the opened-targets queue");

        driver.click("#open-tab");

        List<Map<String, Object>> opened = drainWithWait(2000);
        assertEquals(1, opened.size(), "Exactly one new tab should be reported");
        Map<String, Object> entry = opened.get(0);
        assertNotNull(entry.get("targetId"), "drain entry must carry targetId");
        // The URL field is best-effort — Target.targetCreated can fire with a blank
        // URL before Target.targetInfoChanged populates it. Verify via driver.getUrl()
        // after switching, which is the real contract.

        // Switch to the new tab by targetId and verify we're really there
        driver.switchPageById((String) entry.get("targetId"));
        assertTrue(driver.getUrl().contains("tab-new"),
                "after switchPageById, getUrl should be the new tab");
        assertEquals("Tab New Page", driver.getTitle());

        // Close the new tab so the test leaves the driver on the main tab
        driver.close();
    }

    @Test
    @Order(2)
    void testDrainIsEmptyWhenNoTabsOpened() {
        assertTrue(driver.drainOpenedTargets().isEmpty());
        // A navigation inside the same tab must not look like a new tab
        driver.setUrl(testUrl("/navigation"));
        // Give any stray events a chance to arrive — none should.
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        assertTrue(driver.drainOpenedTargets().isEmpty(),
                "Navigating the same tab must not enqueue an opened-target event");
    }

    @Test
    @Order(3)
    void testWindowOpenFiresTargetCreated() {
        driver.script("window.open('" + testUrl("/tab-new") + "', '_blank')");

        List<Map<String, Object>> opened = drainWithWait(2000);
        assertFalse(opened.isEmpty(), "window.open() should enqueue at least one target");
        Map<String, Object> latest = opened.get(opened.size() - 1);
        driver.switchPageById((String) latest.get("targetId"));
        assertTrue(driver.getUrl().contains("tab-new"),
                "after switchPageById, getUrl should be the window.open target");

        // Cleanup
        driver.close();
    }

    /**
     * Poll drainOpenedTargets() until it returns something or the timeout fires.
     * Accumulates across polls because draining empties the queue.
     */
    private List<Map<String, Object>> drainWithWait(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        java.util.List<Map<String, Object>> accum = new java.util.ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            accum.addAll(driver.drainOpenedTargets());
            if (!accum.isEmpty()) return accum;
            try { Thread.sleep(50); } catch (InterruptedException ignored) { return accum; }
        }
        return accum;
    }
}
