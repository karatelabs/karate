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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CdpEventTest {

    @Test
    void testBasicEvent() {
        Map<String, Object> raw = Map.of(
                "method", "Page.loadEventFired",
                "params", Map.of("timestamp", 12345.67)
        );
        CdpEvent event = new CdpEvent(raw);

        assertEquals("Page.loadEventFired", event.getMethod());
        assertNotNull(event.getParams());
    }

    @Test
    void testGetParams() {
        Map<String, Object> raw = Map.of(
                "method", "Page.frameNavigated",
                "params", Map.of(
                        "frame", Map.of(
                                "id", "ABC123",
                                "url", "https://example.com"
                        )
                )
        );
        CdpEvent event = new CdpEvent(raw);

        assertEquals("ABC123", event.get("frame.id"));
        assertEquals("https://example.com", event.get("frame.url"));
    }

    @Test
    void testGetAsString() {
        Map<String, Object> raw = Map.of(
                "method", "Page.domContentEventFired",
                "params", Map.of("timestamp", 12345)
        );
        CdpEvent event = new CdpEvent(raw);

        assertEquals("12345", event.getAsString("timestamp"));
    }

    @Test
    void testGetAsInt() {
        Map<String, Object> raw = Map.of(
                "method", "Network.requestWillBeSent",
                "params", Map.of("requestId", 42)
        );
        CdpEvent event = new CdpEvent(raw);

        assertEquals(42, event.getAsInt("requestId"));
    }

    @Test
    void testGetAsBoolean() {
        Map<String, Object> raw = Map.of(
                "method", "Page.javascriptDialogOpening",
                "params", Map.of("hasBrowserHandler", true)
        );
        CdpEvent event = new CdpEvent(raw);

        assertTrue(event.getAsBoolean("hasBrowserHandler"));
    }

    @Test
    void testEventWithSessionId() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("method", "Runtime.consoleAPICalled");
        raw.put("sessionId", "session-456");
        raw.put("params", Map.of("type", "log"));

        CdpEvent event = new CdpEvent(raw);

        assertEquals("session-456", event.getSessionId());
        assertEquals("log", event.get("type"));
    }

    @Test
    void testMissingParamsReturnsNull() {
        Map<String, Object> raw = Map.of(
                "method", "Page.loadEventFired",
                "params", Map.of()
        );
        CdpEvent event = new CdpEvent(raw);

        assertNull(event.get("nonexistent"));
        assertNull(event.get("nested.missing.path"));
    }

    @Test
    void testEventWithNullParams() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("method", "Target.targetCreated");

        CdpEvent event = new CdpEvent(raw);

        assertEquals("Target.targetCreated", event.getMethod());
        assertNull(event.getParams());
        assertNull(event.get("anything"));
    }

    @Test
    void testToString() {
        Map<String, Object> raw = Map.of(
                "method", "Page.loadEventFired",
                "params", Map.of()
        );
        CdpEvent event = new CdpEvent(raw);

        assertEquals("CdpEvent[Page.loadEventFired]", event.toString());
    }

    @Test
    void testGetRaw() {
        Map<String, Object> raw = Map.of(
                "method", "Test.event",
                "params", Map.of("value", "test")
        );
        CdpEvent event = new CdpEvent(raw);

        assertEquals(raw, event.getRaw());
    }

}
