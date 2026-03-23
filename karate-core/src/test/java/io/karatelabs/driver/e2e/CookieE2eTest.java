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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for cookie management.
 */
class CookieE2eTest extends DriverTestBase {

    @BeforeEach
    void setup() {
        driver.clearCookies();
        driver.setUrl(testUrl("/"));
    }

    @Test
    void testSetAndGetCookie() {
        driver.cookie(Map.of(
                "name", "test_cookie",
                "value", "hello123",
                "domain", "host.testcontainers.internal"
        ));

        Map<String, Object> cookie = driver.cookie("test_cookie");
        assertNotNull(cookie);
        assertEquals("test_cookie", cookie.get("name"));
        assertEquals("hello123", cookie.get("value"));
    }

    @Test
    void testGetAllCookies() {
        driver.cookie(Map.of(
                "name", "cookie1",
                "value", "value1",
                "domain", "host.testcontainers.internal"
        ));
        driver.cookie(Map.of(
                "name", "cookie2",
                "value", "value2",
                "domain", "host.testcontainers.internal"
        ));

        List<Map<String, Object>> cookies = driver.getCookies();
        assertTrue(cookies.size() >= 2);

        // Check both cookies exist
        boolean found1 = cookies.stream().anyMatch(c -> "cookie1".equals(c.get("name")));
        boolean found2 = cookies.stream().anyMatch(c -> "cookie2".equals(c.get("name")));
        assertTrue(found1);
        assertTrue(found2);
    }

    @Test
    void testDeleteCookie() {
        driver.cookie(Map.of(
                "name", "to_delete",
                "value", "delete_me",
                "domain", "host.testcontainers.internal"
        ));

        // Verify it exists
        assertNotNull(driver.cookie("to_delete"));

        // Delete it
        driver.deleteCookie("to_delete");

        // Verify it's gone
        assertNull(driver.cookie("to_delete"));
    }

    @Test
    void testClearAllCookies() {
        driver.cookie(Map.of(
                "name", "clear1",
                "value", "v1",
                "domain", "host.testcontainers.internal"
        ));
        driver.cookie(Map.of(
                "name", "clear2",
                "value", "v2",
                "domain", "host.testcontainers.internal"
        ));

        driver.clearCookies();

        List<Map<String, Object>> cookies = driver.getCookies();
        assertTrue(cookies.isEmpty() || cookies.stream().noneMatch(c ->
            "clear1".equals(c.get("name")) || "clear2".equals(c.get("name"))));
    }

    @Test
    void testCookieNotFound() {
        Map<String, Object> cookie = driver.cookie("nonexistent");
        assertNull(cookie);
    }

}
