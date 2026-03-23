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

import io.karatelabs.driver.e2e.support.DriverTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for browser navigation.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NavigationE2eTest extends DriverTestBase {

    @Test
    @Order(1)
    void testScriptExecutionBasic() {
        // Test basic script execution (works without navigation)
        Object result = driver.script("1 + 1");
        assertEquals(2, ((Number) result).intValue());

        result = driver.script("'hello' + ' world'");
        assertEquals("hello world", result);
    }

    @Test
    @Order(2)
    void testScriptReturnsObject() {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) driver.script("({name: 'test', value: 42})");
        assertEquals("test", result.get("name"));
        assertEquals(42, ((Number) result.get("value")).intValue());
    }

    @Test
    @Order(3)
    void testScreenshot() {
        // Take screenshot of blank page
        byte[] screenshot = driver.screenshot();

        assertNotNull(screenshot);
        assertTrue(screenshot.length > 0, "Screenshot should have content");

        // PNG files start with specific bytes
        assertEquals((byte) 0x89, screenshot[0]);
        assertEquals((byte) 0x50, screenshot[1]); // P
        assertEquals((byte) 0x4E, screenshot[2]); // N
        assertEquals((byte) 0x47, screenshot[3]); // G
    }

    @Test
    @Order(10)
    void testNavigateToIndexPage() {
        // Navigate to index page
        String url = testUrl("/");
        logger.info("navigating to: {}", url);
        driver.setUrl(url);

        // Verify title
        assertEquals("Karate Driver Test", driver.getTitle());

        // Verify URL contains index
        String currentUrl = driver.getUrl();
        assertTrue(currentUrl.contains("index.html") || currentUrl.endsWith("/"),
                "URL should be index page: " + currentUrl);

        // Verify JS variable was set
        Object testValue = driver.script("window.testValue");
        assertEquals(42, ((Number) testValue).intValue());

        // Verify page content via JS
        String description = (String) driver.script("document.getElementById('description').textContent");
        assertTrue(description.contains("Welcome"));
    }

    @Test
    @Order(11)
    void testNavigateToNavigationPage() {
        // Navigate to navigation test page
        driver.setUrl(testUrl("/navigation"));

        // Verify title
        assertEquals("Navigation Test", driver.getTitle());

        // Verify page content
        String h1Text = (String) driver.script("document.querySelector('h1').textContent");
        assertEquals("Navigation Test Page", h1Text);
    }

    @Test
    @Order(12)
    void testNavigateBetweenPages() {
        // Start at index
        driver.setUrl(testUrl("/"));
        assertEquals("Karate Driver Test", driver.getTitle());

        // Navigate to wait page
        driver.setUrl(testUrl("/wait"));
        assertEquals("Wait Test", driver.getTitle());

        // Navigate to input page
        driver.setUrl(testUrl("/input"));
        assertEquals("Input Test", driver.getTitle());

        // Navigate back to index
        driver.setUrl(testUrl("/"));
        assertEquals("Karate Driver Test", driver.getTitle());
    }

    @Test
    @Order(13)
    void testGetUrlAfterNavigation() {
        driver.setUrl(testUrl("/navigation"));
        String url = driver.getUrl();
        assertTrue(url.contains("/navigation"), "URL should contain /navigation: " + url);
    }

    @Test
    @Order(14)
    void testScreenshotAfterNavigation() {
        // Navigate to a page with content
        driver.setUrl(testUrl("/"));

        // Take screenshot
        byte[] screenshot = driver.screenshot();

        assertNotNull(screenshot);
        assertTrue(screenshot.length > 1000, "Screenshot should have substantial content after navigation");

        // Verify PNG format
        assertEquals((byte) 0x89, screenshot[0]);
        assertEquals((byte) 0x50, screenshot[1]); // P
        assertEquals((byte) 0x4E, screenshot[2]); // N
        assertEquals((byte) 0x47, screenshot[3]); // G
    }

}
