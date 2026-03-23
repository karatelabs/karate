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

import java.util.List;
import java.util.Map;

/**
 * Core driver interface defining the primitive operations.
 *
 * <p>All driver operations reduce to a small set of primitives, with {@code eval(js)}
 * being THE foundational primitive. This interface documents the minimal set of
 * methods that require backend-specific implementation (CDP, WebDriver, etc.).</p>
 *
 * <h2>Tier 1: Essential Primitives</h2>
 * <ul>
 *   <li>{@link #eval(String)} - Execute JavaScript (Runtime.evaluate) - THE core primitive</li>
 *   <li>{@link #navigate(String)} - Navigate to URL (Page.navigate)</li>
 *   <li>{@link #keys()} - Keyboard input (Input.dispatchKeyEvent)</li>
 *   <li>{@link #screenshot()} - Capture screenshot (Page.captureScreenshot)</li>
 *   <li>{@link #frame(Object)} - Switch frames (Page.getFrameTree + createIsolatedWorld)</li>
 *   <li>{@link #dialog(boolean, String)} - Handle JS dialogs (Page.handleJavaScriptDialog)</li>
 * </ul>
 *
 * <h2>Tier 2: Extended Primitives</h2>
 * <ul>
 *   <li>{@link #mouse()} - Mouse input (Input.dispatchMouseEvent)</li>
 *   <li>{@link #pdf(Map)} - Generate PDF (Page.printToPDF)</li>
 *   <li>{@link #cookie(String)}, {@link #cookie(Map)} - Cookie operations (Network.*)</li>
 *   <li>{@link #intercept(List, InterceptHandler)} - Request interception (Fetch.*)</li>
 *   <li>{@link #window(String)} - Window management (Browser.*)</li>
 *   <li>{@link #tab(Object)} - Tab/page switching (Target.*)</li>
 * </ul>
 *
 * <p>All other Driver methods (click, text, waitFor, etc.) are convenience methods
 * that delegate to these primitives, primarily to {@code eval()}.</p>
 *
 * @see Driver
 */
public interface CoreDriver {

    // ========== Tier 1: Essential Primitives ==========

    /**
     * Execute JavaScript and return result.
     * This is THE foundational primitive - virtually all element operations
     * generate JavaScript that is executed via this method.
     *
     * <p>Examples:</p>
     * <pre>
     * eval("document.title")                           // get page title
     * eval("document.querySelector('#btn').click()")   // click element
     * eval("document.querySelector('input').value")    // get input value
     * </pre>
     *
     * @param js JavaScript expression to execute
     * @return the result of the JavaScript execution
     */
    Object eval(String js);

    /**
     * Navigate to URL and wait for page load.
     * Maps to CDP Page.navigate.
     *
     * @param url the URL to navigate to
     */
    void navigate(String url);

    /**
     * Get a Keys object for keyboard input.
     * Maps to CDP Input.dispatchKeyEvent.
     *
     * @return the Keys object for typing and key events
     */
    Keys keys();

    /**
     * Take screenshot and return PNG bytes.
     * Maps to CDP Page.captureScreenshot.
     *
     * @return PNG image bytes
     */
    byte[] screenshot();

    /**
     * Switch to a frame.
     * Maps to CDP Page.getFrameTree + Page.createIsolatedWorld.
     *
     * @param target frame identifier:
     *               - {@code null} to return to main frame
     *               - {@code Integer} for frame index
     *               - {@code String} for frame locator
     */
    void frame(Object target);

    /**
     * Handle JavaScript dialog (alert, confirm, prompt, beforeunload).
     * Maps to CDP Page.handleJavaScriptDialog.
     *
     * @param accept true to accept (OK), false to dismiss (Cancel)
     * @param input text for prompt dialogs (ignored if accept is false)
     */
    void dialog(boolean accept, String input);

    // ========== Tier 2: Extended Primitives ==========

    /**
     * Get a Mouse object for mouse input.
     * Maps to CDP Input.dispatchMouseEvent.
     *
     * @return a new Mouse object at position (0, 0)
     */
    Mouse mouse();

    /**
     * Generate a PDF of the current page.
     * Maps to CDP Page.printToPDF.
     *
     * @param options PDF options (scale, landscape, margins, etc.), or null for defaults
     * @return PDF bytes
     */
    byte[] pdf(Map<String, Object> options);

    /**
     * Get a cookie by name.
     * Maps to CDP Network.getCookies.
     *
     * @param name the cookie name
     * @return the cookie as a Map, or null if not found
     */
    Map<String, Object> cookie(String name);

    /**
     * Set a cookie.
     * Maps to CDP Network.setCookie.
     *
     * @param cookie the cookie as a Map with keys: name, value, domain, path, etc.
     */
    void cookie(Map<String, Object> cookie);

    /**
     * Enable request interception with a handler.
     * Maps to CDP Fetch.enable + Fetch.requestPaused handling.
     *
     * @param patterns URL patterns to intercept (e.g., "*api/*")
     * @param handler the intercept handler (return InterceptResponse to mock, null to continue)
     */
    void intercept(List<String> patterns, InterceptHandler handler);

    /**
     * Perform window management operation.
     * Maps to CDP Browser.setWindowBounds.
     *
     * @param operation one of: "maximize", "minimize", "fullscreen", "normal"
     */
    void window(String operation);

    /**
     * Switch to a different tab/page.
     * Maps to CDP Target.activateTarget + Target.attachToTarget.
     *
     * @param target tab identifier:
     *               - {@code Integer} for tab index
     *               - {@code String} for title or URL substring match
     */
    void tab(Object target);

    // ========== Lifecycle ==========

    /**
     * Close driver and browser.
     */
    void quit();

    /**
     * Check if driver is terminated.
     */
    boolean isTerminated();

    /**
     * Get driver options.
     */
    DriverOptions getOptions();

}
