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

import io.karatelabs.driver.Keys;
import io.karatelabs.driver.e2e.support.DriverTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for login flow - validates form input, button click, and navigation.
 */
class LoginE2eTest extends DriverTestBase {

    @BeforeEach
    void setup() {
        driver.setUrl(testUrl("/login"));
    }

    @Test
    void testLoginPageLoads() {
        assertEquals("Login", driver.getTitle());
        assertTrue(driver.exists("#username"));
        assertTrue(driver.exists("#password"));
        assertTrue(driver.exists("#login-btn"));
    }

    @Test
    void testSuccessfulLoginWithClick() {
        // Fill in credentials
        driver.input("#username", "admin");
        driver.input("#password", "admin123");

        // Click login button
        driver.click("#login-btn");

        // Should navigate to dashboard
        driver.waitFor("#username-display");
        assertEquals("Dashboard", driver.getTitle());
        assertEquals("admin", driver.text("#username-display"));
    }

    @Test
    void testSuccessfulLoginWithKeyboardTyping() {
        // Fill in credentials using keyboard (not driver.input)
        driver.focus("#username");
        driver.keys().type("user");

        driver.focus("#password");
        driver.keys().type("password");

        // Click to submit (Enter key form submit is browser-dependent)
        driver.click("#login-btn");

        // Should navigate to dashboard
        driver.waitFor("#username-display");
        assertEquals("Dashboard", driver.getTitle());
        assertEquals("user", driver.text("#username-display"));
    }

    @Test
    void testSuccessfulLoginWithTabNavigation() {
        // Focus username and type
        driver.focus("#username");
        driver.keys().type("test");

        // Tab to password
        driver.keys().press(Keys.TAB);
        driver.keys().type("test");

        // Tab to button and click (more reliable than Enter)
        driver.click("#login-btn");

        // Should navigate to dashboard
        driver.waitFor("#username-display");
        assertEquals("Dashboard", driver.getTitle());
    }

    @Test
    void testSuccessfulLoginWithEnterKey() {
        // Fill in credentials using keyboard
        driver.focus("#username");
        driver.keys().type("user");

        driver.focus("#password");
        driver.keys().type("password");

        // Press Enter to submit form
        driver.keys().press(Keys.ENTER);

        // Should navigate to dashboard
        driver.waitFor("#username-display");
        assertEquals("Dashboard", driver.getTitle());
        assertEquals("user", driver.text("#username-display"));
    }

    @Test
    void testFailedLogin() {
        // Fill in wrong credentials
        driver.input("#username", "admin");
        driver.input("#password", "wrongpassword");

        // Click login
        driver.click("#login-btn");

        // Should show error and stay on login page
        driver.waitFor("#error.visible");
        assertEquals("Login", driver.getTitle());
        assertTrue(driver.text("#error").contains("Invalid"));
    }

    @Test
    void testLogout() {
        // Login first
        driver.input("#username", "admin");
        driver.input("#password", "admin123");
        driver.click("#login-btn");

        // Wait for dashboard
        driver.waitFor("#logout-btn");
        assertEquals("Dashboard", driver.getTitle());

        // Click logout
        driver.click("#logout-btn");

        // Should be back on login page
        driver.waitFor("#login-btn");
        assertEquals("Login", driver.getTitle());
    }

    @Test
    void testClearAndRetype() {
        // Type wrong username
        driver.input("#username", "wronguser");

        // Clear and type correct username
        driver.clear("#username");
        driver.input("#username", "admin");

        // Verify the value was cleared and replaced
        assertEquals("admin", driver.value("#username"));

        // Complete login
        driver.input("#password", "admin123");
        driver.click("#login-btn");

        driver.waitFor("#username-display");
        assertEquals("Dashboard", driver.getTitle());
    }

}
