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

import io.karatelabs.driver.Dialog;
import io.karatelabs.driver.cdp.*;

import io.karatelabs.driver.e2e.support.DriverTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for dialog handling (alert, confirm, prompt).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DialogE2eTest extends DriverTestBase {

    @BeforeEach
    void navigateToDialogPage() {
        // Clear any previous dialog handler
        driver.onDialog(null);
        driver.setUrl(testUrl("/dialog"));
    }

    // ========== Alert Dialog ==========

    @Test
    @Order(1)
    void testAlertWithHandler() {
        AtomicReference<String> capturedMessage = new AtomicReference<>();
        AtomicReference<String> capturedType = new AtomicReference<>();

        driver.onDialog(dialog -> {
            capturedMessage.set(dialog.getMessage());
            capturedType.set(dialog.getType());
            dialog.accept();
        });

        driver.click("#alert-btn");

        // Wait for result
        driver.waitForText("#result", "Alert was shown");

        assertEquals("Hello from alert!", capturedMessage.get());
        assertEquals("alert", capturedType.get());
    }

    @Test
    @Order(2)
    void testAlertWithDialogMethod() {
        // No handler - use manual dialog method
        driver.onDialog(dialog -> {
            // Capture but use manual method
            dialog.accept();
        });

        driver.click("#alert-btn");

        // Wait for result
        driver.waitForText("#result", "Alert was shown");
        String resultText = driver.text("#result");
        assertEquals("Alert was shown", resultText);
    }

    // ========== Confirm Dialog ==========

    @Test
    @Order(3)
    void testConfirmAccept() {
        driver.onDialog(dialog -> {
            assertEquals("confirm", dialog.getType());
            assertEquals("Do you confirm?", dialog.getMessage());
            dialog.accept();
        });

        driver.click("#confirm-btn");

        // Wait for result
        driver.waitForText("#result", "Confirm result: true");
        String resultText = driver.text("#result");
        assertEquals("Confirm result: true", resultText);
    }

    @Test
    @Order(4)
    void testConfirmDismiss() {
        driver.onDialog(dialog -> {
            assertEquals("confirm", dialog.getType());
            dialog.dismiss();
        });

        driver.click("#confirm-btn");

        // Wait for result
        driver.waitForText("#result", "Confirm result: false");
        String resultText = driver.text("#result");
        assertEquals("Confirm result: false", resultText);
    }

    // ========== Prompt Dialog ==========

    @Test
    @Order(5)
    void testPromptAccept() {
        driver.onDialog(dialog -> {
            assertEquals("prompt", dialog.getType());
            assertEquals("Enter your name:", dialog.getMessage());
            dialog.accept("John Doe");
        });

        driver.click("#prompt-btn");

        // Wait for result
        driver.waitForText("#result", "Prompt result: John Doe");
        String resultText = driver.text("#result");
        assertEquals("Prompt result: John Doe", resultText);
    }

    @Test
    @Order(6)
    void testPromptDismiss() {
        driver.onDialog(dialog -> {
            assertEquals("prompt", dialog.getType());
            dialog.dismiss();
        });

        driver.click("#prompt-btn");

        // Wait for result
        driver.waitForText("#result", "Prompt result: null");
        String resultText = driver.text("#result");
        assertEquals("Prompt result: null", resultText);
    }

    @Test
    @Order(7)
    void testPromptWithDefault() {
        AtomicReference<String> capturedDefault = new AtomicReference<>();

        driver.onDialog(dialog -> {
            assertEquals("prompt", dialog.getType());
            capturedDefault.set(dialog.getDefaultPrompt());
            // Accept with the default value
            dialog.accept(dialog.getDefaultPrompt());
        });

        driver.click("#prompt-default-btn");

        // Wait for result
        driver.waitForText("#result", "Prompt result: default value");
        assertEquals("default value", capturedDefault.get());
    }

    @Test
    @Order(8)
    void testPromptEmptyInput() {
        driver.onDialog(dialog -> {
            assertEquals("prompt", dialog.getType());
            dialog.accept(""); // Empty string
        });

        driver.click("#prompt-btn");

        // Wait for result - empty string becomes empty but not null
        driver.waitForText("#result", "Prompt result:");
    }

    // ========== Dialog State ==========

    @Test
    @Order(9)
    void testGetDialogText() {
        AtomicReference<String> dialogText = new AtomicReference<>();

        driver.onDialog(dialog -> {
            // Capture dialog text via getDialogText()
            dialogText.set(driver.getDialogText());
            dialog.accept();
        });

        driver.click("#alert-btn");

        // Wait for result
        driver.waitForText("#result", "Alert was shown");
        assertEquals("Hello from alert!", dialogText.get());
    }

    @Test
    @Order(10)
    void testMultipleDialogsSequentially() {
        AtomicReference<Integer> dialogCount = new AtomicReference<>(0);

        driver.onDialog(dialog -> {
            dialogCount.updateAndGet(c -> c + 1);
            if (dialog.getType().equals("confirm")) {
                dialog.accept();
            } else {
                dialog.accept();
            }
        });

        // Trigger multiple dialogs
        driver.click("#alert-btn");
        driver.waitForText("#result", "Alert was shown");

        driver.click("#confirm-btn");
        driver.waitForText("#result", "Confirm result: true");

        assertEquals(2, dialogCount.get());
    }

    @Test
    @Order(11)
    void testScriptFailsFastWhenDialogBlocking() throws Exception {
        // No dialog handler — dialog will stay open and block Runtime.evaluate
        // Use setTimeout so script() returns before the dialog opens
        driver.script("setTimeout(() => document.getElementById('confirm-btn').click(), 100)");
        Thread.sleep(500); // Wait for dialog to open

        long start = System.currentTimeMillis();
        try {
            driver.script("document.title");
            fail("Should have thrown due to dialog blocking");
        } catch (DialogOpenedException e) {
            // Expected — dialog cancelled the pending evaluation
        }
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 2000, "Should fail fast, not wait for timeout. Took: " + elapsed + "ms");

        // Clean up — dismiss dialog
        Dialog dialog = driver.getDialog();
        assertNotNull(dialog);
        dialog.dismiss();
    }

}
