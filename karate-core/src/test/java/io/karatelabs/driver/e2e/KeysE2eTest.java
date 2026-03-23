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

import io.karatelabs.driver.Keys;
import io.karatelabs.driver.e2e.support.DriverTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for keyboard operations.
 */
class KeysE2eTest extends DriverTestBase {

    @BeforeEach
    void setup() {
        driver.setUrl(testUrl("/input"));
    }

    @Test
    void testTypeText() {
        driver.focus("#username");
        driver.keys().type("hello");

        String value = driver.value("#username");
        assertEquals("hello", value);
    }

    @Test
    void testTypeWithNumbers() {
        driver.focus("#username");
        driver.keys().type("user123");

        String value = driver.value("#username");
        assertEquals("user123", value);
    }

    @Test
    void testTypeWithSpecialChars() {
        driver.focus("#email");
        driver.keys().type("test@example.com");

        String value = driver.value("#email");
        assertEquals("test@example.com", value);
    }

    @Test
    void testTabKey() {
        driver.focus("#username");
        driver.keys().type("user1");

        // Tab to next field
        driver.keys().press(Keys.TAB);

        // Type in email field (should now be focused)
        driver.keys().type("test@test.com");

        // Verify email field has the value
        String emailValue = driver.value("#email");
        assertEquals("test@test.com", emailValue);
    }

    @Test
    void testEnterKey() {
        driver.focus("#username");

        // Press Enter key (verifies key dispatch works without throwing)
        driver.keys().press(Keys.ENTER);

        // Key was sent successfully
    }

    @Test
    void testBackspace() {
        driver.focus("#username");
        driver.keys().type("hello world");

        // Delete last 5 characters
        for (int i = 0; i < 5; i++) {
            driver.keys().press(Keys.BACKSPACE);
        }

        String value = driver.value("#username");
        assertEquals("hello ", value);
    }

    @Test
    void testArrowKeys() {
        driver.focus("#username");
        driver.keys().type("abc");

        // Move cursor left and insert character
        driver.keys().press(Keys.LEFT);
        driver.keys().press(Keys.LEFT);
        driver.keys().type("X");

        String value = driver.value("#username");
        assertEquals("aXbc", value);
    }

    @Test
    void testCtrlA_SelectAll() {
        driver.input("#username", "select me");
        driver.focus("#username");

        // Select all with Ctrl+A
        driver.keys().ctrl("a");

        // Type to replace selection
        driver.keys().type("replaced");

        String value = driver.value("#username");
        assertEquals("replaced", value);
    }

    @Test
    void testShiftArrow_SelectText() {
        driver.input("#username", "hello");
        driver.focus("#username");

        // Move cursor to end (input focuses at end by default)
        // Select last 2 characters with Shift+Arrow
        driver.keys().shift(Keys.LEFT);
        driver.keys().shift(Keys.LEFT);

        // Type to replace selection
        driver.keys().type("XY");

        String value = driver.value("#username");
        assertEquals("helXY", value);
    }

    @Test
    void testMultipleModifiers_CtrlShift() {
        driver.input("#username", "test");
        driver.focus("#username");

        // Ctrl+Shift+Left selects word(s) in most browsers
        // We'll use combo() with multiple modifiers
        driver.keys().combo(new String[]{Keys.CONTROL, Keys.SHIFT}, Keys.LEFT);

        // Replace with new text
        driver.keys().type("X");

        // The result depends on browser word selection, but should have changed
        String value = driver.value("#username");
        // Word selection varies by browser, just verify it's different
        assertNotEquals("test", value);
    }

    @Test
    void testDownUp_NonModifierKey() {
        driver.focus("#username");

        // Test down()/up() for regular keys
        driver.keys().down("a");
        driver.keys().up("a");

        // 'a' should have been typed via the key events
        // Note: down() sends rawKeyDown, up() sends keyUp
        // Without the char event, the character may or may not appear
        // depending on the browser's handling of rawKeyDown
        // This test verifies the API doesn't throw
    }

    @Test
    void testAltKey() {
        driver.focus("#username");
        driver.keys().type("hello");

        // Alt+key should not type the character
        driver.keys().alt("x");

        // Value should still be "hello" - 'x' should not be added
        String value = driver.value("#username");
        assertEquals("hello", value);
    }

    @Test
    void testNumpadKeys() {
        driver.focus("#username");

        // Type using numpad keys
        driver.keys().press(Keys.NUMPAD1);
        driver.keys().press(Keys.NUMPAD2);
        driver.keys().press(Keys.NUMPAD3);

        String value = driver.value("#username");
        assertEquals("123", value);
    }

    @Test
    void testComboMethodChaining() {
        driver.input("#username", "test value");
        driver.focus("#username");

        // Method chaining with multiple operations
        driver.keys()
                .ctrl("a")     // Select all
                .type("new");  // Replace

        String value = driver.value("#username");
        assertEquals("new", value);
    }

    @Test
    void testEscapeKey() {
        driver.focus("#username");
        driver.keys().type("hello");

        // Escape should not add text
        driver.keys().press(Keys.ESCAPE);

        String value = driver.value("#username");
        assertEquals("hello", value);
    }

    @Test
    void testHomeEndKeys() {
        driver.focus("#username");
        driver.keys().type("hello");

        // Move to beginning with Home, then type
        driver.keys().press(Keys.HOME);
        driver.keys().type("X");

        String value = driver.value("#username");
        assertEquals("Xhello", value);

        // Move to end with End, then type
        driver.keys().press(Keys.END);
        driver.keys().type("Y");

        value = driver.value("#username");
        assertEquals("XhelloY", value);
    }

    @Test
    void testDeleteKey() {
        driver.focus("#username");
        driver.keys().type("hello");

        // Move to beginning
        driver.keys().press(Keys.HOME);

        // Delete first character (forward delete)
        driver.keys().press(Keys.DELETE);

        String value = driver.value("#username");
        assertEquals("ello", value);
    }

    @Test
    void testHoldShiftWhileTyping() {
        driver.focus("#username");

        // Hold Shift, type, release Shift
        driver.keys().down(Keys.SHIFT);
        driver.keys().type("hello");
        driver.keys().up(Keys.SHIFT);

        // Should be uppercase
        String value = driver.value("#username");
        assertEquals("HELLO", value);
    }

    @Test
    void testHoldShiftWhileTyping_Chained() {
        driver.focus("#username");

        // Chained: Shift held for first part, released for second
        driver.keys()
                .down(Keys.SHIFT)
                .type("abc")
                .up(Keys.SHIFT)
                .type("def");

        String value = driver.value("#username");
        assertEquals("ABCdef", value);
    }

    @Test
    void testPlusNotation_CtrlA() {
        driver.input("#username", "select me");
        driver.focus("#username");

        // Use "+" notation: Control+a
        driver.keys().press("Control+a");
        driver.keys().type("replaced");

        String value = driver.value("#username");
        assertEquals("replaced", value);
    }

    @Test
    void testPlusNotation_ShiftArrow() {
        driver.input("#username", "hello");
        driver.focus("#username");

        // Use "+" notation for Shift+Arrow
        driver.keys().press("Shift+ArrowLeft");
        driver.keys().press("Shift+ArrowLeft");
        driver.keys().type("XY");

        String value = driver.value("#username");
        assertEquals("helXY", value);
    }

    @Test
    void testPlusNotation_MultipleModifiers() {
        driver.input("#username", "test");
        driver.focus("#username");

        // Multiple modifiers: Control+Shift+ArrowLeft (select word)
        driver.keys().press("Control+Shift+ArrowLeft");
        driver.keys().type("X");

        String value = driver.value("#username");
        // Should have selected and replaced
        assertNotEquals("test", value);
    }

}
