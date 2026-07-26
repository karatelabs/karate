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
import java.util.Map;

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

    /**
     * Robustness: {@link Locators#SCROLL_JS_FUNCTION} run on a locator that resolves to NOTHING (a missing or
     * stale element) must be a graceful no-op — not a cryptic
     * "Failed to execute 'getComputedStyle' on 'Window': parameter 1 is not of type 'Element'" that masks the
     * real "element not found". Without the null/non-element guard, {@code getComputedStyle(null)} threw; the
     * unguarded {@code while(d=='none')} climb could likewise step past {@code <html>} onto {@code null}.
     */
    @Test
    void testScrollJsOnMissingElementIsNoOpNotCrash() {
        driver.setUrl("data:text/html,<div id='present'>here</div>");
        assertDoesNotThrow(() -> driver.script(Locators.scrollJs("#totally-absent")),
                "scrolling a missing element must degrade to a no-op, not throw getComputedStyle(null)");
        assertDoesNotThrow(() -> driver.script(Locators.scrollJs("#present")),
                "a present element must still scroll without error");
    }

    /**
     * Action JS on a locator that resolves to NOTHING must fail loudly and name the locator.
     * Two regressions are pinned here. An unguarded deref reported a cryptic
     * "Cannot read properties of null (reading 'focus')" against generated source with no
     * locator anywhere in it. Worse, input/clear silently no-opped, stranding the caller with
     * a downstream symptom far from the cause — an empty field failing a match many steps
     * later. The marker also has to survive, since it is what makes a transient re-resolve
     * miss retryable rather than fatal.
     * <p>
     * Contrast {@link #testScrollJsOnMissingElementIsNoOpNotCrash} — scroll deliberately keeps
     * its no-op contract, because scrolling something absent is harmless.
     * </p>
     */
    @Test
    void testActionJsOnMissingElementFailsLoudlyNamingLocator() {
        driver.setUrl("data:text/html,<div id='present'>here</div>");
        List<String> actions = List.of(
                Locators.focusJs("#totally-absent"),
                Locators.clickJs("#totally-absent"),
                Locators.inputJs("#totally-absent", "x"),
                Locators.clearJs("#totally-absent"),
                Locators.getPositionJs("#totally-absent"),
                Locators.optionSelector("#totally-absent", "x"));
        for (String js : actions) {
            RuntimeException e = assertThrows(RuntimeException.class, () -> driver.script(js),
                    "action on a missing element must throw, not no-op: " + js);
            assertTrue(e.getMessage().contains(Locators.ELEMENT_NOT_FOUND),
                    "must carry the retryable marker, got: " + e.getMessage());
            assertTrue(e.getMessage().contains("#totally-absent"),
                    "must name the locator, got: " + e.getMessage());
        }
    }

    // ========== Resolve ranker (the __kjs.setResolveRanker extension seam) ==========
    // A downstream runtime (e.g. an agent layer that knows an open menu item should
    // beat an ambient same-text label) can register a candidate ranker instead of
    // monkeypatching __kjs.resolve. The seam's contract: with no ranker — or a
    // broken one — resolution is EXACTLY the default (first visible leaf match in
    // DOM order); a registered ranker reorders or augments the matched candidate
    // list, and index selection applies to the ranked list.

    @Test
    void testResolveRankerReordersTieAndUnregisters() {
        driver.setUrl("data:text/html," +
                "<div id='ambient'>Ray</div>" +
                "<div id='menu-item'>Ray</div>");
        // default: first visible match in DOM order
        assertEquals("ambient", driver.attribute("{div}Ray", "id"));
        driver.script("window.__kjs.setResolveRanker(function(matches, ctx){"
                + " return matches.slice().reverse(); })");
        assertEquals("menu-item", driver.attribute("{div}Ray", "id"));
        // unregister restores the default order
        driver.script("window.__kjs.setResolveRanker(null)");
        assertEquals("ambient", driver.attribute("{div}Ray", "id"));
    }

    @Test
    void testResolveRankerIndexAppliesToRankedList() {
        driver.setUrl("data:text/html," +
                "<b id='one'>X</b><b id='two'>X</b><b id='three'>X</b>");
        // rotate: [two, three, one]
        driver.script("window.__kjs.setResolveRanker(function(m){ m.push(m.shift()); return m; })");
        assertEquals("two", driver.attribute("{b:1}X", "id"));
        assertEquals("three", driver.attribute("{b:2}X", "id"));
        assertEquals("one", driver.attribute("{b:3}X", "id"));
        // index beyond the ranked list still misses
        assertFalse(driver.exists("{b:4}X"));
    }

    @Test
    void testResolveRankerIdentityKeepsDefaultBehavior() {
        driver.script("window.__kjs.setResolveRanker(function(m){ return m; })");
        assertEquals("1", driver.attribute("{li:1}Item", "data-index"));
        assertEquals("2", driver.attribute("{li:2}Item", "data-index"));
        assertEquals("3", driver.attribute("{li:3}Item", "data-index"));
        assertEquals("Click Me", driver.text("{button}Click Me"));
        assertFalse(driver.exists("{button}NonExistentText"));
    }

    @Test
    void testResolveRankerFailuresFallBackToDefaultOrder() {
        driver.setUrl("data:text/html," +
                "<div id='first'>Twin</div>" +
                "<div id='second'>Twin</div>");
        // a throwing ranker must not break resolution
        driver.script("window.__kjs.setResolveRanker(function(){ throw new Error('boom'); })");
        assertEquals("first", driver.attribute("{div}Twin", "id"));
        // a non-array return is ignored
        driver.script("window.__kjs.setResolveRanker(function(){ return 'nope'; })");
        assertEquals("first", driver.attribute("{div}Twin", "id"));
    }

    @Test
    void testResolveRankerMayAddCandidatesTheDefaultFilterExcluded() {
        // the ranker may inject candidates of its own — e.g. a hidden element it
        // knows how to reveal — which the default visible-only filter never emits
        driver.setUrl("data:text/html," +
                "<div id='visible-twin'>Save</div>" +
                "<div id='hidden-menu' style='display:none'>Save</div>");
        assertEquals("visible-twin", driver.attribute("{div}Save", "id"));
        driver.script("window.__kjs.setResolveRanker(function(m){"
                + " m.unshift(document.getElementById('hidden-menu')); return m; })");
        assertEquals("hidden-menu", driver.attribute("{div}Save", "id"));
    }

    // ========== resolveAll (the candidate list behind resolve) ==========
    // Asking "which index am I?" used to mean calling resolve() for 1, 2, 3 … n, and each of
    // those re-scans the document — on an enterprise page whose text repeats, that walk cost
    // hundreds of ms PER ELEMENT and dominated the caller's run. resolveAll answers it in one
    // scan, and is only worth having if it is EXACTLY what resolve() would have said.

    @Test
    void testResolveAllIsIndexByIndexWhatResolveReturns() {
        driver.setUrl("data:text/html,"
                + "<b id='one'>Status</b><i>skip</i><b id='two'>Status</b>"
                + "<b id='three'>Status</b><b id='other'>Elsewhere</b>");
        // the equivalence, asserted in the page so it compares element IDENTITY, not ids
        Object verdict = driver.script("(function(){"
                + " var all = window.__kjs.resolveAll('b', 'Status', false);"
                + " for (var i = 0; i < all.length; i++) {"
                + "   if (all[i] !== window.__kjs.resolve('b', 'Status', i + 1, false)) return 'differs at ' + (i + 1);"
                + " }"
                + " if (window.__kjs.resolve('b', 'Status', all.length + 1, false)) return 'resolve saw one past the list';"
                + " return 'ok:' + all.length; })()");
        assertEquals("ok:3", String.valueOf(verdict));
    }

    @Test
    void testResolveAllReportsNonCandidatesAsAbsentRatherThanGuessing() {
        driver.setUrl("data:text/html,<b id='hit'>Status</b><b id='miss'>Other</b>");
        Object verdict = driver.script("(function(){"
                + " var all = window.__kjs.resolveAll('b', 'Status', false);"
                + " return [all.length,"
                + "  all.indexOf(document.getElementById('hit')),"
                + "  all.indexOf(document.getElementById('miss')),"
                + "  window.__kjs.resolveAll('b', 'NothingHere', false).length].join(','); })()");
        assertEquals("1,0,-1,0", String.valueOf(verdict));
    }

    @Test
    void testResolveAllHonoursTheRankerSoItStillAgreesWithResolve() {
        driver.setUrl("data:text/html,<b id='one'>X</b><b id='two'>X</b><b id='three'>X</b>");
        driver.script("window.__kjs.setResolveRanker(function(m){ m.push(m.shift()); return m; })");
        Object ranked = driver.script("(function(){"
                + " var all = window.__kjs.resolveAll('b', 'X', false);"
                + " var ids = []; for (var i = 0; i < all.length; i++) ids.push(all[i].id);"
                + " return ids.join(','); })()");
        assertEquals("two,three,one", String.valueOf(ranked));
        // …and index-for-index that is still what resolve() hands back under the same ranker
        assertEquals("two", driver.attribute("{b:1}X", "id"));
        assertEquals("one", driver.attribute("{b:3}X", "id"));
    }

    @Test
    void testResolveAllFallsBackToTheDeepSweepLikeResolveDoes() {
        driver.setUrl("data:text/html,<div id='host'></div>");
        driver.script("(function(){ var h = document.getElementById('host').attachShadow({mode:'open'});"
                + " h.innerHTML = \"<b id='inner'>Shadowed</b>\"; })()");
        Object verdict = driver.script("(function(){"
                + " var all = window.__kjs.resolveAll('b', 'Shadowed', false);"
                + " if (all.length !== 1) return 'list length ' + all.length;"
                + " return all[0] === window.__kjs.resolve('b', 'Shadowed', 1, false) ? 'same' : 'different'; })()");
        assertEquals("same", String.valueOf(verdict));
    }

    // ========== withScan (the quiescent-scan memo) ==========
    // resolve()/resolveAll() evaluate the text predicate over EVERY candidate, and extracting one
    // candidate's visible text walks each of its text nodes' ancestors asking getComputedStyle. So
    // one document-wide resolve costs O(nodes x depth) style reads — and a caller that resolves
    // once per element pays that whole cost per element, re-deriving the same few thousand answers
    // every time. On a live enterprise page (2298 nodes, 48 elements) that was 9.4 seconds.
    //
    // withScan() lets a caller that KNOWS the page is holding still pay for those answers once.
    // The tests below pin the three things that makes it safe to rely on rather than the wall
    // clock, which no CI box can promise: the answers do not change, the work actually drops, and
    // a closed scan leaves nothing behind.

    /** A page dense and deep enough for O(nodes x depth) to dominate — 60 rows of repeated text,
     *  each nested a realistic number of levels down (a real enterprise screen puts its text nodes
     *  15-25 levels deep, and depth is the multiplier). Built in the page so the fixture sits next
     *  to the assertions that depend on its shape. */
    private static final String DENSE_PAGE = "(function(){"
            + " var host = document.createElement('div'); host.id = 'dense';"
            + " var shell = host;"
            + " for (var w = 0; w < 6; w++) { var lvl = document.createElement('div'); shell.appendChild(lvl); shell = lvl; }"
            + " var LABELS = ['Edit', 'Delete', 'View', 'Approve', 'Reject'];"
            + " for (var r = 0; r < 60; r++) {"
            + "   var row = document.createElement('div');"
            + "   for (var c = 0; c < LABELS.length; c++) {"
            + "     var cell = document.createElement('div');"
            + "     var inner = document.createElement('div');"
            + "     var span = document.createElement('span');"
            + "     span.appendChild(document.createTextNode(LABELS[c]));"
            + "     inner.appendChild(span); cell.appendChild(inner); row.appendChild(cell);"
            + "   }"
            + "   shell.appendChild(row);"
            + " }"
            + " document.body.appendChild(host);"
            + " return document.getElementsByTagName('*').length; })()";

    /** The shape a caller's derivation pass has: for every distinct repeated text, ask which
     *  candidates match and which index each one is. Returns the answers as a fingerprint plus the
     *  getComputedStyle count it took to produce them — the count is the deterministic cost
     *  measure, identical on any machine, where wall clock is not. */
    private static final String WORKLOAD = "function(useScan){"
            + " var k = window.__kjs;"
            + " var LABELS = ['Edit', 'Delete', 'View', 'Approve', 'Reject'];"
            + " var styles = 0, origStyle = window.getComputedStyle;"
            + " window.getComputedStyle = function(){ styles++; return origStyle.apply(this, arguments); };"
            + " var body = function(){"
            + "   var out = [];"
            + "   var all = document.getElementsByTagName('*');"
            + "   for (var i = 0; i < all.length; i++) { if (k.getVisibleText(all[i])) out.push(i); }"
            + "   for (var q = 0; q < LABELS.length; q++) {"
            // the wildcard tag deliberately: a '*' resolve is what a caller deriving a {tag}text
            // locator actually issues, and it is the shape whose cost is O(nodes x depth) — a
            // tag-scoped resolve over leaf <span>s would walk almost nothing and prove nothing
            + "     var list = k.resolveAll('*', LABELS[q], false);"
            + "     out.push(LABELS[q] + '#' + list.length);"
            + "     for (var j = 0; j < list.length; j++) {"
            + "       out.push(k.resolve('*', LABELS[q], j + 1, false) === list[j] ? 1 : 'MISMATCH@' + j);"
            + "       out.push(k.isVisible(list[j]) ? 1 : 0);"
            + "     }"
            + "   }"
            + "   return out.join(',');"
            + " };"
            + " var fingerprint, t0 = performance.now();"
            + " try { fingerprint = useScan ? k.withScan(body) : body(); }"
            + " finally { window.getComputedStyle = origStyle; }"
            + " return {fp: fingerprint, styles: styles, ms: performance.now() - t0,"
            + "         open: k.inScan(), nodes: document.getElementsByTagName('*').length};"
            + "}";

    @SuppressWarnings("unchecked")
    private Map<String, Object> measure(boolean useScan) {
        return (Map<String, Object>) driver.script("(" + WORKLOAD + ")(" + useScan + ")");
    }

    @Test
    void testWithScanReturnsTheSameAnswersForFarLessWorkAndLeavesNoResidue() {
        driver.setUrl(testUrl("/locators"));
        Object nodes = driver.script(DENSE_PAGE);
        assertTrue(Integer.parseInt(String.valueOf(nodes)) > 900,
                "the dense fixture must actually be dense, got " + nodes + " nodes");

        Map<String, Object> closedBefore = measure(false);
        Map<String, Object> open = measure(true);
        Map<String, Object> closedAfter = measure(false);

        long stylesClosed = ((Number) closedBefore.get("styles")).longValue();
        long stylesOpen = ((Number) open.get("styles")).longValue();
        long stylesAfter = ((Number) closedAfter.get("styles")).longValue();
        logger.info("withScan: {} nodes, getComputedStyle {} closed -> {} open ({}x), {} ms -> {} ms",
                closedBefore.get("nodes"), stylesClosed, stylesOpen,
                stylesClosed / Math.max(stylesOpen, 1), closedBefore.get("ms"), open.get("ms"));

        // 1. the answers are identical — a memo that changes what resolve() says is a bug, not a
        //    speedup, and this is the assertion that would catch it
        assertEquals(closedBefore.get("fp"), open.get("fp"),
                "withScan must not change what the resolver answers");
        assertFalse(String.valueOf(open.get("fp")).contains("MISMATCH"),
                "resolveAll/resolve disagreed inside a scan");

        // 2. the work actually drops. The bound is deliberately loose (10x, against ~20x measured
        //    on this fixture and 250x+ on a real enterprise page) so it fails only if the memo has
        //    stopped memoizing — the thing worth a regression test — and not on Chrome-version drift.
        assertTrue(stylesOpen * 10 < stylesClosed,
                "a scan must cut the style reads by an order of magnitude: " + stylesClosed + " -> " + stylesOpen);

        // 3. a closed scan costs exactly what it cost before one was ever opened. This is the
        //    guarantee for every caller that never opens a scan — OSS driver.click()/waitFor() —
        //    and it is also how a leaked memo (state surviving the bracket) would show up.
        assertEquals(stylesClosed, stylesAfter,
                "a closed scan must do the same work it always did — no residue from the open one");
        assertEquals(closedBefore.get("fp"), closedAfter.get("fp"));
    }

    @Test
    void testScanClosesOnTheWayOutEvenWhenTheCallbackThrows() {
        driver.setUrl("data:text/html,<b id='one'>Status</b>");
        Object verdict = driver.script("(function(){"
                + " var k = window.__kjs;"
                + " if (k.inScan()) return 'open before';"
                + " var inside = k.withScan(function(){ return k.inScan(); });"
                + " if (!inside) return 'not open inside';"
                + " if (k.inScan()) return 'still open after return';"
                // nesting re-uses the outer bracket rather than opening a second one
                + " var nested = k.withScan(function(){ return k.withScan(function(){ return k.inScan(); }) && k.inScan(); });"
                + " if (!nested) return 'nesting broke the bracket';"
                + " if (k.inScan()) return 'still open after nesting';"
                + " try { k.withScan(function(){ throw new Error('boom'); }); } catch (e) { /* expected */ }"
                + " return k.inScan() ? 'LEAKED after throw' : 'ok'; })()");
        assertEquals("ok", String.valueOf(verdict));
    }

    @Test
    void testScanSeesTheSameHiddenAndShadowTextTheClosedPathDoes() {
        // the two shapes the memo could plausibly get wrong: text excluded because an ancestor is
        // hidden, and text that only exists inside a shadow root (getVisibleText's fallback)
        driver.setUrl("data:text/html,"
                + "<div id='vis'><span>Shown</span><span style='display:none'>Gone</span></div>"
                + "<div id='off' style='visibility:hidden'><span>Invisible</span></div>"
                + "<div id='aria' aria-hidden='true'><span>Excluded</span></div>"
                + "<div id='host'></div>");
        driver.script("(function(){ var h = document.getElementById('host').attachShadow({mode:'open'});"
                + " h.innerHTML = \"<b>Shadowed</b>\"; })()");
        Object verdict = driver.script("(function(){"
                + " var k = window.__kjs;"
                + " var read = function(){ return ['vis','off','aria','host'].map(function(id){"
                + "   var el = document.getElementById(id);"
                + "   return id + '=' + k.getVisibleText(el) + '/' + k.isVisible(el); }).join('|'); };"
                + " var closed = read();"
                + " var open = k.withScan(read);"
                + " var closedAgain = read();"
                + " if (closed !== open) return 'open differs: ' + closed + ' vs ' + open;"
                + " if (closed !== closedAgain) return 'residue: ' + closed + ' vs ' + closedAgain;"
                + " return closed; })()");
        assertEquals("vis=Shown/true|off=/false|aria=/false|host=Shadowed/true", String.valueOf(verdict));
    }

}
