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

import io.karatelabs.driver.Element;
import io.karatelabs.driver.Locators;
import io.karatelabs.driver.e2e.support.DriverTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for all locator types including wildcards, CSS, and XPath.
 * Tests against a real browser using the locators.html test page.
 */
class LocatorsE2eTest extends DriverTestBase {

    @BeforeEach
    void navigateToLocatorPage() {
        driver.setUrl(testUrl("/locators"));
    }

    // ========== CSS Selectors ==========

    @Test
    void testCssById() {
        assertTrue(driver.exists("#click-result"));
        assertTrue(driver.exists("#container"));
    }

    @Test
    void testCssByClass() {
        assertTrue(driver.exists(".form-group"));
    }

    @Test
    void testCssByTag() {
        assertTrue(driver.exists("h1"));
        assertEquals("Locators Test Page", driver.text("h1"));
    }

    @Test
    void testCssByAttribute() {
        assertTrue(driver.exists("[data-testid='primary-btn']"));
        assertTrue(driver.exists("[aria-label='Username']"));
    }

    @Test
    void testCssComplex() {
        assertTrue(driver.exists("#container button"));
        assertTrue(driver.exists("section h2"));
    }

    // ========== XPath Selectors ==========

    @Test
    void testXpathAbsolute() {
        String text = driver.text("//h1");
        assertEquals("Locators Test Page", text);
    }

    @Test
    void testXpathWithPredicate() {
        assertTrue(driver.exists("//button[@data-testid='primary-btn']"));
    }

    @Test
    void testXpathIndexed() {
        // Get the second li element (all have text "Item", differentiate by data-index)
        String dataIndex = driver.attribute("(//li)[2]", "data-index");
        assertEquals("2", dataIndex);
    }

    @Test
    void testXpathRelative() {
        // Use XPath with parent context
        assertTrue(driver.exists("//section[@id='container']//button"));
    }

    // ========== Wildcard Exact Match ==========

    @Test
    void testWildcardExactMatch() {
        assertTrue(driver.exists("{button}Click Me"));
        assertEquals("Click Me", driver.text("{button}Click Me"));
    }

    @Test
    void testWildcardExactMatchNoMatch() {
        // Partial text should not match exact wildcard
        assertFalse(driver.exists("{button}Click M"));
    }

    @Test
    void testWildcardAnyTag() {
        assertTrue(driver.exists("{}Truly Unique Text"));
        String tagName = (String) driver.script("{}Truly Unique Text", "_.tagName");
        assertEquals("SPAN", tagName);
    }

    @Test
    void testWildcardSpecificTag() {
        assertTrue(driver.exists("{span}Truly Unique Text"));
        assertTrue(driver.exists("{button}Submit"));
    }

    @Test
    void testWildcardTagOnly() {
        assertTrue(driver.exists("{details}"));
    }

    @Test
    void testWildcardAnchorTagNoAttributes() {
        assertTrue(driver.exists("{a}Anchor With No Attributes"));
    }

    // ========== Wildcard Contains Match ==========

    @Test
    void testWildcardContains() {
        assertTrue(driver.exists("{^button}Click"));
        assertTrue(driver.exists("{^button}Me"));
    }

    @Test
    void testWildcardContainsAnyTag() {
        assertTrue(driver.exists("{^}Truly Unique"));
        assertTrue(driver.exists("{^}special chars"));
    }

    @Test
    void testWildcardContainsCaseSensitive() {
        // Contains match is case-sensitive in XPath
        assertTrue(driver.exists("{^span}special"));
        assertFalse(driver.exists("{^span}SPECIAL"));
    }

    // ========== Wildcard Index Matching ==========

    @Test
    void testWildcardIndexFirst() {
        driver.click("{button:1}Add");
        String result = driver.text("#click-result");
        assertEquals("Add 1 clicked", result);
    }

    @Test
    void testWildcardIndexSecond() {
        driver.click("{button:2}Add");
        String result = driver.text("#click-result");
        assertEquals("Add 2 clicked", result);
    }

    @Test
    void testWildcardIndexThird() {
        driver.click("{button:3}Add");
        String result = driver.text("#click-result");
        assertEquals("Add 3 clicked", result);
    }

    @Test
    void testWildcardIndexAnyTag() {
        // Index with any tag - matches second "Item" element
        String dataIndex = driver.attribute("{:2}Item", "data-index");
        assertEquals("2", dataIndex);
    }

    // ========== Special Characters ==========

    @Test
    void testWildcardWithSingleQuote() {
        assertTrue(driver.exists("{span}It's working"));
        assertEquals("It's working", driver.text("{span}It's working"));
    }

    @Test
    void testWildcardWithDoubleQuote() {
        assertTrue(driver.exists("{span}Say \"Hello\""));
    }

    @Test
    void testWildcardWithBothQuotes() {
        assertTrue(driver.exists("{span}It's \"complex\""));
    }

    @Test
    void testWildcardWithBrackets() {
        assertTrue(driver.exists("{a}[Edit]"));
        assertEquals("[Edit]", driver.text("{a}[Edit]"));
    }

    @Test
    void testWildcardWithDollarSign() {
        assertTrue(driver.exists("{span}$100.00"));
    }

    @Test
    void testWildcardWithParentheses() {
        assertTrue(driver.exists("{span}Price (USD)"));
    }

    @Test
    void testWildcardWithColon() {
        // Colon in text should not be interpreted as index separator
        assertTrue(driver.exists("{span}Time: 10:30"));
    }

    // ========== Whitespace Handling ==========

    @Test
    void testWildcardNormalizesWhitespace() {
        // normalize-space() in XPath collapses multiple spaces
        assertTrue(driver.exists("{p}Spaced text"));
    }

    @Test
    void testWildcardWithNewline() {
        // Multi-line text - contains match
        assertTrue(driver.exists("{^pre}Line 1"));
        assertTrue(driver.exists("{^pre}Line 2"));
    }

    // ========== Click Operations with Wildcards ==========

    @Test
    void testClickWithWildcard() {
        driver.click("{button}Click Me");
        String result = driver.text("#click-result");
        assertEquals("Clicked!", result);
    }

    @Test
    void testClickDataTestId() {
        driver.click("[data-testid='primary-btn']");
        String result = driver.text("#click-result");
        assertEquals("Primary clicked", result);
    }

    // ========== Input Operations with Wildcards ==========

    @Test
    void testInputWithAriaLabel() {
        driver.input("[aria-label='Username']", "testuser");
        String value = driver.value("[aria-label='Username']");
        assertEquals("testuser", value);
    }

    @Test
    void testInputWithPlaceholder() {
        driver.input("[placeholder='Enter email']", "test@example.com");
        String value = driver.value("[placeholder='Enter email']");
        assertEquals("test@example.com", value);
    }

    // ========== Element Chaining ==========

    @Test
    void testElementChainingWithWildcard() {
        Element element = driver.locate("{button}Click Me");
        assertTrue(element.exists());
        element.click();
        String result = driver.text("#click-result");
        assertEquals("Clicked!", result);
    }

    // ========== List Item Matching ==========

    @Test
    void testListItemsByIndex() {
        // All three li elements have same text "Item", distinguish by index
        assertEquals("1", driver.attribute("{li:1}Item", "data-index"));
        assertEquals("2", driver.attribute("{li:2}Item", "data-index"));
        assertEquals("3", driver.attribute("{li:3}Item", "data-index"));
    }

    // ========== Data Attribute Locators ==========

    @Test
    void testDataTestIdLocator() {
        assertTrue(driver.exists("[data-testid='primary-btn']"));
        assertTrue(driver.exists("[data-testid='secondary-btn']"));
    }

    // ========== Relative Locators (Context) ==========

    @Test
    void testLocatorWithContext() {
        Element container = driver.locate("#container");
        assertTrue(container.exists());

        // Find button within container using descendant CSS selector
        Element button = driver.locate("#container button");
        assertTrue(button.exists());

        // Click and verify
        button.click();
        String result = driver.text("#click-result");
        assertEquals("Container Submit clicked", result);
    }

    // ========== LocateAll with Wildcards ==========

    @Test
    void testLocateAllWithCss() {
        var buttons = driver.locateAll("button");
        assertTrue(buttons.size() > 5);
    }

    @Test
    void testLocateAllWithXpath() {
        var items = driver.locateAll("//li");
        assertEquals(3, items.size());
    }

    // ========== findAll enumeration: every locator must re-resolve (round-trip) ==========

    /**
     * Regression for the {@link Locators#findAllJs} locator bug: the enumeration
     * must return, for each match, a selector that re-resolves to <em>that</em>
     * element via {@code document.querySelector}. On the locators page "button"
     * and "input" match elements scattered across many sections / wrapper divs,
     * so the old unscoped {@code <sel>:nth-of-type(i+1)} form yielded locators
     * that resolved to a different element or to null (silently breaking
     * {@code findAll().eval()} / {@code act()} on those handles). Driven through
     * a real browser so the page-side {@code __kjs.uniqueCss} path is exercised.
     */
    @Test
    void testFindAllJsLocatorsRoundTrip() {
        verifyFindAllRoundTrip("button");
        verifyFindAllRoundTrip("input");
    }

    @SuppressWarnings("unchecked")
    private void verifyFindAllRoundTrip(String selector) {
        List<Object> locs = (List<Object>) driver.script(Locators.findAllJs(selector));
        int count = ((Number) driver.script(
                "document.querySelectorAll(" + jsStr(selector) + ").length")).intValue();
        assertEquals(count, locs.size(), "findAllJs locator count mismatch for: " + selector);
        assertTrue(count > 1, "expected several scattered matches for: " + selector + " (got " + count + ")");
        for (int i = 0; i < locs.size(); i++) {
            String loc = String.valueOf(locs.get(i));
            Object ok = driver.script(
                    "(function(){ var el = document.querySelector(" + jsStr(loc) + ");"
                            + " return el !== null && el === document.querySelectorAll(" + jsStr(selector) + ")[" + i + "]; })()");
            assertEquals(Boolean.TRUE, ok,
                    "findAllJs locator[" + i + "] '" + loc + "' must re-resolve to the matched element for: " + selector);
        }
    }

    private static String jsStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ========== Edge Cases ==========

    @Test
    void testNonExistentElement() {
        assertFalse(driver.exists("{button}NonExistentText"));
        assertFalse(driver.exists("#non-existent-id"));
        assertFalse(driver.exists("//div[@id='nowhere']"));
    }

    @Test
    void testEmptyElement() {
        assertTrue(driver.exists("#empty-div"));
        String text = driver.text("#empty-div");
        assertEquals("", text);
    }

    // ========== Wildcard JS Resolver ==========

    @Test
    void testWildcardMatchesVisibleOnly() {
        driver.setUrl("data:text/html," +
            "<div style='display:none'>Save</div>" +
            "<div id='visible'>Save</div>");
        Element el = driver.locate("{div}Save");
        assertEquals("visible", el.attribute("id"));
    }

    @Test
    void testWildcardMatchesLeafElement() {
        driver.setUrl("data:text/html,<div id='outer'><div id='inner'>Click</div></div>");
        Element el = driver.locate("{div}Click");
        assertEquals("inner", el.attribute("id"));
    }

    @Test
    void testWildcardIndexCountsVisibleOnly() {
        driver.setUrl("data:text/html," +
            "<button>Save</button>" +
            "<button style='display:none'>Save</button>" +
            "<button id='second'>Save</button>");
        Element el = driver.locate("{button:2}Save");
        assertEquals("second", el.attribute("id"));
    }

    @Test
    void testWildcardButtonExpandsRoles() {
        // {button} should match role="button" too
        driver.setUrl("data:text/html,<div role='button' id='btn'>Click Me</div>");
        Element el = driver.locate("{button}Click Me");
        assertEquals("btn", el.attribute("id"));
    }

    /**
     * Regression: {@code ensureKjsRuntime} must guard on {@code __kjs.resolve}, not merely the
     * existence of {@code window.__kjs}. A co-installed helper (karate-max's {@code agent-look.js},
     * installed via {@code Page.addScriptToEvaluateOnNewDocument} so capture survives navigation)
     * seeds a <em>partial</em> {@code window.__kjs} that lacks the wildcard resolver. With a bare
     * {@code typeof window.__kjs !== 'undefined'} guard, driver.js injection is then skipped and any
     * {@code {tag}text} locator throws "window.__kjs.resolve is not a function". Here we simulate
     * that partial seed and assert the wildcard still resolves (driver.js extends, never clobbers).
     */
    @Test
    void testWildcardResolvesAfterPartialKjsSeed() {
        driver.setUrl("data:text/html,<button id='go'>Log In</button>");
        // mimic agent-look's partial seed landing on the document before any resolver injection
        // (the assignment overwrites whatever driver.js this script() call may have injected first)
        driver.script("window.__kjs = { look: {}, log: function(){} };");
        Element el = driver.locate("{button}Log In");
        assertEquals("go", el.attribute("id"));
    }

}
