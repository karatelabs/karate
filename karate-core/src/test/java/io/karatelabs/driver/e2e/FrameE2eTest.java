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

import io.karatelabs.driver.cdp.*;

import io.karatelabs.driver.DriverException;
import io.karatelabs.driver.e2e.support.DriverTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for frame switching.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FrameE2eTest extends DriverTestBase {

    @BeforeEach
    void navigateToIframePage() {
        // Switch back to main frame and navigate
        driver.switchFrame(null);
        driver.setUrl(testUrl("/iframe"));
    }

    // ========== Main Frame Operations ==========

    @Test
    @Order(1)
    void testMainFrameContent() {
        // Verify we're in the main frame
        assertNull(driver.getCurrentFrame());

        // Check main frame content
        String title = driver.text("h1");
        assertEquals("IFrame Test Page", title);

        String mainText = driver.text("#main-text");
        assertEquals("This content is in the main frame.", mainText);
    }

    @Test
    @Order(2)
    void testMainFrameVariable() {
        // Main frame has window.mainValue = 'main-data'
        Object value = driver.script("window.mainValue");
        assertEquals("main-data", value);
    }

    @Test
    @Order(3)
    void testMainFrameButton() {
        driver.click("#main-btn");

        // Wait for result
        driver.waitForText("#result", "Main button clicked!");
        String resultText = driver.text("#result");
        assertEquals("Main button clicked!", resultText);
    }

    // ========== Switch Frame by Index ==========

    @Test
    @Order(10)
    void testSwitchFrameByIndex() {
        // Switch to first (and only) iframe by index
        driver.switchFrame(0);

        // Verify we're in the frame
        Map<String, Object> frame = driver.getCurrentFrame();
        assertNotNull(frame);
        assertEquals("testFrame", frame.get("name"));

        // Check frame content
        String frameText = driver.text("#frame-text");
        assertEquals("This content is inside the iframe.", frameText);
    }

    @Test
    @Order(11)
    void testFrameVariableByIndex() {
        driver.switchFrame(0);

        // Frame has window.frameValue = 'iframe-data'
        Object value = driver.script("window.frameValue");
        assertEquals("iframe-data", value);
    }

    @Test
    @Order(12)
    void testFrameButtonByIndex() {
        driver.switchFrame(0);

        driver.click("#frame-btn");

        // Wait for result within frame
        driver.waitForText("#frame-result", "Frame button clicked!");
        String resultText = driver.text("#frame-result");
        assertEquals("Frame button clicked!", resultText);
    }

    @Test
    @Order(13)
    void testInvalidFrameIndex() {
        // Try to switch to non-existent frame
        assertThrows(DriverException.class, () -> driver.switchFrame(999));
    }

    // ========== Switch Frame by Locator ==========

    @Test
    @Order(20)
    void testSwitchFrameByIdLocator() {
        // Switch by CSS ID selector
        driver.switchFrame("#test-frame");

        // Verify we're in the frame
        Map<String, Object> frame = driver.getCurrentFrame();
        assertNotNull(frame);

        // Check frame content
        String frameText = driver.text("#frame-text");
        assertEquals("This content is inside the iframe.", frameText);
    }

    @Test
    @Order(21)
    void testSwitchFrameByNameLocator() {
        // Switch by name attribute selector
        driver.switchFrame("iframe[name='testFrame']");

        // Verify we're in the frame
        Map<String, Object> frame = driver.getCurrentFrame();
        assertNotNull(frame);
        assertEquals("testFrame", frame.get("name"));
    }

    @Test
    @Order(22)
    void testFrameContentByLocator() {
        driver.switchFrame("#test-frame");

        // Verify frame content
        String heading = driver.text("h2");
        assertEquals("Frame Content", heading);

        assertTrue(driver.exists("#frame-btn"));
        assertTrue(driver.exists("#frame-result"));
    }

    @Test
    @Order(23)
    void testInvalidFrameLocator() {
        // Try to switch to non-existent frame
        assertThrows(DriverException.class, () -> driver.switchFrame("#nonexistent-frame"));
    }

    @Test
    @Order(24)
    void testNonFrameLocator() {
        // Try to switch to a non-frame element
        assertThrows(DriverException.class, () -> driver.switchFrame("#main-btn"));
    }

    // ========== Switch Back to Main Frame ==========

    @Test
    @Order(30)
    void testSwitchBackToMainFrame() {
        // Switch to iframe
        driver.switchFrame(0);
        assertNotNull(driver.getCurrentFrame());

        // Verify we're in the frame
        Object frameValue = driver.script("window.frameValue");
        assertEquals("iframe-data", frameValue);

        // Switch back to main frame
        driver.switchFrame(null);
        assertNull(driver.getCurrentFrame());

        // Verify we're back in main frame
        Object mainValue = driver.script("window.mainValue");
        assertEquals("main-data", mainValue);
    }

    @Test
    @Order(31)
    void testMainFrameContentAfterSwitch() {
        // Switch to iframe
        driver.switchFrame("#test-frame");

        // Switch back
        driver.switchFrame(null);

        // Verify main frame elements accessible
        String title = driver.text("h1");
        assertEquals("IFrame Test Page", title);

        assertTrue(driver.exists("#main-btn"));
        assertTrue(driver.exists("#main-text"));
    }

    // ========== Frame Operations ==========

    @Test
    @Order(40)
    void testElementOperationsInFrame() {
        driver.switchFrame("#test-frame");

        // Test various element operations within frame
        assertTrue(driver.exists("#frame-text"));
        assertTrue(driver.exists("#frame-btn"));

        String text = driver.text("#frame-text");
        assertEquals("This content is inside the iframe.", text);

        String html = driver.html("#frame-text");
        assertTrue(html.contains("This content is inside the iframe."));
    }

    @Test
    @Order(41)
    void testClickAndResultInFrame() {
        driver.switchFrame("#test-frame");

        // Initial state - result should be empty
        String initialResult = driver.text("#frame-result");
        assertEquals("", initialResult);

        // Click button
        driver.click("#frame-btn");

        // Verify result updated
        driver.waitForText("#frame-result", "Frame button clicked!");
    }

    @Test
    @Order(42)
    void testScriptExecutionInFrame() {
        driver.switchFrame("#test-frame");

        // Execute script in frame context
        Object value = driver.script("document.getElementById('frame-text').textContent");
        assertEquals("This content is inside the iframe.", value);
    }

    @Test
    @Order(43)
    void testFrameDoesNotAffectMainFrame() {
        // Perform action in frame
        driver.switchFrame("#test-frame");
        driver.click("#frame-btn");
        driver.waitForText("#frame-result", "Frame button clicked!");

        // Switch back to main frame
        driver.switchFrame(null);

        // Main frame result should still be empty (not affected by frame click)
        String mainResult = driver.text("#result");
        // Initially empty since we didn't click main button
        assertTrue(mainResult.isEmpty() || !mainResult.contains("Frame button"));
    }

    // ========== Multiple Frame Switches ==========

    @Test
    @Order(50)
    void testMultipleSwitches() {
        // Start in main frame
        assertNull(driver.getCurrentFrame());
        assertEquals("main-data", driver.script("window.mainValue"));

        // Switch to frame
        driver.switchFrame(0);
        assertNotNull(driver.getCurrentFrame());
        assertEquals("iframe-data", driver.script("window.frameValue"));

        // Back to main
        driver.switchFrame(null);
        assertNull(driver.getCurrentFrame());
        assertEquals("main-data", driver.script("window.mainValue"));

        // Switch again by locator
        driver.switchFrame("#test-frame");
        assertNotNull(driver.getCurrentFrame());
        assertEquals("iframe-data", driver.script("window.frameValue"));

        // And back
        driver.switchFrame(null);
        assertNull(driver.getCurrentFrame());
    }

}
