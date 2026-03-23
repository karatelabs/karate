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
package io.karatelabs.driver.cdp;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CdpMessageTest {

    @Test
    void testBasicMessage() {
        CdpMessage message = new CdpMessage(null, 1, "Page.enable");
        assertEquals(1, message.getId());
        assertEquals("Page.enable", message.getMethod());
        assertNull(message.getParams());
    }

    @Test
    void testMessageWithParams() {
        CdpMessage message = new CdpMessage(null, 2, "Page.navigate")
                .param("url", "https://example.com");

        assertEquals(2, message.getId());
        assertEquals("Page.navigate", message.getMethod());
        assertEquals("https://example.com", message.getParams().get("url"));
    }

    @Test
    void testMessageWithMultipleParams() {
        CdpMessage message = new CdpMessage(null, 3, "Runtime.evaluate")
                .param("expression", "document.title")
                .param("returnByValue", true);

        assertEquals("document.title", message.getParams().get("expression"));
        assertEquals(true, message.getParams().get("returnByValue"));
    }

    @Test
    void testMessageWithParamsMap() {
        CdpMessage message = new CdpMessage(null, 4, "Page.captureScreenshot")
                .params(Map.of("format", "png", "quality", 80));

        assertEquals("png", message.getParams().get("format"));
        assertEquals(80, message.getParams().get("quality"));
    }

    @Test
    void testTimeout() {
        CdpMessage message = new CdpMessage(null, 5, "Page.navigate")
                .timeout(Duration.ofSeconds(60));

        assertEquals(Duration.ofSeconds(60), message.getTimeout());
    }

    @Test
    void testTimeoutMillis() {
        CdpMessage message = new CdpMessage(null, 6, "Page.navigate")
                .timeout(5000);

        assertEquals(Duration.ofMillis(5000), message.getTimeout());
    }

    @Test
    void testSessionId() {
        CdpMessage message = new CdpMessage(null, 7, "Runtime.evaluate")
                .sessionId("session-123");

        assertEquals("session-123", message.getSessionId());
    }

    @Test
    void testToMap() {
        CdpMessage message = new CdpMessage(null, 8, "Page.navigate")
                .param("url", "https://example.com")
                .sessionId("sess-1");

        Map<String, Object> map = message.toMap();

        assertEquals(8, map.get("id"));
        assertEquals("Page.navigate", map.get("method"));
        assertEquals("sess-1", map.get("sessionId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) map.get("params");
        assertEquals("https://example.com", params.get("url"));
    }

    @Test
    void testToMapWithoutParams() {
        CdpMessage message = new CdpMessage(null, 9, "Page.enable");
        Map<String, Object> map = message.toMap();

        assertEquals(9, map.get("id"));
        assertEquals("Page.enable", map.get("method"));
        assertFalse(map.containsKey("params"));
        assertFalse(map.containsKey("sessionId"));
    }

    @Test
    void testToJson() {
        CdpMessage message = new CdpMessage(null, 10, "Page.navigate")
                .param("url", "https://example.com");

        String json = message.toJson();

        // Verify JSON structure - forward slashes should NOT be escaped
        assertTrue(json.contains("\"id\":10"), "Should have id");
        assertTrue(json.contains("\"method\":\"Page.navigate\""), "Should have method");
        assertTrue(json.contains("https://example.com"), "URL should have unescaped slashes");
        assertFalse(json.contains("\\/"), "Forward slashes should not be escaped");
    }

    @Test
    void testToString() {
        CdpMessage message = new CdpMessage(null, 11, "Page.enable");
        assertEquals("CdpMessage[11: Page.enable]", message.toString());
    }

}
