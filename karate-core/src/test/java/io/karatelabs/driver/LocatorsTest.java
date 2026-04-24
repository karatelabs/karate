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
package io.karatelabs.driver;

import io.karatelabs.js.Engine;
import io.karatelabs.js.JsFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocatorsTest {

    // ========== CSS Selector Tests ==========

    @Test
    void testCssSelectorById() {
        String result = Locators.selector("#myId");
        // Uses shadow DOM fallback when context is document
        assertEquals("(window.__kjs && window.__kjs.qsDeep ? window.__kjs.qsDeep(\"#myId\") : document.querySelector(\"#myId\"))", result);
    }

    @Test
    void testCssSelectorByClass() {
        String result = Locators.selector(".myClass");
        assertEquals("(window.__kjs && window.__kjs.qsDeep ? window.__kjs.qsDeep(\".myClass\") : document.querySelector(\".myClass\"))", result);
    }

    @Test
    void testCssSelectorByTag() {
        String result = Locators.selector("button");
        assertEquals("(window.__kjs && window.__kjs.qsDeep ? window.__kjs.qsDeep(\"button\") : document.querySelector(\"button\"))", result);
    }

    @Test
    void testCssSelectorComplex() {
        String result = Locators.selector("div.container > p.text");
        assertEquals("(window.__kjs && window.__kjs.qsDeep ? window.__kjs.qsDeep(\"div.container > p.text\") : document.querySelector(\"div.container > p.text\"))", result);
    }

    @Test
    void testCssSelectorWithContext() {
        String result = Locators.selector("#child", "parentElement");
        assertEquals("parentElement.querySelector(\"#child\")", result);
    }

    // ========== XPath Selector Tests ==========

    @Test
    void testIsXpathSlash() {
        assertTrue(Locators.isXpath("//div"));
        assertTrue(Locators.isXpath("/html/body"));
    }

    @Test
    void testIsXpathRelative() {
        assertTrue(Locators.isXpath("./span"));
        assertTrue(Locators.isXpath("../parent"));
    }

    @Test
    void testIsXpathParenthesized() {
        assertTrue(Locators.isXpath("(//div)[1]"));
    }

    @Test
    void testIsNotXpath() {
        assertFalse(Locators.isXpath("#id"));
        assertFalse(Locators.isXpath(".class"));
        assertFalse(Locators.isXpath("div"));
    }

    @Test
    void testXpathSelector() {
        String result = Locators.selector("//div[@id='test']");
        assertTrue(result.contains("document.evaluate"));
        assertTrue(result.contains("//div[@id='test']"));
        assertTrue(result.contains("9, null"));  // FIRST_ORDERED_NODE_TYPE
    }

    @Test
    void testXpathSelectorWithContext() {
        String result = Locators.selector("//span", "parentElement");
        assertTrue(result.contains(".//span"));  // Should be made relative
        assertTrue(result.contains("parentElement"));
    }

    @Test
    void testXpathRelativeSelector() {
        String result = Locators.selector("./child", "parentElement");
        assertTrue(result.contains("./child"));  // Already relative
        assertTrue(result.contains("parentElement"));
    }

    // ========== Wildcard Locator Tests ==========

    @Test
    void testWildcardExact() {
        String result = Locators.expandWildcard("{div}Login");
        assertEquals("window.__kjs.resolve(\"div\", \"Login\", 1, false)", result);
    }

    @Test
    void testWildcardContains() {
        String result = Locators.expandWildcard("{^div}Log");
        assertEquals("window.__kjs.resolve(\"div\", \"Log\", 1, true)", result);
    }

    @Test
    void testWildcardAnyTag() {
        String result = Locators.expandWildcard("{}Submit");
        assertEquals("window.__kjs.resolve(\"*\", \"Submit\", 1, false)", result);
    }

    @Test
    void testWildcardWithIndex() {
        String result = Locators.expandWildcard("{div:2}Item");
        assertEquals("window.__kjs.resolve(\"div\", \"Item\", 2, false)", result);
    }

    @Test
    void testWildcardAnyTagWithIndex() {
        String result = Locators.expandWildcard("{:3}Option");
        assertEquals("window.__kjs.resolve(\"*\", \"Option\", 3, false)", result);
    }

    @Test
    void testWildcardContainsWithIndex() {
        String result = Locators.expandWildcard("{^span:1}Click");
        assertEquals("window.__kjs.resolve(\"span\", \"Click\", 1, true)", result);
    }

    @Test
    void testWildcardToSelector() {
        // Wildcard selector returns JS resolver call directly
        String result = Locators.selector("{button}Submit");
        assertEquals("window.__kjs.resolve(\"button\", \"Submit\", 1, false)", result);
    }

    @Test
    void testWildcardBadFormat() {
        assertThrows(DriverException.class, () -> {
            Locators.expandWildcard("{no-closing-brace");
        });
    }

    @Test
    void testExpandWildcardReturnsJsResolver() {
        // Basic wildcard
        assertEquals("window.__kjs.resolve(\"div\", \"Save\", 1, false)",
            Locators.expandWildcard("{div}Save"));
        // Contains syntax
        assertEquals("window.__kjs.resolve(\"div\", \"Sav\", 1, true)",
            Locators.expandWildcard("{^div}Sav"));
        // With index
        assertEquals("window.__kjs.resolve(\"button\", \"Submit\", 2, false)",
            Locators.expandWildcard("{button:2}Submit"));
        // Any element
        assertEquals("window.__kjs.resolve(\"*\", \"Click\", 1, false)",
            Locators.expandWildcard("{}Click"));
    }

    // ========== XPath Quote Escaping (still used for non-wildcard XPath) ==========

    @Test
    void testEscapeXpathStringSimple() {
        assertEquals("'hello'", Locators.escapeXpathString("hello"));
    }

    @Test
    void testEscapeXpathStringSingleQuote() {
        assertEquals("\"it's\"", Locators.escapeXpathString("it's"));
    }

    @Test
    void testEscapeXpathStringDoubleQuote() {
        assertEquals("'say \"hi\"'", Locators.escapeXpathString("say \"hi\""));
    }

    @Test
    void testEscapeXpathStringBothQuotes() {
        String result = Locators.escapeXpathString("it's \"complex\"");
        assertTrue(result.startsWith("concat("));
    }

    // ========== JS Expression Passthrough ==========

    @Test
    void testPureJsPassthrough() {
        String js = "(document.getElementById('test'))";
        assertEquals(js, Locators.selector(js));
    }

    @Test
    void testParenthesizedXpathNotPassedThrough() {
        String xpath = "(//div)[1]";
        String result = Locators.selector(xpath);
        assertTrue(result.contains("document.evaluate"));
    }

    // ========== selectorAll Tests ==========

    @Test
    void testSelectorAllCss() {
        String result = Locators.selectorAll("div.item");
        // Uses shadow DOM fallback when context is document
        assertEquals("(window.__kjs && window.__kjs.qsaDeep ? window.__kjs.qsaDeep(\"div.item\") : document.querySelectorAll(\"div.item\"))", result);
    }

    @Test
    void testSelectorAllXpath() {
        String result = Locators.selectorAll("//div[@class='item']");
        assertTrue(result.contains("document.evaluate"));
        assertTrue(result.contains("5, null"));  // ORDERED_NODE_ITERATOR_TYPE
    }

    @Test
    void testSelectorAllWildcard() {
        // Wildcard selectorAll wraps single result in array
        String result = Locators.selectorAll("{li}Option");
        assertTrue(result.contains("window.__kjs.resolve(\"li\", \"Option\", 1, false)"));
        assertTrue(result.contains("return e ? [e] : []"));
    }

    // ========== toFunction Tests ==========

    @Test
    void testToFunctionIdentity() {
        // Now uses arrow function syntax
        assertEquals("_ => _", Locators.toFunction(null));
        assertEquals("_ => _", Locators.toFunction(""));
    }

    @Test
    void testToFunctionUnderscore() {
        // Now uses arrow function syntax
        assertEquals("_ => _.value", Locators.toFunction("_.value"));
        assertEquals("_ => _.textContent", Locators.toFunction("_.textContent"));
    }

    @Test
    void testToFunctionNegation() {
        // Now uses arrow function syntax
        assertEquals("_ => !_.disabled", Locators.toFunction("!_.disabled"));
    }

    @Test
    void testToFunctionArrow() {
        String arrow = "e => e.value";
        assertEquals(arrow, Locators.toFunction(arrow));
    }

    @Test
    void testToFunctionRegular() {
        String fn = "function(e){ return e.id }";
        assertEquals(fn, Locators.toFunction(fn));
    }

    @Test
    void testToFunctionWithJsFunction() {
        // Create a JsFunction using the JS engine
        Engine engine = new Engine();
        engine.eval("var fn = _ => _.value");
        Object fn = engine.getBindings().get("fn");
        assertTrue(fn instanceof JsFunction);
        assertEquals("_ => _.value", Locators.toFunction(fn));
    }

    @Test
    void testToFunctionWithJsFunctionBlockBody() {
        Engine engine = new Engine();
        engine.eval("var fn = () => { return document.title }");
        Object fn = engine.getBindings().get("fn");
        assertTrue(fn instanceof JsFunction);
        assertEquals("() => { return document.title }", Locators.toFunction(fn));
    }

    // ========== toFunction: Regression for #2803 ==========
    // Plain string expressions must pass through toFunction() untouched.
    // Driver.script(Object) only wraps in IIFE when the result contains "=>"
    // or starts with "function", so anything toFunction() returns unchanged
    // here reaches the browser verbatim.

    @Test
    void testToFunctionVoidMethodCallPassThrough() {
        // .click() / .focus() / .dispatchEvent() return undefined — the
        // string must not be rewritten into an arrow function form, or
        // the IIFE wrap would try to call undefined as a function.
        String voidClick = "document.querySelector('.my-tab').click()";
        assertEquals(voidClick, Locators.toFunction(voidClick));

        String voidFocus = "document.getElementById('username').focus()";
        assertEquals(voidFocus, Locators.toFunction(voidFocus));

        String voidDispatch = "document.getElementById('x').dispatchEvent(new Event('focus'))";
        assertEquals(voidDispatch, Locators.toFunction(voidDispatch));

        String voidSetItem = "sessionStorage.setItem('k', 'v')";
        assertEquals(voidSetItem, Locators.toFunction(voidSetItem));
    }

    @Test
    void testToFunctionStatementsPassThrough() {
        // var/let/const and semicolon-joined statements must reach the
        // browser as-is. If they were wrapped in "(...)" the parser would
        // reject them with "Unexpected token 'var'" etc.
        assertEquals("var x = 1; window.y = x", Locators.toFunction("var x = 1; window.y = x"));
        assertEquals("let x = 1; window.y = x", Locators.toFunction("let x = 1; window.y = x"));
        assertEquals("const x = 1; window.y = x", Locators.toFunction("const x = 1; window.y = x"));
        assertEquals("window.a = 1; window.b = 2", Locators.toFunction("window.a = 1; window.b = 2"));
    }

    @Test
    void testToFunctionCommaOperatorPassThrough() {
        // (expr1, expr2) must remain a comma expression, not get split into
        // separate arguments.
        assertEquals("(1, 2, 3)", Locators.toFunction("(1, 2, 3)"));
    }

    // ========== wrapInFunctionInvoke Tests ==========

    @Test
    void testWrapInFunctionInvoke() {
        String result = Locators.wrapInFunctionInvoke("return 42");
        assertEquals("(function(){ return 42 })()", result);
    }

    // ========== scriptSelector Tests ==========

    @Test
    void testScriptSelector() {
        String result = Locators.scriptSelector("#myInput", "_.value");
        assertTrue(result.startsWith("(function(){"));
        // Now uses arrow function syntax
        assertTrue(result.contains("var fun = _ => _.value"));
        assertTrue(result.contains("qsDeep(\"#myInput\")"));
        assertTrue(result.contains("return fun(e)"));
        assertTrue(result.endsWith("})()"));
    }

    @Test
    void testScriptAllSelector() {
        String result = Locators.scriptAllSelector("li", "_.textContent");
        assertTrue(result.startsWith("(function(){"));
        assertTrue(result.contains("querySelectorAll"));
        assertTrue(result.contains("forEach"));
    }

    @Test
    void testScriptAllSelectorXpath() {
        String result = Locators.scriptAllSelector("//li", "_.textContent");
        assertTrue(result.startsWith("(function(){"));
        assertTrue(result.contains("document.evaluate"));
        assertTrue(result.contains("iterateNext"));
    }

    // ========== UI Helper Tests ==========

    @Test
    void testHighlightJs() {
        String result = Locators.highlight("#btn", 3000);
        assertTrue(result.contains("qsDeep(\"#btn\")"));
        assertTrue(result.contains("background: yellow"));
        assertTrue(result.contains("border: 2px solid red"));
        assertTrue(result.contains("setTimeout"));
        assertTrue(result.contains("3000"));
    }

    @Test
    void testOptionSelectorByValue() {
        String result = Locators.optionSelector("#dropdown", "us");
        assertTrue(result.contains("e.options[i].value === t"));
    }

    @Test
    void testOptionSelectorByExactText() {
        String result = Locators.optionSelector("#dropdown", "{}United States");
        assertTrue(result.contains("e.options[i].text === t"));
        assertTrue(result.contains("United States"));
    }

    @Test
    void testOptionSelectorByTextContains() {
        String result = Locators.optionSelector("#dropdown", "{^}United");
        assertTrue(result.contains("e.options[i].text.indexOf(t) !== -1"));
    }

    @Test
    void testGetPositionJs() {
        String result = Locators.getPositionJs("#element");
        assertTrue(result.contains("getBoundingClientRect"));
        assertTrue(result.contains("scrollX"));
        assertTrue(result.contains("scrollY"));
        assertTrue(result.contains("width: r.width"));  // v1 bug fix: no scroll offset on dimensions
        assertTrue(result.contains("height: r.height"));
    }

    @Test
    void testFocusJs() {
        String result = Locators.focusJs("#input");
        assertTrue(result.contains("focus()"));
        assertTrue(result.contains("selectionStart"));
        assertTrue(result.contains("selectionEnd"));
    }

    @Test
    void testClickJs() {
        String result = Locators.clickJs("#btn");
        assertTrue(result.contains("qsDeep(\"#btn\")"));
        assertTrue(result.contains(".click()"));
    }

    @Test
    void testScrollJs() {
        String result = Locators.scrollJs("#element");
        assertTrue(result.contains("scrollIntoView"));
        assertTrue(result.contains("block: 'center'"));
    }

    @Test
    void testInputJs() {
        String result = Locators.inputJs("#name", "John Doe");
        assertTrue(result.contains("focus()"));
        assertTrue(result.contains("e.value = \"John Doe\""));
        assertTrue(result.contains("dispatchEvent"));
        assertTrue(result.contains("input"));
        assertTrue(result.contains("change"));
    }

    @Test
    void testClearJs() {
        String result = Locators.clearJs("#name");
        assertTrue(result.contains("e.value = ''"));
        assertTrue(result.contains("dispatchEvent"));
    }

    @Test
    void testTextJs() {
        String result = Locators.textJs("#content");
        assertTrue(result.contains("textContent"));
    }

    @Test
    void testValueJs() {
        String result = Locators.valueJs("#input");
        assertTrue(result.contains("e.value"));
    }

    @Test
    void testAttributeJs() {
        String result = Locators.attributeJs("#link", "href");
        assertTrue(result.contains("getAttribute(\"href\")"));
    }

    @Test
    void testPropertyJs() {
        String result = Locators.propertyJs("#checkbox", "checked");
        assertTrue(result.contains("e[\"checked\"]"));
    }

    @Test
    void testEnabledJs() {
        String result = Locators.enabledJs("#btn");
        assertTrue(result.contains("!e.disabled"));
    }

    @Test
    void testExistsJs() {
        String result = Locators.existsJs("#element");
        assertTrue(result.contains("!== null"));
    }

    @Test
    void testOuterHtmlJs() {
        String result = Locators.outerHtmlJs("#div");
        assertTrue(result.contains("outerHTML"));
    }

    @Test
    void testInnerHtmlJs() {
        String result = Locators.innerHtmlJs("#div");
        assertTrue(result.contains("innerHTML"));
    }

    @Test
    void testCountJs() {
        String result = Locators.countJs("li.item");
        assertTrue(result.contains("querySelectorAll"));
        assertTrue(result.contains(".length"));
    }

    @Test
    void testCountJsXpath() {
        String result = Locators.countJs("//li");
        assertTrue(result.contains("document.evaluate"));
        assertTrue(result.contains("while(iter.iterateNext())"));
    }

    // ========== findAllJs Tests ==========

    @Test
    void testFindAllJsCss() {
        String result = Locators.findAllJs("li.item");
        assertTrue(result.contains("querySelectorAll"));
        assertTrue(result.contains("forEach"));
        assertTrue(result.contains("nth-of-type"));
        assertTrue(result.contains("return result"));
    }

    @Test
    void testFindAllJsWildcard() {
        String result = Locators.findAllJs("{button}Submit");
        assertTrue(result.contains("window.__kjs.resolve"));
        assertTrue(result.contains("return e ? ["));
    }

    @Test
    void testFindAllJsXpath() {
        String result = Locators.findAllJs("//li");
        assertTrue(result.contains("return ["));
    }

    // ========== JS String Escaping Tests ==========

    @Test
    void testEscapeForJsBasic() {
        assertEquals("hello", Locators.escapeForJs("hello"));
    }

    @Test
    void testEscapeForJsQuotes() {
        assertEquals("say \\\"hello\\\"", Locators.escapeForJs("say \"hello\""));
    }

    @Test
    void testEscapeForJsBackslash() {
        assertEquals("path\\\\to\\\\file", Locators.escapeForJs("path\\to\\file"));
    }

    @Test
    void testEscapeForJsNewline() {
        assertEquals("line1\\nline2", Locators.escapeForJs("line1\nline2"));
    }

    @Test
    void testEscapeForJsCarriageReturn() {
        assertEquals("text\\r\\n", Locators.escapeForJs("text\r\n"));
    }

    @Test
    void testEscapeForJsTab() {
        assertEquals("col1\\tcol2", Locators.escapeForJs("col1\tcol2"));
    }

    @Test
    void testEscapeForJsNull() {
        assertEquals("", Locators.escapeForJs(null));
    }

    // ========== Shadow DOM Fallback Tests ==========

    @Test
    void testCssSelectorShadowFallback() {
        // Document context uses qsDeep shadow fallback
        String result = Locators.selector("[aria-label=\"shadow\"]");
        assertTrue(result.contains("window.__kjs.qsDeep"));
        assertTrue(result.contains("document.querySelector"));
    }

    @Test
    void testCssSelectorNoShadowFallbackWithContext() {
        // Non-document context uses plain querySelector (no shadow fallback)
        String result = Locators.selector("#child", "parentElement");
        assertEquals("parentElement.querySelector(\"#child\")", result);
        assertFalse(result.contains("qsDeep"));
    }

    @Test
    void testSelectorAllShadowFallback() {
        String result = Locators.selectorAll("button.shadow");
        assertTrue(result.contains("window.__kjs.qsaDeep"));
        assertTrue(result.contains("document.querySelectorAll"));
    }

    @Test
    void testSelectorAllNoShadowFallbackWithContext() {
        String result = Locators.selectorAll("span", "parentElement");
        assertEquals("parentElement.querySelectorAll(\"span\")", result);
        assertFalse(result.contains("qsaDeep"));
    }

    @Test
    void testCountJsShadowFallback() {
        String result = Locators.countJs("li.item");
        assertTrue(result.contains("qsaDeep"));
        assertTrue(result.contains(".length"));
    }

    @Test
    void testFindAllJsShadowFallback() {
        String result = Locators.findAllJs("li.item");
        assertTrue(result.contains("qsaDeep"));
        assertTrue(result.contains("nth-of-type"));
    }

    // ========== Navigation: closestJs / matchesJs ==========

    @Test
    void testClosestJsNullSafe() {
        String result = Locators.closestJs("#foo", ".row");
        // IIFE that guards against missing base element
        assertTrue(result.startsWith("(function()"), "should be a pure-JS IIFE: " + result);
        assertTrue(result.contains("e ? e.closest(\".row\") : null"));
        assertTrue(result.contains("document.querySelector(\"#foo\")")
                || result.contains("qsDeep(\"#foo\")"));
    }

    @Test
    void testClosestJsPassesThroughSelector() {
        // Pure-JS locators (starting with `(`) must pass through selector() unchanged
        // — otherwise the element ops pipeline would wrap/mangle them.
        String closestLocator = Locators.closestJs("#foo", ".row");
        assertEquals(closestLocator, Locators.selector(closestLocator));
    }

    @Test
    void testClosestJsEscapesQuotes() {
        // A selector containing a quote must not break out of the generated JS string.
        String result = Locators.closestJs("#foo", "[data-x=\"y\"]");
        assertFalse(result.contains("data-x=\"y\""),
                "unescaped double-quote would break the JS literal: " + result);
        assertTrue(result.contains("e.closest("));
    }

    @Test
    void testClosestJsChainable() {
        // e.closest('.row').closest('form') composes: outer IIFE wraps inner IIFE.
        String inner = Locators.closestJs("#foo", ".row");
        String outer = Locators.closestJs(inner, "form");
        assertTrue(outer.startsWith("(function()"));
        assertTrue(outer.contains(inner), "outer must embed inner IIFE");
    }

    @Test
    void testMatchesJsReturnsBooleanFalseWhenMissing() {
        String result = Locators.matchesJs("#foo", ".active");
        assertTrue(result.startsWith("(function()"));
        // Missing elements must report false rather than throwing on null.matches
        assertTrue(result.contains("e ? e.matches(\".active\") : false"));
    }

    @Test
    void testMatchesJsEscapesQuotes() {
        String result = Locators.matchesJs("#foo", "[data-x=\"y\"]");
        assertFalse(result.contains("data-x=\"y\""),
                "unescaped double-quote would break the JS literal: " + result);
        assertTrue(result.contains("e.matches("));
    }

    @Test
    void testMatchesJsComposesOverClosestLocator() {
        // closest returns a locator; matches on that locator is a valid composition.
        String closest = Locators.closestJs("#foo", ".row");
        String matches = Locators.matchesJs(closest, "tr");
        assertTrue(matches.contains(closest), "matches must embed the closest IIFE as its base");
        assertTrue(matches.contains("e.matches(\"tr\")"));
    }

    // ========== Scoped lookups off a pure-JS base (closest + locate/locateAll) ==========

    @Test
    void testScopedSelectorJsIsNullSafeAndIIFE() {
        String base = Locators.closestJs("#foo", "form");
        String scoped = Locators.scopedSelectorJs(base, "input");
        assertTrue(scoped.startsWith("(function()"));
        assertTrue(scoped.contains("__b ? __b.querySelector(\"input\") : null"));
        assertTrue(scoped.contains(base), "scoped selector must embed the base IIFE");
    }

    @Test
    void testScopedSelectorJsPassesThroughSelector() {
        // The result must itself be usable as a locator — pass through selector() unchanged.
        String base = Locators.closestJs("#foo", "form");
        String scoped = Locators.scopedSelectorJs(base, "input");
        assertEquals(scoped, Locators.selector(scoped));
    }

    @Test
    void testScopedCountJsReturnsZeroForMissingBase() {
        String base = Locators.closestJs("#foo", "form");
        String countJs = Locators.scopedCountJs(base, "input");
        assertTrue(countJs.contains("__b ? __b.querySelectorAll(\"input\").length : 0"));
    }

    @Test
    void testScopedIndexedSelectorJsBoundsChecks() {
        // Out-of-range index must return null, not throw on undefined.click() downstream.
        String base = Locators.closestJs("#foo", "form");
        String indexed = Locators.scopedIndexedSelectorJs(base, "input", 2);
        assertTrue(indexed.contains("all.length > 2 ? all[2] : null"));
        assertTrue(indexed.contains(base));
    }

    @Test
    void testScopedIndexedSelectorJsReResolvesBase() {
        // Each indexed locator must re-eval the base so later element ops tolerate
        // DOM mutations between count and access.
        String base = Locators.closestJs("#foo", "form");
        String i0 = Locators.scopedIndexedSelectorJs(base, "input", 0);
        String i1 = Locators.scopedIndexedSelectorJs(base, "input", 1);
        assertTrue(i0.contains(base));
        assertTrue(i1.contains(base));
        assertNotEquals(i0, i1);
    }

    @Test
    void testScopedHelpersEscapeQuotes() {
        String base = Locators.closestJs("#foo", "form");
        String scoped = Locators.scopedSelectorJs(base, "[data-x=\"y\"]");
        assertFalse(scoped.contains("data-x=\"y\""),
                "unescaped double-quote would break the JS literal: " + scoped);
    }

    // ========== Error Cases ==========

    @Test
    void testSelectorNullThrows() {
        assertThrows(DriverException.class, () -> Locators.selector(null));
    }

    @Test
    void testSelectorEmptyThrows() {
        assertThrows(DriverException.class, () -> Locators.selector(""));
    }

    @Test
    void testSelectorAllNullThrows() {
        assertThrows(DriverException.class, () -> Locators.selectorAll(null));
    }

    @Test
    void testSelectorAllEmptyThrows() {
        assertThrows(DriverException.class, () -> Locators.selectorAll(""));
    }

}
