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

import io.karatelabs.js.JavaCallable;
import io.karatelabs.js.SimpleObject;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Browser automation driver interface.
 * Provides a unified API for browser automation across different backends (CDP, Playwright, WebDriver).
 *
 * <p>This interface extends {@link CoreDriver} which defines the ~12 primitive operations.
 * Most methods in this interface are convenience methods that delegate to those primitives,
 * primarily to {@link CoreDriver#eval(String)}.</p>
 *
 * <p>Phase 8: Extracted from CdpDriver to enable multi-backend support.</p>
 * <p>Phase 9: Implements ObjectLike for Gherkin/DSL integration (JS property access).</p>
 * <p>Phase 10: Extends CoreDriver to clearly separate primitives from convenience methods.</p>
 *
 * @see CoreDriver
 */
public interface Driver extends CoreDriver, SimpleObject {

    // ========== ObjectLike Implementation (for JS property access) ==========

    /**
     * Get a property value or method by name for JavaScript access.
     * Properties return values directly; methods return JavaCallables.
     */
    @SuppressWarnings("unchecked")
    @Override
    default Object jsGet(String name) {
        return switch (name) {
            // Properties (direct values)
            case DriverApi.URL -> getUrl();
            case DriverApi.TITLE -> getTitle();
            case DriverApi.COOKIES -> getCookies();
            case DriverApi.DIMENSIONS -> getDimensions();

            // Element actions (return Element for chaining)
            case DriverApi.CLICK -> (JavaCallable) (ctx, args) ->
                    click(String.valueOf(args[0]));
            case DriverApi.INPUT -> (JavaCallable) (ctx, args) ->
                    input(String.valueOf(args[0]), args.length > 1 ? String.valueOf(args[1]) : "");
            case DriverApi.CLEAR -> (JavaCallable) (ctx, args) ->
                    clear(String.valueOf(args[0]));
            case DriverApi.FOCUS -> (JavaCallable) (ctx, args) ->
                    focus(String.valueOf(args[0]));
            case DriverApi.SCROLL -> (JavaCallable) (ctx, args) ->
                    scroll(String.valueOf(args[0]));
            case DriverApi.HIGHLIGHT -> (JavaCallable) (ctx, args) ->
                    highlight(String.valueOf(args[0]));
            case DriverApi.SELECT -> (JavaCallable) (ctx, args) -> {
                String locator = String.valueOf(args[0]);
                Object value = args.length > 1 ? args[1] : null;
                if (value instanceof Number n) {
                    return select(locator, n.intValue());
                }
                return select(locator, value != null ? String.valueOf(value) : "");
            };
            case DriverApi.SUBMIT -> (JavaCallable) (ctx, args) -> submit();

            // Element state (return primitives)
            case DriverApi.TEXT -> (JavaCallable) (ctx, args) ->
                    text(String.valueOf(args[0]));
            case DriverApi.HTML -> (JavaCallable) (ctx, args) ->
                    html(String.valueOf(args[0]));
            case DriverApi.VALUE -> (JavaCallable) (ctx, args) -> {
                if (args.length == 1) {
                    return value(String.valueOf(args[0]));
                } else {
                    return value(String.valueOf(args[0]), String.valueOf(args[1]));
                }
            };
            case DriverApi.ATTRIBUTE -> (JavaCallable) (ctx, args) ->
                    attribute(String.valueOf(args[0]), String.valueOf(args[1]));
            case DriverApi.PROPERTY -> (JavaCallable) (ctx, args) ->
                    property(String.valueOf(args[0]), String.valueOf(args[1]));
            case DriverApi.EXISTS -> (JavaCallable) (ctx, args) ->
                    exists(String.valueOf(args[0]));
            case DriverApi.ENABLED -> (JavaCallable) (ctx, args) ->
                    enabled(String.valueOf(args[0]));
            case DriverApi.POSITION -> (JavaCallable) (ctx, args) ->
                    position(String.valueOf(args[0]));

            // Locators
            case DriverApi.LOCATE -> (JavaCallable) (ctx, args) ->
                    locate(String.valueOf(args[0]));
            case DriverApi.LOCATE_ALL -> (JavaCallable) (ctx, args) ->
                    locateAll(String.valueOf(args[0]));
            case DriverApi.OPTIONAL -> (JavaCallable) (ctx, args) ->
                    optional(String.valueOf(args[0]));

            // Wait methods
            case DriverApi.WAIT_FOR -> (JavaCallable) (ctx, args) ->
                    waitFor(String.valueOf(args[0]));
            case DriverApi.WAIT_FOR_TEXT -> (JavaCallable) (ctx, args) ->
                    waitForText(String.valueOf(args[0]), String.valueOf(args[1]));
            case DriverApi.WAIT_FOR_ENABLED -> (JavaCallable) (ctx, args) ->
                    waitForEnabled(String.valueOf(args[0]));
            case DriverApi.WAIT_FOR_URL -> (JavaCallable) (ctx, args) ->
                    waitForUrl(String.valueOf(args[0]));
            case DriverApi.WAIT_UNTIL -> (JavaCallable) (ctx, args) -> {
                if (args.length == 1) {
                    return waitUntil(String.valueOf(args[0]));
                } else {
                    return waitUntil(String.valueOf(args[0]), String.valueOf(args[1]));
                }
            };

            case DriverApi.WAIT_FOR_RESULT_COUNT -> (JavaCallable) (ctx, args) ->
                    waitForResultCount(String.valueOf(args[0]), ((Number) args[1]).intValue());

            // Frame/Page switching
            case DriverApi.SWITCH_FRAME -> (JavaCallable) (ctx, args) -> {
                Object arg = args.length > 0 ? args[0] : null;
                if (arg == null) {
                    switchFrame((String) null);
                } else if (arg instanceof Number n) {
                    switchFrame(n.intValue());
                } else {
                    switchFrame(String.valueOf(arg));
                }
                return null;
            };
            case DriverApi.SWITCH_PAGE -> (JavaCallable) (ctx, args) -> {
                Object arg = args[0];
                if (arg instanceof Number n) {
                    switchPage(n.intValue());
                } else {
                    switchPage(String.valueOf(arg));
                }
                return null;
            };
            case DriverApi.GET_PAGES -> (JavaCallable) (ctx, args) -> getPages();

            // Script execution
            case DriverApi.SCRIPT -> (JavaCallable) (ctx, args) -> {
                if (args.length == 1) {
                    return script(args[0]);
                } else {
                    return script(String.valueOf(args[0]), args[1]);
                }
            };
            case DriverApi.SCRIPT_ALL -> (JavaCallable) (ctx, args) ->
                    scriptAll(String.valueOf(args[0]), args[1]);

            // Navigation
            case DriverApi.REFRESH -> (JavaCallable) (ctx, args) -> {
                refresh();
                return null;
            };
            case DriverApi.BACK -> (JavaCallable) (ctx, args) -> {
                back();
                return null;
            };
            case DriverApi.FORWARD -> (JavaCallable) (ctx, args) -> {
                forward();
                return null;
            };

            // Screenshot
            case DriverApi.SCREENSHOT -> (JavaCallable) (ctx, args) -> {
                if (args.length == 0) {
                    return screenshot();
                } else if (args[0] instanceof Boolean b) {
                    return screenshot(b);
                }
                return screenshot();
            };

            // Cookies
            case DriverApi.COOKIE -> (JavaCallable) (ctx, args) -> {
                Object arg = args[0];
                if (arg instanceof String s) {
                    return cookie(s);
                } else if (arg instanceof Map) {
                    cookie((Map<String, Object>) arg);
                    return null;
                }
                return null;
            };
            case DriverApi.CLEAR_COOKIES -> (JavaCallable) (ctx, args) -> {
                clearCookies();
                return null;
            };
            case DriverApi.DELETE_COOKIE -> (JavaCallable) (ctx, args) -> {
                deleteCookie(String.valueOf(args[0]));
                return null;
            };

            // Request interception
            // V1 compat:  driver.intercept({ patterns: [...], mock: 'classpath:mock.feature' })
            // V2 handler:  driver.intercept({ patterns: [...], handler: function(req){ return { status: 200, body: '...' } } })
            case DriverApi.INTERCEPT -> (JavaCallable) (ctx, args) -> {
                Object arg = args[0];
                if (arg instanceof Map) {
                    Map<String, Object> configMap = (Map<String, Object>) arg;
                    List<Object> patterns = (List<Object>) configMap.get("patterns");
                    if (patterns == null) {
                        throw new RuntimeException("missing array argument 'patterns': " + configMap);
                    }
                    // Extract URL pattern strings from pattern maps or plain strings
                    List<String> urlPatterns = new java.util.ArrayList<>();
                    for (Object p : patterns) {
                        if (p instanceof Map) {
                            Object urlPattern = ((Map<String, Object>) p).get("urlPattern");
                            urlPatterns.add(urlPattern != null ? String.valueOf(urlPattern) : "*");
                        } else {
                            urlPatterns.add(String.valueOf(p));
                        }
                    }
                    Object handlerObj = configMap.get("handler");
                    String mock = configMap.get("mock") != null ? String.valueOf(configMap.get("mock")) : null;
                    if (handlerObj instanceof JavaCallable jsHandler) {
                        // V2: inline JS handler
                        intercept(urlPatterns, request -> {
                            Object result = jsHandler.call(ctx, request);
                            if (result instanceof InterceptResponse ir) {
                                return ir;
                            } else if (result instanceof Map) {
                                Map<String, Object> resultMap = (Map<String, Object>) result;
                                int status = resultMap.get("status") instanceof Number n ? n.intValue() : 200;
                                String body = resultMap.get("body") != null ? String.valueOf(resultMap.get("body")) : "";
                                Map<String, Object> responseHeaders = new java.util.LinkedHashMap<>();
                                if (resultMap.get("headers") instanceof Map) {
                                    responseHeaders.putAll((Map<String, Object>) resultMap.get("headers"));
                                }
                                return InterceptResponse.of(status, responseHeaders,
                                        body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            }
                            return null;
                        });
                    } else if (mock != null) {
                        // V1 compat: mock feature file
                        io.karatelabs.gherkin.Feature feature = io.karatelabs.gherkin.Feature.read(mock);
                        io.karatelabs.core.MockHandler mockHandler = new io.karatelabs.core.MockHandler(feature);
                        intercept(urlPatterns, request -> {
                            io.karatelabs.http.HttpRequest httpRequest = new io.karatelabs.http.HttpRequest();
                            httpRequest.setUrl(request.getUrl());
                            httpRequest.setMethod(request.getMethod());
                            if (request.getHeaders() != null) {
                                Map<String, List<String>> headers = new java.util.LinkedHashMap<>();
                                for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                                    headers.put(entry.getKey(), List.of(String.valueOf(entry.getValue())));
                                }
                                httpRequest.setHeaders(headers);
                            }
                            if (request.getPostData() != null) {
                                httpRequest.setBody(request.getPostData().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            }
                            io.karatelabs.http.HttpResponse httpResponse = mockHandler.apply(httpRequest);
                            if (httpResponse != null) {
                                Map<String, Object> responseHeaders = new java.util.LinkedHashMap<>();
                                if (httpResponse.getHeaders() != null) {
                                    for (Map.Entry<String, List<String>> entry : httpResponse.getHeaders().entrySet()) {
                                        List<String> values = entry.getValue();
                                        if (values != null && !values.isEmpty()) {
                                            responseHeaders.put(entry.getKey(), values.getLast());
                                        }
                                    }
                                }
                                return InterceptResponse.of(httpResponse.getStatus(), responseHeaders,
                                        httpResponse.getBodyBytes() != null ? httpResponse.getBodyBytes() : new byte[0]);
                            }
                            return null;
                        });
                    } else {
                        throw new RuntimeException("missing 'mock' or 'handler' in intercept config: " + configMap);
                    }
                }
                return null;
            };

            // Dialog handling
            case DriverApi.DIALOG -> (JavaCallable) (ctx, args) -> {
                boolean accept = args.length > 0 && Boolean.TRUE.equals(args[0]);
                if (args.length > 1 && args[1] != null) {
                    dialog(accept, String.valueOf(args[1]));
                } else {
                    dialog(accept);
                }
                return null;
            };
            case DriverApi.ON_DIALOG -> (JavaCallable) (ctx, args) -> {
                if (args.length == 0 || args[0] == null) {
                    onDialog(null);
                } else if (args[0] instanceof JavaCallable jsHandler) {
                    onDialog(dialog -> jsHandler.call(ctx, dialog));
                }
                return this;
            };

            // Mouse and keys
            case DriverApi.MOUSE -> (JavaCallable) (ctx, args) -> {
                if (args.length == 0) {
                    return mouse();
                } else if (args.length == 1) {
                    return mouse(String.valueOf(args[0]));
                } else {
                    return mouse((Number) args[0], (Number) args[1]);
                }
            };
            case DriverApi.KEYS -> (JavaCallable) (ctx, args) -> keys();

            // Retry - returns a proxy with custom timeout for chaining
            case DriverApi.RETRY -> (JavaCallable) (ctx, args) -> {
                Integer count = args.length > 0 ? ((Number) args[0]).intValue() : null;
                Integer interval = args.length > 1 ? ((Number) args[1]).intValue() : null;
                return new RetryableDriver(this, count, interval);
            };

            default -> null;
        };
    }

    /**
     * Set a property value by name for JavaScript assignment.
     * Supports: driver.url = '...', driver.dimensions = {...}
     */
    @SuppressWarnings("unchecked")
    @Override
    default void putMember(String name, Object value) {
        switch (name) {
            case DriverApi.URL -> setUrl(String.valueOf(value));
            case DriverApi.DIMENSIONS -> setDimensions((Map<String, Object>) value);
            default -> SimpleObject.super.putMember(name, value);
        }
    }

    // ========== CoreDriver Primitive Implementations ==========
    // These default methods implement CoreDriver primitives by delegating to Driver methods

    /**
     * Execute JavaScript. Delegates to {@link #script(String)}.
     */
    @Override
    default Object eval(String js) {
        return script(js);
    }

    /**
     * Execute JavaScript with support for JsFunction.
     * Delegates to {@link #script(Object)}.
     *
     * @param expression JavaScript expression as String or JsFunction
     * @return the result of the JavaScript execution
     */
    default Object eval(Object expression) {
        return script(expression);
    }

    /**
     * Navigate to URL. Delegates to {@link #setUrl(String)}.
     */
    @Override
    default void navigate(String url) {
        setUrl(url);
    }

    /**
     * Switch frames. Dispatches to {@link #switchFrame(int)} or {@link #switchFrame(String)}.
     */
    @Override
    default void frame(Object target) {
        if (target == null) {
            switchFrame((String) null);
        } else if (target instanceof Number n) {
            switchFrame(n.intValue());
        } else {
            switchFrame(target.toString());
        }
    }

    /**
     * Window management. Dispatches to maximize(), minimize(), fullscreen().
     */
    @Override
    default void window(String operation) {
        switch (operation) {
            case "maximize" -> maximize();
            case "minimize" -> minimize();
            case "fullscreen" -> fullscreen();
            case "normal" -> setDimensions(Map.of("windowState", "normal"));
            default -> throw new DriverException("unknown window operation: " + operation);
        }
    }

    /**
     * Tab switching. Dispatches to {@link #switchPage(int)} or {@link #switchPage(String)}.
     */
    @Override
    default void tab(Object target) {
        if (target instanceof Number n) {
            switchPage(n.intValue());
        } else {
            switchPage(target.toString());
        }
    }

    // ========== Navigation ==========

    /**
     * Navigate to URL and wait for page load.
     */
    void setUrl(String url);

    /**
     * Get current URL.
     */
    String getUrl();

    /**
     * Get page title.
     */
    String getTitle();

    /**
     * Wait for page to load based on strategy.
     */
    void waitForPageLoad(PageLoadStrategy strategy);

    /**
     * Wait for page to load with custom timeout.
     */
    void waitForPageLoad(PageLoadStrategy strategy, Duration timeout);

    /**
     * Refresh the current page.
     */
    void refresh();

    /**
     * Reload the page ignoring cache.
     */
    void reload();

    /**
     * Navigate back in history.
     */
    void back();

    /**
     * Navigate forward in history.
     */
    void forward();

    // ========== JavaScript Evaluation ==========

    /**
     * Execute JavaScript and return result.
     */
    Object script(String expression);

    /**
     * Execute JavaScript with support for JsFunction.
     * If the argument is a JsFunction (e.g., arrow function), it will be serialized
     * to its source code and invoked as an IIFE.
     *
     * <p>Examples:</p>
     * <pre>
     * script("document.title")                    // string expression
     * script(() => document.title)                // arrow function (from Karate JS)
     * script(() => { return document.title })     // arrow function with block body
     * </pre>
     *
     * @param expression JavaScript expression as String or JsFunction
     * @return the result of the JavaScript execution
     */
    default Object script(Object expression) {
        String js = Locators.toFunction(expression);
        // If it's a function, wrap in IIFE to invoke it
        if (js.contains("=>") || js.startsWith("function")) {
            js = "(" + js + ")()";
        }
        return script(js);
    }

    /**
     * Execute a script on an element.
     * The element is available as '_' in the expression.
     * Expression can be a String ("_.value") or JsFunction (_ => _.value).
     */
    default Object script(String locator, Object expression) {
        return script(Locators.scriptSelector(locator, expression));
    }

    /**
     * Execute a script on all matching elements.
     * Each element is available as '_' in the expression.
     * Expression can be a String or JsFunction.
     */
    @SuppressWarnings("unchecked")
    default List<Object> scriptAll(String locator, Object expression) {
        return (List<Object>) script(Locators.scriptAllSelector(locator, expression));
    }

    // ========== Screenshot ==========

    /**
     * Take screenshot and return PNG bytes.
     */
    byte[] screenshot();

    /**
     * Take screenshot, optionally embed in report.
     */
    byte[] screenshot(boolean embed);

    // ========== Dialog Handling ==========

    /**
     * Register a handler for JavaScript dialogs (alert, confirm, prompt, beforeunload).
     */
    void onDialog(DialogHandler handler);

    /**
     * Get the current dialog message.
     */
    String getDialogText();

    /**
     * Accept or dismiss the current dialog.
     */
    void dialog(boolean accept);

    /**
     * Accept or dismiss the current dialog with optional prompt input.
     */
    void dialog(boolean accept, String input);

    /**
     * Get the current dialog if one is open.
     * This is useful for detecting dialogs that appear after actions.
     *
     * @return the current Dialog, or null if no dialog is open
     */
    Dialog getDialog();

    // ========== Frame Switching ==========

    /**
     * Switch to an iframe by index.
     */
    void switchFrame(int index);

    /**
     * Switch to an iframe by locator, or null to return to main frame.
     */
    void switchFrame(String locator);

    /**
     * Get the current frame info, or null if in main frame.
     */
    Map<String, Object> getCurrentFrame();

    // ========== Submit (wait for page change after next action) ==========

    /**
     * Mark the next action as triggering a page navigation.
     * After the next click/action, the driver will wait for the page to change
     * (detected by URL or document element hash change).
     * <p>Works on all backends (CDP, WebDriver, Playwright).</p>
     * <p>Usage: {@code submit().click('#button')}</p>
     */
    default Driver submit() {
        String hash = getUrl() + script("document.documentElement.id");
        getOptions().setPreSubmitHash(hash);
        return this;
    }

    /**
     * If submit() was called, wait for the document to change after running the action.
     */
    default void waitIfSubmitRequested() {
        String before = getOptions().getPreSubmitHash();
        if (before == null) {
            return;
        }
        getOptions().setPreSubmitHash(null);
        long deadline = System.currentTimeMillis() + getOptions().getTimeout();
        while (System.currentTimeMillis() < deadline) {
            String after = getUrl() + script("document.documentElement.id");
            if (!before.equals(after)) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ========== Element Operations (defaults delegate to script + Locators) ==========

    /**
     * Click an element.
     */
    default Element click(String locator) {
        script(Locators.clickJs(locator));
        waitIfSubmitRequested();
        return BaseElement.of(this, locator);
    }

    /**
     * Focus an element.
     */
    default Element focus(String locator) {
        script(Locators.focusJs(locator));
        return BaseElement.of(this, locator);
    }

    /**
     * Clear an input element.
     */
    default Element clear(String locator) {
        script(Locators.clearJs(locator));
        return BaseElement.of(this, locator);
    }

    /**
     * Input text into an element (focus, clear, type).
     * For time/date inputs that don't respond to CDP keystrokes, uses JS value assignment.
     */
    default Element input(String locator, String value) {
        focus(locator);
        Boolean isDateTimeInput = (Boolean) script(locator,
            "_ && ['time','date','datetime-local','month','week'].indexOf(_.type) >= 0");
        if (Boolean.TRUE.equals(isDateTimeInput)) {
            script(Locators.inputJs(locator, value));
        } else {
            clear(locator);
            keys().type(value);
        }
        return BaseElement.of(this, locator);
    }

    /**
     * Set the value of an input element directly.
     */
    default Element value(String locator, String value) {
        script(Locators.inputJs(locator, value));
        return BaseElement.of(this, locator);
    }

    /**
     * Select an option from a dropdown by text or value.
     */
    default Element select(String locator, String text) {
        script(Locators.optionSelector(locator, text));
        return BaseElement.of(this, locator);
    }

    /**
     * Select an option from a dropdown by index.
     */
    default Element select(String locator, int index) {
        String js = Locators.wrapInFunctionInvoke(
                "var e = " + Locators.selector(locator) + ";" +
                        " e.options[" + index + "].selected = true;" +
                        " e.dispatchEvent(new Event('input', {bubbles: true}));" +
                        " e.dispatchEvent(new Event('change', {bubbles: true}))");
        script(js);
        return BaseElement.of(this, locator);
    }

    /**
     * Scroll an element into view.
     */
    default Element scroll(String locator) {
        script(Locators.scrollJs(locator));
        return BaseElement.of(this, locator);
    }

    /**
     * Highlight an element (for debugging).
     */
    default Element highlight(String locator) {
        script(Locators.highlight(locator, getOptions().getHighlightDuration()));
        return BaseElement.of(this, locator);
    }

    // ========== Element State (defaults delegate to script + Locators) ==========

    /**
     * Get the text content of an element.
     */
    default String text(String locator) {
        return (String) script(Locators.textJs(locator));
    }

    /**
     * Get the outer HTML of an element.
     */
    default String html(String locator) {
        return (String) script(Locators.outerHtmlJs(locator));
    }

    /**
     * Get the value of an input element.
     */
    default String value(String locator) {
        return (String) script(Locators.valueJs(locator));
    }

    /**
     * Get an attribute of an element.
     */
    default String attribute(String locator, String name) {
        return (String) script(Locators.attributeJs(locator, name));
    }

    /**
     * Get a property of an element.
     */
    default Object property(String locator, String name) {
        return script(Locators.propertyJs(locator, name));
    }

    /**
     * Check if an element is enabled.
     */
    default boolean enabled(String locator) {
        return Boolean.TRUE.equals(script(Locators.enabledJs(locator)));
    }

    /**
     * Check if an element exists.
     */
    default boolean exists(String locator) {
        return Boolean.TRUE.equals(script(Locators.existsJs(locator)));
    }

    /**
     * Get the position of an element (absolute).
     */
    @SuppressWarnings("unchecked")
    default Map<String, Object> position(String locator) {
        return (Map<String, Object>) script(Locators.getPositionJs(locator));
    }

    /**
     * Get the position of an element.
     *
     * @param relative if true, returns viewport-relative position
     */
    @SuppressWarnings("unchecked")
    default Map<String, Object> position(String locator, boolean relative) {
        if (relative) {
            String js = Locators.wrapInFunctionInvoke(
                    "var r = " + Locators.selector(locator) + ".getBoundingClientRect();" +
                            " return { x: r.x, y: r.y, width: r.width, height: r.height }");
            return (Map<String, Object>) script(js);
        }
        return position(locator);
    }

    // ========== Locators ==========

    /**
     * Find an element by locator.
     * This is a convenience method that creates an Element wrapper.
     */
    default Element locate(String locator) {
        return BaseElement.of(this, locator);
    }

    /**
     * Find all elements matching a locator.
     * Implementation requires driver-specific indexed locator support.
     */
    List<Element> locateAll(String locator);

    /**
     * Find an element that may not exist (optional).
     * This is a convenience method that creates an optional Element wrapper.
     */
    default Element optional(String locator) {
        return BaseElement.optional(this, locator);
    }

    // ========== Wait Methods ==========

    /**
     * Wait for an element to exist.
     */
    Element waitFor(String locator);

    /**
     * Wait for an element to exist with custom timeout.
     */
    Element waitFor(String locator, Duration timeout);

    /**
     * Wait for any of the locators to match.
     */
    Element waitForAny(String locator1, String locator2);

    /**
     * Wait for any of the locators to match.
     */
    Element waitForAny(String[] locators);

    /**
     * Wait for any of the locators to match with custom timeout.
     */
    Element waitForAny(String[] locators, Duration timeout);

    /**
     * Wait for an element to contain specific text.
     */
    Element waitForText(String locator, String expected);

    /**
     * Wait for an element to contain specific text with custom timeout.
     */
    Element waitForText(String locator, String expected, Duration timeout);

    /**
     * Wait for an element to be enabled.
     */
    Element waitForEnabled(String locator);

    /**
     * Wait for an element to be enabled with custom timeout.
     */
    Element waitForEnabled(String locator, Duration timeout);

    /**
     * Wait for URL to contain expected string.
     */
    String waitForUrl(String expected);

    /**
     * Wait for URL to contain expected string with custom timeout.
     */
    String waitForUrl(String expected, Duration timeout);

    /**
     * Wait until a JavaScript expression on an element evaluates to truthy.
     */
    Element waitUntil(String locator, String expression);

    /**
     * Wait until a JavaScript expression on an element evaluates to truthy.
     */
    Element waitUntil(String locator, String expression, Duration timeout);

    /**
     * Wait until a JavaScript expression evaluates to truthy.
     */
    boolean waitUntil(String expression);

    /**
     * Wait until a JavaScript expression evaluates to truthy with custom timeout.
     */
    boolean waitUntil(String expression, Duration timeout);

    /**
     * Wait until a supplier returns a truthy value.
     */
    Object waitUntil(Supplier<Object> condition);

    /**
     * Wait until a supplier returns a truthy value with custom timeout.
     */
    Object waitUntil(Supplier<Object> condition, Duration timeout);

    /**
     * Wait for a specific number of elements to match.
     */
    List<Element> waitForResultCount(String locator, int count);

    /**
     * Wait for a specific number of elements to match with custom timeout.
     */
    List<Element> waitForResultCount(String locator, int count, Duration timeout);

    // ========== Cookies ==========

    /**
     * Get a cookie by name.
     */
    Map<String, Object> cookie(String name);

    /**
     * Set a cookie.
     */
    void cookie(Map<String, Object> cookie);

    /**
     * Delete a cookie by name.
     */
    void deleteCookie(String name);

    /**
     * Clear all cookies.
     */
    void clearCookies();

    /**
     * Get all cookies.
     */
    List<Map<String, Object>> getCookies();

    // ========== Window Management ==========

    /**
     * Maximize the browser window.
     */
    void maximize();

    /**
     * Minimize the browser window.
     */
    void minimize();

    /**
     * Make the browser window fullscreen.
     */
    void fullscreen();

    /**
     * Get window dimensions and position.
     */
    Map<String, Object> getDimensions();

    /**
     * Set window dimensions and/or position.
     */
    void setDimensions(Map<String, Object> dimensions);

    /**
     * Activate the browser window (bring to front).
     */
    void activate();

    // ========== PDF Generation ==========

    /**
     * Generate a PDF of the current page.
     */
    byte[] pdf(Map<String, Object> options);

    /**
     * Generate a PDF of the current page with default options.
     */
    byte[] pdf();

    // ========== Mouse and Keyboard ==========

    /**
     * Get a Mouse object at position (0, 0).
     */
    Mouse mouse();

    /**
     * Get a Mouse object positioned at an element's center.
     */
    Mouse mouse(String locator);

    /**
     * Get a Mouse object at specified coordinates.
     */
    Mouse mouse(Number x, Number y);

    /**
     * Get a Keys object for keyboard input.
     */
    Keys keys();

    // ========== Pages/Tabs Management ==========

    /**
     * Get list of all page targets (tabs).
     */
    List<String> getPages();

    /**
     * Switch to a page by title or URL substring.
     */
    void switchPage(String titleOrUrl);

    /**
     * Switch to a page by index.
     */
    void switchPage(int index);

    /**
     * Switch to a page by its backend target ID.
     *
     * <p>Useful in conjunction with {@link #drainOpenedTargets()} — the drained
     * entries carry a {@code targetId} which unambiguously identifies the new tab,
     * while URL/title can be ambiguous (e.g. blank or duplicated across tabs).</p>
     *
     * <p>Default implementation delegates to {@link #switchPage(String)} using the
     * target ID as a substring match; most drivers should override.</p>
     */
    default void switchPageById(String targetId) {
        switchPage(targetId);
    }

    /**
     * Drain and return the list of new page targets (tabs) opened since the last call.
     *
     * <p>Event-driven — no CDP round-trip. Each entry is a map with {@code targetId},
     * {@code url}, and {@code title}. Returns an empty list if nothing new was opened.</p>
     *
     * <p>Use case: an automation that clicks a link with {@code target="_blank"} or a
     * button that calls {@code window.open()} can call this after the click to learn
     * whether (and where) a new tab appeared, and then call {@link #switchPage(String)}
     * or {@link #switchPage(int)} to focus it.</p>
     *
     * <p>Mirrors the {@link #getDialog()} pattern — the driver pushes events into an
     * internal queue and the caller drains it, avoiding polling {@code Target.getTargets}
     * after every action.</p>
     *
     * <p>Default implementation returns an empty list for drivers that don't track
     * target events.</p>
     */
    default List<Map<String, Object>> drainOpenedTargets() {
        return java.util.Collections.emptyList();
    }

    // ========== Positional Locators ==========

    /**
     * Create a finder for elements to the right of the reference element.
     * This is a convenience method that creates a Finder.
     */
    default Finder rightOf(String locator) {
        return new Finder(this, locator, Finder.Position.RIGHT_OF);
    }

    /**
     * Create a finder for elements to the left of the reference element.
     * This is a convenience method that creates a Finder.
     */
    default Finder leftOf(String locator) {
        return new Finder(this, locator, Finder.Position.LEFT_OF);
    }

    /**
     * Create a finder for elements above the reference element.
     * This is a convenience method that creates a Finder.
     */
    default Finder above(String locator) {
        return new Finder(this, locator, Finder.Position.ABOVE);
    }

    /**
     * Create a finder for elements below the reference element.
     * This is a convenience method that creates a Finder.
     */
    default Finder below(String locator) {
        return new Finder(this, locator, Finder.Position.BELOW);
    }

    /**
     * Create a finder for elements near the reference element.
     * This is a convenience method that creates a Finder.
     */
    default Finder near(String locator) {
        return new Finder(this, locator, Finder.Position.NEAR);
    }

    // ========== Request Interception ==========

    /**
     * Enable request interception with a handler.
     */
    void intercept(List<String> patterns, InterceptHandler handler);

    /**
     * Enable request interception for all requests.
     */
    void intercept(InterceptHandler handler);

    /**
     * Stop request interception.
     */
    void stopIntercept();

    // ========== Lifecycle ==========

    /**
     * Close driver and browser.
     */
    void quit();

    /**
     * Alias for quit().
     */
    void close();

    /**
     * Check if driver is terminated.
     */
    boolean isTerminated();

    // ========== Accessors ==========

    /**
     * Get driver options.
     */
    DriverOptions getOptions();

}
