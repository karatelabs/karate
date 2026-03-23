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

import io.karatelabs.driver.PageLoadStrategy;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CdpDriverOptionsTest {

    @Test
    void testDefaultValues() {
        CdpDriverOptions options = CdpDriverOptions.builder().build();

        assertEquals(30000, options.getTimeout());
        assertEquals(3, options.getRetryCount());
        assertEquals(500, options.getRetryInterval());
        assertFalse(options.isHeadless());
        assertEquals("localhost", options.getHost());
        assertEquals(0, options.getPort());
        assertNull(options.getExecutable());
        // userDataDir auto-generates a temp sandbox by default
        assertNotNull(options.getUserDataDir());
        assertTrue(options.getUserDataDir().contains("chrome-temp-"));
        assertNull(options.getUserAgent());
        assertTrue(options.isScreenshotOnFailure());
        assertFalse(options.isHighlight());
        assertEquals(3000, options.getHighlightDuration());
        assertEquals(PageLoadStrategy.DOMCONTENT_AND_FRAMES, options.getPageLoadStrategy());
        assertTrue(options.getAddOptions().isEmpty());
        assertNull(options.getWebSocketUrl());
    }

    @Test
    void testBuilderMethods() {
        CdpDriverOptions options = CdpDriverOptions.builder()
                .timeout(60000)
                .retryCount(5)
                .retryInterval(1000)
                .headless(true)
                .host("remote-host")
                .port(9222)
                .executable("/path/to/chrome")
                .userDataDir("/tmp/chrome-data")
                .userAgent("Custom UA")
                .screenshotOnFailure(false)
                .highlight(true)
                .highlightDuration(5000)
                .pageLoadStrategy(PageLoadStrategy.NETWORKIDLE)
                .addOptions(List.of("--disable-gpu", "--no-sandbox"))
                .webSocketUrl("ws://localhost:9222/devtools/page/ABC")
                .build();

        assertEquals(60000, options.getTimeout());
        assertEquals(5, options.getRetryCount());
        assertEquals(1000, options.getRetryInterval());
        assertTrue(options.isHeadless());
        assertEquals("remote-host", options.getHost());
        assertEquals(9222, options.getPort());
        assertEquals("/path/to/chrome", options.getExecutable());
        assertEquals("/tmp/chrome-data", options.getUserDataDir());
        assertEquals("Custom UA", options.getUserAgent());
        assertFalse(options.isScreenshotOnFailure());
        assertTrue(options.isHighlight());
        assertEquals(5000, options.getHighlightDuration());
        assertEquals(PageLoadStrategy.NETWORKIDLE, options.getPageLoadStrategy());
        assertEquals(List.of("--disable-gpu", "--no-sandbox"), options.getAddOptions());
        assertEquals("ws://localhost:9222/devtools/page/ABC", options.getWebSocketUrl());
    }

    @Test
    void testTimeoutDuration() {
        CdpDriverOptions options = CdpDriverOptions.builder()
                .timeout(Duration.ofSeconds(45))
                .build();

        assertEquals(45000, options.getTimeout());
        assertEquals(Duration.ofMillis(45000), options.getTimeoutDuration());
    }

    @Test
    void testAddOptionsSingle() {
        CdpDriverOptions options = CdpDriverOptions.builder()
                .addOption("--disable-gpu")
                .addOption("--no-sandbox")
                .build();

        assertEquals(List.of("--disable-gpu", "--no-sandbox"), options.getAddOptions());
    }

    @Test
    void testFromMapBasic() {
        Map<String, Object> map = Map.of(
                "headless", true,
                "timeout", 60000,
                "port", 9222
        );

        CdpDriverOptions options = CdpDriverOptions.fromMap(map);

        assertTrue(options.isHeadless());
        assertEquals(60000, options.getTimeout());
        assertEquals(9222, options.getPort());
    }

    @Test
    void testFromMapWithStringValues() {
        Map<String, Object> map = Map.of(
                "headless", "true",
                "timeout", "45000",
                "port", "9223"
        );

        CdpDriverOptions options = CdpDriverOptions.fromMap(map);

        assertTrue(options.isHeadless());
        assertEquals(45000, options.getTimeout());
        assertEquals(9223, options.getPort());
    }

    @Test
    void testFromMapWithPageLoadStrategy() {
        Map<String, Object> map = Map.of(
                "pageLoadStrategy", "NETWORKIDLE"
        );

        CdpDriverOptions options = CdpDriverOptions.fromMap(map);

        assertEquals(PageLoadStrategy.NETWORKIDLE, options.getPageLoadStrategy());
    }

    @Test
    void testFromMapWithPageLoadStrategyEnum() {
        Map<String, Object> map = Map.of(
                "pageLoadStrategy", PageLoadStrategy.LOAD
        );

        CdpDriverOptions options = CdpDriverOptions.fromMap(map);

        assertEquals(PageLoadStrategy.LOAD, options.getPageLoadStrategy());
    }

    @Test
    void testFromMapWithAddOptions() {
        Map<String, Object> map = Map.of(
                "addOptions", List.of("--disable-gpu", "--no-sandbox")
        );

        CdpDriverOptions options = CdpDriverOptions.fromMap(map);

        assertEquals(List.of("--disable-gpu", "--no-sandbox"), options.getAddOptions());
    }

    @Test
    void testFromMapNull() {
        CdpDriverOptions options = CdpDriverOptions.fromMap(null);

        // Should use defaults
        assertEquals(30000, options.getTimeout());
        assertFalse(options.isHeadless());
    }

    @Test
    void testFromMapComprehensive() {
        Map<String, Object> map = Map.ofEntries(
                Map.entry("timeout", 50000),
                Map.entry("retryCount", 10),
                Map.entry("retryInterval", 200),
                Map.entry("headless", true),
                Map.entry("host", "chrome-host"),
                Map.entry("port", 9333),
                Map.entry("executable", "/opt/chrome/chrome"),
                Map.entry("userDataDir", "/data/chrome"),
                Map.entry("userAgent", "TestAgent/1.0"),
                Map.entry("screenshotOnFailure", false),
                Map.entry("highlight", true),
                Map.entry("highlightDuration", 2000),
                Map.entry("pageLoadStrategy", "DOMCONTENT"),
                Map.entry("webSocketUrl", "ws://localhost:9333/devtools/page/XYZ")
        );

        CdpDriverOptions options = CdpDriverOptions.fromMap(map);

        assertEquals(50000, options.getTimeout());
        assertEquals(10, options.getRetryCount());
        assertEquals(200, options.getRetryInterval());
        assertTrue(options.isHeadless());
        assertEquals("chrome-host", options.getHost());
        assertEquals(9333, options.getPort());
        assertEquals("/opt/chrome/chrome", options.getExecutable());
        assertEquals("/data/chrome", options.getUserDataDir());
        assertEquals("TestAgent/1.0", options.getUserAgent());
        assertFalse(options.isScreenshotOnFailure());
        assertTrue(options.isHighlight());
        assertEquals(2000, options.getHighlightDuration());
        assertEquals(PageLoadStrategy.DOMCONTENT, options.getPageLoadStrategy());
        assertEquals("ws://localhost:9333/devtools/page/XYZ", options.getWebSocketUrl());
    }

    @Test
    void testAddOptionsImmutable() {
        CdpDriverOptions options = CdpDriverOptions.builder()
                .addOptions(List.of("--disable-gpu"))
                .build();

        // The returned list should be immutable
        assertThrows(UnsupportedOperationException.class, () -> {
            options.getAddOptions().add("--new-option");
        });
    }

}
