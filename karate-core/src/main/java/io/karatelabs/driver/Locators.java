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

import io.karatelabs.js.JsFunction;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for transforming locators into JavaScript expressions.
 * Supports CSS selectors, XPath, and wildcard locators.
 *
 * <p>Locator types:</p>
 * <ul>
 *   <li>CSS selector: "#id", ".class", "tag", "tag.class"</li>
 *   <li>XPath: "//div", "./span", "../parent", "(//div)[2]"</li>
 *   <li>Wildcard: "{div}text", "{^div}partial", "{tag:2}text", "{:2}text"</li>
 *   <li>Pure JS: "(expression)" - passed through unchanged</li>
 * </ul>
 */
public class Locators {

    private static final String DOCUMENT = "document";

    // XPath prefixes for detection
    private static final Set<String> XPATH_PREFIXES = Set.of("/", "./", "../", "(//");

    // Pre-compiled pattern for wildcard locators: {^tag:index}text
    private static final Pattern WILDCARD_PATTERN =
            Pattern.compile("^\\{(\\^)?([^:}]*)?(?::(\\d+))?}(.*)$");

    // ========== Reusable JS Functions ==========

    public static final String SCROLL_JS_FUNCTION =
            "function(e){ var d = window.getComputedStyle(e).display;" +
                    " while(d == 'none'){ e = e.parentElement; d = window.getComputedStyle(e).display }" +
                    " e.scrollIntoView({block: 'center'}) }";

    // ========== Main Selector Transformation ==========

    /**
     * Transform a locator into a JavaScript expression that returns a single element.
     *
     * @param locator CSS selector, XPath, wildcard, or JS expression
     * @return JavaScript expression
     */
    public static String selector(String locator) {
        return selector(locator, DOCUMENT);
    }

    /**
     * Transform a locator into a JavaScript expression with a context node.
     *
     * @param locator     CSS selector, XPath, wildcard, or JS expression
     * @param contextNode JavaScript expression for the context node
     * @return JavaScript expression
     */
    public static String selector(String locator, String contextNode) {
        if (locator == null || locator.isEmpty()) {
            throw new DriverException("locator cannot be null or empty");
        }

        // Pure JS expression - pass through (but not XPath starting with parenthesis)
        if (locator.startsWith("(") && !locator.startsWith("(//")) {
            return locator;
        }

        // Wildcard: {div}text or {^div}partial or {tag:2}text
        // Returns JS expression directly (no further processing needed)
        if (locator.startsWith("{")) {
            return expandWildcard(locator);
        }

        // XPath
        if (isXpath(locator)) {
            return xpathSelector(locator, contextNode);
        }

        // CSS selector — use shadow DOM fallback when context is document
        String escaped = escapeForJs(locator);
        if (DOCUMENT.equals(contextNode)) {
            return "(window.__kjs && window.__kjs.qsDeep ? window.__kjs.qsDeep(\"" + escaped + "\") : document.querySelector(\"" + escaped + "\"))";
        }
        return contextNode + ".querySelector(\"" + escaped + "\")";
    }

    /**
     * Check if a locator is an XPath expression.
     */
    public static boolean isXpath(String locator) {
        for (String prefix : XPATH_PREFIXES) {
            if (locator.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // ========== Wildcard Locator Expansion ==========

    /**
     * Expand wildcard locator to JavaScript resolver call.
     * Uses browser-side JS that matches the same logic as locator generation.
     * <ul>
     *   <li>{tag}text     → window.__kjs.resolve('tag', 'text', 1, false)</li>
     *   <li>{^tag}text    → window.__kjs.resolve('tag', 'text', 1, true)</li>
     *   <li>{tag:2}text   → window.__kjs.resolve('tag', 'text', 2, false)</li>
     *   <li>{:2}text      → window.__kjs.resolve('*', 'text', 2, false)</li>
     * </ul>
     */
    public static String expandWildcard(String locator) {
        Matcher m = WILDCARD_PATTERN.matcher(locator);
        if (!m.matches()) {
            throw new DriverException("bad wildcard locator: " + locator);
        }

        boolean contains = m.group(1) != null;  // ^ prefix
        String tagPart = m.group(2);
        String indexPart = m.group(3);
        String text = m.group(4);

        String tag = (tagPart == null || tagPart.isEmpty()) ? "*" : tagPart;
        int index = (indexPart != null) ? Integer.parseInt(indexPart) : 1;

        // Build JS resolver call
        String escapedTag = escapeForJs(tag);
        String escapedText = escapeForJs(text);
        return "window.__kjs.resolve(\"" + escapedTag + "\", \"" + escapedText + "\", " + index + ", " + contains + ")";
    }

    // ========== XPath Selector ==========

    private static String xpathSelector(String xpath, String contextNode) {
        // Handle indexed wildcard: /(...)[n]
        if (xpath.startsWith("/(")) {
            if (DOCUMENT.equals(contextNode)) {
                xpath = xpath.substring(1);
            } else {
                xpath = "(." + xpath.substring(2);
            }
        } else if (!DOCUMENT.equals(contextNode) && !xpath.startsWith(".")) {
            // Make XPath relative to context node
            xpath = "." + xpath;
        }

        String escapedXpath = escapeForJs(xpath);

        // XPathResult.FIRST_ORDERED_NODE_TYPE = 9
        return "document.evaluate(\"" + escapedXpath + "\", " + contextNode +
                ", null, 9, null).singleNodeValue";
    }

    // ========== selectorAll for Multiple Elements ==========

    /**
     * Transform a locator into a JavaScript expression that returns all matching elements.
     */
    public static String selectorAll(String locator) {
        return selectorAll(locator, DOCUMENT);
    }

    /**
     * Transform a locator into a JavaScript expression that returns all matching elements.
     */
    public static String selectorAll(String locator, String contextNode) {
        if (locator == null || locator.isEmpty()) {
            throw new DriverException("locator cannot be null or empty");
        }

        // Wildcard returns single element wrapped in array
        if (locator.startsWith("{")) {
            String js = expandWildcard(locator);
            return "(function(){ var e = " + js + "; return e ? [e] : [] })()";
        }

        if (isXpath(locator)) {
            // Make XPath relative to contextNode
            if (!DOCUMENT.equals(contextNode) && !locator.startsWith(".")) {
                locator = "." + locator;
            }
            // XPathResult.ORDERED_NODE_ITERATOR_TYPE = 5
            String escapedXpath = escapeForJs(locator);
            return "document.evaluate(\"" + escapedXpath + "\", " + contextNode +
                    ", null, 5, null)";
        }

        // CSS selector — use shadow DOM fallback when context is document
        String escaped = escapeForJs(locator);
        if (DOCUMENT.equals(contextNode)) {
            return "(window.__kjs && window.__kjs.qsaDeep ? window.__kjs.qsaDeep(\"" + escaped + "\") : document.querySelectorAll(\"" + escaped + "\"))";
        }
        return contextNode + ".querySelectorAll(\"" + escaped + "\")";
    }

    // ========== IIFE Wrapper ==========

    /**
     * Wrap JavaScript in an IIFE (Immediately Invoked Function Expression).
     */
    public static String wrapInFunctionInvoke(String js) {
        return "(function(){ " + js + " })()";
    }

    // ========== Shorthand Expression to Function ==========

    /**
     * Convert shorthand expression to a function.
     * <ul>
     *   <li>JsFunction: arrow functions like {@code _ => _.value} are serialized to source</li>
     *   <li>"_" expressions: "_.value" → _ => _.value</li>
     *   <li>"!" expressions: "!_.disabled" → _ => !_.disabled</li>
     * </ul>
     */
    public static String toFunction(Object expression) {
        if (expression == null) {
            return "_ => _";  // identity function
        }
        // Arrow functions passed directly: _ => _.value
        if (expression instanceof JsFunction fn) {
            String source = fn.getSource();
            if (source != null) {
                return source;
            }
        }
        // String expressions - convert to arrow functions
        String expr = expression.toString();
        if (expr.isEmpty()) {
            return "_ => _";
        }
        // Already an arrow function or full function
        if (expr.contains("=>") || expr.startsWith("function")) {
            return expr;
        }
        char first = expr.charAt(0);
        if (first == '_' || first == '!') {
            return "_ => " + expr;
        }
        return expr;
    }

    // ========== Script Execution Helpers ==========

    /**
     * Execute a function on a single element found by locator.
     * Expression can be a String or JsFunction (arrow function).
     */
    public static String scriptSelector(String locator, Object expression) {
        return scriptSelector(locator, expression, DOCUMENT);
    }

    /**
     * Execute a function on a single element found by locator with context.
     * Expression can be a String or JsFunction (arrow function).
     */
    public static String scriptSelector(String locator, Object expression, String contextNode) {
        String js = "var fun = " + toFunction(expression) +
                "; var e = " + selector(locator, contextNode) +
                "; return fun(e)";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Execute a function on all elements matching locator.
     * Expression can be a String or JsFunction (arrow function).
     */
    public static String scriptAllSelector(String locator, Object expression) {
        return scriptAllSelector(locator, expression, DOCUMENT);
    }

    /**
     * Execute a function on all elements matching locator with context.
     * Expression can be a String or JsFunction (arrow function).
     */
    public static String scriptAllSelector(String locator, Object expression, String contextNode) {
        // Wildcard returns single element - apply function to it
        if (locator.startsWith("{")) {
            String resolverJs = expandWildcard(locator);
            String js = "var res = []; var fun = " + toFunction(expression) + "; var e = " + resolverJs + "; ";
            js += "if (e) res.push(fun(e)); return res";
            return wrapInFunctionInvoke(js);
        }

        boolean isXpathLocator = isXpath(locator);

        // Make XPath relative to contextNode
        if (isXpathLocator && !DOCUMENT.equals(contextNode) && !locator.startsWith(".")) {
            locator = "." + locator;
        }

        String escaped = escapeForJs(locator);
        String selectorExpr;
        if (isXpathLocator) {
            selectorExpr = "document.evaluate(\"" + escaped + "\", " + contextNode + ", null, 5, null)";
        } else if (DOCUMENT.equals(contextNode)) {
            selectorExpr = "(window.__kjs && window.__kjs.qsaDeep ? window.__kjs.qsaDeep(\"" + escaped + "\") : document.querySelectorAll(\"" + escaped + "\"))";
        } else {
            selectorExpr = contextNode + ".querySelectorAll(\"" + escaped + "\")";
        }

        String js = "var res = []; var fun = " + toFunction(expression) + "; var es = " + selectorExpr + "; ";
        if (isXpathLocator) {
            js += "var e = null; while(e = es.iterateNext()) res.push(fun(e)); return res";
        } else {
            js += "es.forEach(function(e){ res.push(fun(e)) }); return res";
        }
        return wrapInFunctionInvoke(js);
    }

    // ========== UI Helper Functions ==========

    /**
     * Generate JS to highlight an element.
     */
    public static String highlight(String locator, int millis) {
        String fn = "function(e){ var old = e.getAttribute('style');" +
                " e.setAttribute('style', 'background: yellow; border: 2px solid red;');" +
                " setTimeout(function(){ e.setAttribute('style', old || '') }, " + millis + ") }";
        String js = "var e = " + selector(locator) + "; var fun = " + fn + "; fun(e)";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to select a dropdown option.
     * Dispatches both 'input' and 'change' events with bubbles:true for framework compatibility.
     * <ul>
     *   <li>"{}" prefix: select by exact text only</li>
     *   <li>"{^}" prefix: select by text contains</li>
     *   <li>otherwise: try value first, then fall back to text match</li>
     * </ul>
     */
    public static String optionSelector(String locator, String text) {
        boolean textEquals = text.startsWith("{}");
        boolean textContains = text.startsWith("{^}");
        String condition;
        if (textEquals || textContains) {
            text = text.substring(text.indexOf('}') + 1);
            condition = textContains
                    ? "e.options[i].text.indexOf(t) !== -1"
                    : "e.options[i].text === t";
        } else {
            // Try value first, then fall back to text match
            condition = "e.options[i].value === t || e.options[i].text === t";
        }
        String escapedText = escapeForJs(text);
        String js = "var e = " + selector(locator) + "; var t = \"" + escapedText + "\";" +
                " for (var i = 0; i < e.options.length; ++i)" +
                " if (" + condition + ") { e.options[i].selected = true; break }" +
                " e.dispatchEvent(new Event('input', {bubbles: true}));" +
                " e.dispatchEvent(new Event('change', {bubbles: true}))";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to get element position.
     */
    public static String getPositionJs(String locator) {
        String js = "var r = " + selector(locator) + ".getBoundingClientRect();" +
                " var dx = window.scrollX; var dy = window.scrollY;" +
                " return { x: r.x + dx, y: r.y + dy, width: r.width, height: r.height }";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to focus an element with cursor at end.
     */
    public static String focusJs(String locator) {
        return "var e = " + selector(locator) +
                "; e.focus(); try { e.selectionStart = e.selectionEnd = e.value.length } catch(x) {}";
    }

    /**
     * Generate JS to click an element.
     */
    public static String clickJs(String locator) {
        return selector(locator) + ".click()";
    }

    /**
     * Generate JS to scroll an element into view.
     */
    public static String scrollJs(String locator) {
        String js = "var e = " + selector(locator) + "; var fun = " + SCROLL_JS_FUNCTION + "; fun(e)";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to set the value of an input element with events.
     */
    public static String inputJs(String locator, String value) {
        String escapedValue = escapeForJs(value);
        String js = "var e = " + selector(locator) + ";" +
                " e.focus();" +
                " e.value = \"" + escapedValue + "\";" +
                " e.dispatchEvent(new Event('input', { bubbles: true }));" +
                " e.dispatchEvent(new Event('change', { bubbles: true }))";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to clear an input element.
     */
    public static String clearJs(String locator) {
        String js = "var e = " + selector(locator) + ";" +
                " e.focus();" +
                " e.value = '';" +
                " e.dispatchEvent(new Event('input', { bubbles: true }));" +
                " e.dispatchEvent(new Event('change', { bubbles: true }))";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to get text content of an element.
     */
    public static String textJs(String locator) {
        String js = "var e = " + selector(locator) + "; return e ? e.textContent : null";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to get the value of an input element.
     */
    public static String valueJs(String locator) {
        String js = "var e = " + selector(locator) + "; return e ? e.value : null";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to get an attribute of an element.
     */
    public static String attributeJs(String locator, String name) {
        String js = "var e = " + selector(locator) + "; return e ? e.getAttribute(\"" + escapeForJs(name) + "\") : null";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to get a property of an element.
     */
    public static String propertyJs(String locator, String name) {
        String js = "var e = " + selector(locator) + "; return e ? e[\"" + escapeForJs(name) + "\"] : null";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to check if element is enabled.
     */
    public static String enabledJs(String locator) {
        String js = "var e = " + selector(locator) + "; return e ? !e.disabled : false";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to check if element exists.
     */
    public static String existsJs(String locator) {
        String js = "return " + selector(locator) + " !== null";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to get outer HTML of an element.
     */
    public static String outerHtmlJs(String locator) {
        String js = "var e = " + selector(locator) + "; return e ? e.outerHTML : null";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to get inner HTML of an element.
     */
    public static String innerHtmlJs(String locator) {
        String js = "var e = " + selector(locator) + "; return e ? e.innerHTML : null";
        return wrapInFunctionInvoke(js);
    }

    /**
     * Generate JS to find all matching elements and return their unique selectors.
     * Returns an array of CSS selectors that can be used to re-locate each element.
     */
    public static String findAllJs(String locator) {
        // For CSS selectors, generate nth-of-type selectors
        if (!isXpath(locator) && !locator.startsWith("{")) {
            String escaped = escapeForJs(locator);
            String js = "var els = (window.__kjs && window.__kjs.qsaDeep ? window.__kjs.qsaDeep(\"" + escaped + "\") : document.querySelectorAll(\"" + escaped + "\"));" +
                    " var result = [];" +
                    " els.forEach(function(el, i) {" +
                    "   result.push(\"" + escaped + ":nth-of-type(\" + (i+1) + \")\");" +
                    " });" +
                    " return result";
            return wrapInFunctionInvoke(js);
        }
        // Wildcard - returns single element as array
        if (locator.startsWith("{")) {
            String resolverJs = expandWildcard(locator);
            return wrapInFunctionInvoke("var e = " + resolverJs + "; return e ? ['" + escapeForJs(locator) + "'] : []");
        }
        // XPath - less common, return locator wrapped
        return wrapInFunctionInvoke("return ['" + escapeForJs(locator) + "']");
    }

    /**
     * Generate JS to count matching elements.
     */
    public static String countJs(String locator) {
        // Wildcard returns single element - count is 0 or 1
        if (locator.startsWith("{")) {
            String resolverJs = expandWildcard(locator);
            return wrapInFunctionInvoke("return " + resolverJs + " ? 1 : 0");
        }
        if (isXpath(locator)) {
            String escapedXpath = escapeForJs(locator);
            String js = "var iter = document.evaluate(\"" + escapedXpath + "\", document, null, 5, null);" +
                    " var count = 0; while(iter.iterateNext()) count++; return count";
            return wrapInFunctionInvoke(js);
        }
        String escaped = escapeForJs(locator);
        return "(window.__kjs && window.__kjs.qsaDeep ? window.__kjs.qsaDeep(\"" + escaped + "\") : document.querySelectorAll(\"" + escaped + "\")).length";
    }

    // ========== String Escaping ==========

    /**
     * Escape a string for embedding in a JavaScript double-quoted string.
     */
    public static String escapeForJs(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Escape a string for use in XPath.
     * Handles strings containing single quotes, double quotes, or both.
     */
    public static String escapeXpathString(String text) {
        if (text == null) {
            return "''";
        }
        if (!text.contains("'")) {
            return "'" + text + "'";
        }
        if (!text.contains("\"")) {
            return "\"" + text + "\"";
        }
        // Contains both - use concat()
        return "concat('" + text.replace("'", "',\"'\",'") + "')";
    }

}
