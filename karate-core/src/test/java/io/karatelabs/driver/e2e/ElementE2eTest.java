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

import io.karatelabs.driver.Element;
import io.karatelabs.driver.e2e.support.DriverTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ElementE2eTest extends DriverTestBase {

    @BeforeEach
    void navigateToInputPage() {
        driver.setUrl(testUrl("/input"));
    }

    // ========== Basic Element State ==========

    @Test
    @Order(1)
    void testExists() {
        assertTrue(driver.exists("#username"));
        assertTrue(driver.exists("#email"));
        assertFalse(driver.exists("#nonexistent"));
    }

    @Test
    @Order(2)
    void testTextContent() {
        String text = driver.text("h1");
        assertEquals("Input Test Page", text);
    }

    @Test
    @Order(3)
    void testHtml() {
        String html = driver.html("h1");
        assertTrue(html.contains("Input Test Page"));
        assertTrue(html.startsWith("<h1"));
    }

    @Test
    @Order(4)
    void testAttribute() {
        String placeholder = driver.attribute("#username", "placeholder");
        assertEquals("Enter username", placeholder);
    }

    @Test
    @Order(5)
    void testProperty() {
        Object type = driver.property("#email", "type");
        assertEquals("email", type);
    }

    @Test
    @Order(6)
    void testEnabled() {
        assertTrue(driver.enabled("#username"));
        assertTrue(driver.enabled("#submit-btn"));
    }

    // ========== Input Operations ==========

    @Test
    @Order(10)
    void testInput() {
        driver.input("#username", "testuser");
        String value = driver.value("#username");
        assertEquals("testuser", value);
    }

    @Test
    @Order(11)
    void testClear() {
        driver.input("#username", "initial");
        driver.clear("#username");
        String value = driver.value("#username");
        assertEquals("", value);
    }

    @Test
    @Order(12)
    void testInputMultipleFields() {
        driver.input("#username", "john_doe");
        driver.input("#email", "john@example.com");
        driver.input("#password", "secret123");

        assertEquals("john_doe", driver.value("#username"));
        assertEquals("john@example.com", driver.value("#email"));
        assertEquals("secret123", driver.value("#password"));
    }

    @Test
    @Order(13)
    void testInputTextarea() {
        driver.input("#bio", "This is my biography.\nLine 2.");
        String value = driver.value("#bio");
        assertTrue(value.contains("biography"));
    }

    // ========== Click Operations ==========

    @Test
    @Order(20)
    void testClick() {
        // Fill form first
        driver.input("#username", "clicktest");

        // Click submit button
        driver.click("#submit-btn");

        // Check form output contains the username
        String output = driver.text("#form-output");
        assertTrue(output.contains("clicktest"));
    }

    @Test
    @Order(21)
    void testClickClearButton() {
        driver.input("#username", "tobecleared");
        driver.click("#clear-btn");

        // After clicking Clear, form should be reset
        String output = driver.text("#form-output");
        assertTrue(output.contains("cleared"));
    }

    // ========== Select Operations ==========

    @Test
    @Order(30)
    void testSelectByValue() {
        driver.select("#country", "us");
        String value = driver.value("#country");
        assertEquals("us", value);
    }

    @Test
    @Order(31)
    void testSelectByExactText() {
        driver.select("#country", "{}United Kingdom");
        String value = driver.value("#country");
        assertEquals("uk", value);
    }

    @Test
    @Order(32)
    void testSelectByTextContains() {
        driver.select("#country", "{^}Austr");
        String value = driver.value("#country");
        assertEquals("au", value);
    }

    @Test
    @Order(33)
    void testSelectByIndex() {
        // Index 0 is "Select a country", index 1 is "United States"
        driver.select("#country", 1);
        String value = driver.value("#country");
        assertEquals("us", value);
    }

    // ========== Checkbox Operations ==========

    @Test
    @Order(40)
    void testCheckboxClick() {
        // Initially unchecked
        Object checked = driver.property("#agree", "checked");
        assertFalse((Boolean) checked);

        // Click to check
        driver.click("#agree");
        checked = driver.property("#agree", "checked");
        assertTrue((Boolean) checked);

        // Click to uncheck
        driver.click("#agree");
        checked = driver.property("#agree", "checked");
        assertFalse((Boolean) checked);
    }

    // ========== Element Class ==========

    @Test
    @Order(50)
    void testLocate() {
        Element element = driver.locate("#username");
        assertTrue(element.exists());
        assertEquals("#username", element.getLocator());
    }

    @Test
    @Order(51)
    void testLocateNonExistent() {
        Element element = driver.locate("#nonexistent");
        assertFalse(element.exists());
    }

    @Test
    @Order(52)
    void testElementChaining() {
        Element element = driver.locate("#username")
                .clear()
                .input("chained")
                .focus();

        assertEquals("chained", element.value());
    }

    @Test
    @Order(53)
    void testOptional() {
        Element exists = driver.optional("#username");
        assertTrue(exists.isPresent());

        Element notExists = driver.optional("#nonexistent");
        assertFalse(notExists.isPresent());
    }

    // ========== LocateAll ==========

    @Test
    @Order(60)
    void testLocateAll() {
        List<Element> inputs = driver.locateAll("input[type='text'], input[type='email'], input[type='password']");
        assertTrue(inputs.size() >= 3);
    }

    @Test
    @Order(61)
    void testLocateAllOptions() {
        List<Element> options = driver.locateAll("#country option");
        assertEquals(5, options.size()); // Empty + 4 countries
    }

    // ========== ScriptAll ==========

    @Test
    @Order(70)
    void testScriptAll() {
        List<Object> values = driver.scriptAll("#country option", "_.value");
        assertTrue(values.contains("us"));
        assertTrue(values.contains("uk"));
        assertTrue(values.contains("ca"));
        assertTrue(values.contains("au"));
    }

    // ========== Position ==========

    @Test
    @Order(80)
    void testPosition() {
        Map<String, Object> pos = driver.position("#username");
        assertNotNull(pos.get("x"));
        assertNotNull(pos.get("y"));
        assertNotNull(pos.get("width"));
        assertNotNull(pos.get("height"));

        // Check width and height are reasonable
        double width = ((Number) pos.get("width")).doubleValue();
        double height = ((Number) pos.get("height")).doubleValue();
        assertTrue(width > 0);
        assertTrue(height > 0);
    }

    // ========== Wait Methods ==========

    @Test
    @Order(90)
    void testWaitFor() {
        // Element already exists, should return immediately
        Element element = driver.waitFor("#username");
        assertTrue(element.exists());
    }

    @Test
    @Order(91)
    void testWaitForText() {
        Element element = driver.waitForText("h1", "Input Test");
        assertTrue(element.exists());
    }

    @Test
    @Order(92)
    void testWaitForEnabled() {
        Element element = driver.waitForEnabled("#submit-btn");
        assertTrue(element.enabled());
    }

    @Test
    @Order(93)
    void testWaitUntil() {
        // Fill in username and wait until value is set
        driver.input("#username", "waited");
        Element element = driver.waitUntil("#username", "_.value === 'waited'");
        assertEquals("waited", element.value());
    }

    @Test
    @Order(94)
    void testWaitUntilExpression() {
        // Set a JavaScript variable and wait for it
        driver.script("window.testFlag = true");
        boolean result = driver.waitUntil("window.testFlag === true");
        assertTrue(result);
    }

    // ========== Scroll and Highlight ==========

    @Test
    @Order(100)
    void testScroll() {
        // Just verify it doesn't throw
        driver.scroll("#bio");
    }

    @Test
    @Order(101)
    void testHighlight() {
        // Just verify it doesn't throw
        driver.highlight("#username");
    }

    // ========== Focus ==========

    @Test
    @Order(110)
    void testFocus() {
        driver.focus("#email");

        // Verify by checking activeElement
        Object activeId = driver.script("document.activeElement.id");
        assertEquals("email", activeId);
    }

    // ========== XPath and Wildcard Locators ==========

    @Test
    @Order(120)
    void testXPathLocator() {
        String text = driver.text("//h1");
        assertEquals("Input Test Page", text);
    }

    @Test
    @Order(121)
    void testWildcardLocatorExact() {
        String text = driver.text("{h1}Input Test Page");
        assertEquals("Input Test Page", text);
    }

    @Test
    @Order(122)
    void testWildcardLocatorContains() {
        Element element = driver.locate("{^button}Submit");
        assertTrue(element.exists());
    }

    // ========== Form Submission ==========

    @Test
    @Order(130)
    void testFullFormSubmission() {
        // Fill out form
        driver.input("#username", "johndoe");
        driver.input("#email", "john@example.com");
        driver.input("#password", "secret123");
        driver.select("#country", "us");
        driver.input("#bio", "Test biography");
        driver.click("#agree");

        // Submit
        driver.click("#submit-btn");

        // Verify output
        String output = driver.text("#form-output");
        assertTrue(output.contains("johndoe"));
        assertTrue(output.contains("john@example.com"));
        assertTrue(output.contains("secret123"));
        assertTrue(output.contains("us"));
    }

}
