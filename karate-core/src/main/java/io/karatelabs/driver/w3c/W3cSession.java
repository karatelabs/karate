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
package io.karatelabs.driver.w3c;

import io.karatelabs.driver.DriverException;
import io.karatelabs.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * W3C WebDriver HTTP session client.
 * Uses java.net.http.HttpClient for clean separation from scenario HTTP state.
 *
 * <p>Implements the W3C WebDriver protocol:
 * <a href="https://www.w3.org/TR/webdriver2/">W3C WebDriver Specification</a></p>
 */
public class W3cSession {

    private static final Logger logger = LoggerFactory.getLogger(W3cSession.class);

    /**
     * W3C standard element identifier key.
     */
    static final String W3C_ELEMENT_KEY = "element-6066-11e4-a52e-4f735466cecf";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String sessionId;
    private final Duration timeout;

    private W3cSession(HttpClient httpClient, String baseUrl, String sessionId, Duration timeout) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.sessionId = sessionId;
        this.timeout = timeout;
    }

    /**
     * Create a new WebDriver session.
     *
     * @param baseUrl      WebDriver server URL (e.g., "http://localhost:9515")
     * @param sessionPayload W3C session creation payload
     * @param timeout      request timeout
     * @return a new W3cSession
     */
    @SuppressWarnings("unchecked")
    public static W3cSession create(String baseUrl, Map<String, Object> sessionPayload, Duration timeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();

        // Normalize baseUrl - remove trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String json = Json.of(sessionPayload).toString();
        logger.debug("Creating W3C session at {} with payload: {}", baseUrl, json);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/session"))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new DriverException("WebDriver session create failed with status "
                        + response.statusCode() + ": " + response.body());
            }

            Map<String, Object> body = Json.of(response.body()).asMap();
            Map<String, Object> value = (Map<String, Object>) body.get("value");
            String sessionId = (String) value.get("sessionId");
            if (sessionId == null) {
                throw new DriverException("WebDriver session create response missing sessionId: " + response.body());
            }

            logger.info("W3C session created: {}", sessionId);
            return new W3cSession(client, baseUrl, sessionId, timeout);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DriverException("WebDriver session create failed: " + e.getMessage(), e);
        }
    }

    // ========== Navigation ==========

    public void navigateTo(String url) {
        post("url", Map.of("url", url));
    }

    public String getUrl() {
        return (String) getValue(get("url"));
    }

    public String getTitle() {
        return (String) getValue(get("title"));
    }

    public void back() {
        post("back", Map.of());
    }

    public void forward() {
        post("forward", Map.of());
    }

    public void refresh() {
        post("refresh", Map.of());
    }

    // ========== JavaScript Execution ==========

    public Object executeScript(String script, Object... args) {
        List<Object> argsList = new ArrayList<>();
        Collections.addAll(argsList, args);
        Map<String, Object> payload = Map.of("script", script, "args", argsList);
        return getValue(post("execute/sync", payload));
    }

    // ========== Element Finding ==========

    /**
     * Find a single element. Returns the W3C element ID.
     *
     * @throws DriverException if element not found
     */
    @SuppressWarnings("unchecked")
    public String findElement(String using, String value) {
        Map<String, Object> payload = Map.of("using", using, "value", value);
        Map<String, Object> result = (Map<String, Object>) getValue(post("element", payload));
        return extractElementId(result);
    }

    /**
     * Find multiple elements. Returns list of W3C element IDs.
     */
    @SuppressWarnings("unchecked")
    public List<String> findElements(String using, String value) {
        Map<String, Object> payload = Map.of("using", using, "value", value);
        List<Map<String, Object>> results = (List<Map<String, Object>>) getValue(post("elements", payload));
        List<String> ids = new ArrayList<>();
        if (results != null) {
            for (Map<String, Object> result : results) {
                ids.add(extractElementId(result));
            }
        }
        return ids;
    }

    // ========== Element Operations ==========

    public void clickElement(String elementId) {
        post("element/" + elementId + "/click", Map.of());
    }

    public void sendKeys(String elementId, String text) {
        post("element/" + elementId + "/value", Map.of("text", text));
    }

    public void clearElement(String elementId) {
        post("element/" + elementId + "/clear", Map.of());
    }

    public String getElementText(String elementId) {
        return (String) getValue(get("element/" + elementId + "/text"));
    }

    public String getElementAttribute(String elementId, String name) {
        return (String) getValue(get("element/" + elementId + "/attribute/" + name));
    }

    public String getElementProperty(String elementId, String name) {
        Object val = getValue(get("element/" + elementId + "/property/" + name));
        return val != null ? val.toString() : null;
    }

    public String getElementTagName(String elementId) {
        return (String) getValue(get("element/" + elementId + "/name"));
    }

    public boolean isElementEnabled(String elementId) {
        Object result = getValue(get("element/" + elementId + "/enabled"));
        return Boolean.TRUE.equals(result);
    }

    public byte[] elementScreenshot(String elementId) {
        String base64 = (String) getValue(get("element/" + elementId + "/screenshot"));
        return Base64.getDecoder().decode(base64);
    }

    // ========== Screenshot ==========

    public byte[] screenshot() {
        String base64 = (String) getValue(get("screenshot"));
        return Base64.getDecoder().decode(base64);
    }

    // ========== Frames ==========

    public void switchFrame(Object id) {
        if (id == null) {
            // W3C spec: {"id": null} switches to top-level browsing context
            // Use raw JSON to ensure null is serialized correctly
            postRaw("frame", "{\"id\":null}");
        } else {
            post("frame", Map.of("id", id));
        }
    }

    public void parentFrame() {
        post("frame/parent", Map.of());
    }

    // ========== Alerts/Dialogs ==========

    public String getAlertText() {
        return (String) getValue(get("alert/text"));
    }

    public void acceptAlert() {
        post("alert/accept", Map.of());
    }

    public void dismissAlert() {
        post("alert/dismiss", Map.of());
    }

    public void sendAlertText(String text) {
        post("alert/text", Map.of("text", text));
    }

    // ========== Window ==========

    @SuppressWarnings("unchecked")
    public Map<String, Object> getWindowRect() {
        return (Map<String, Object>) getValue(get("window/rect"));
    }

    public void setWindowRect(Map<String, Object> rect) {
        post("window/rect", rect);
    }

    public void maximizeWindow() {
        post("window/maximize", Map.of());
    }

    public void minimizeWindow() {
        post("window/minimize", Map.of());
    }

    public void fullscreenWindow() {
        post("window/fullscreen", Map.of());
    }

    public String getWindowHandle() {
        return (String) getValue(get("window"));
    }

    @SuppressWarnings("unchecked")
    public List<String> getWindowHandles() {
        return (List<String>) getValue(get("window/handles"));
    }

    public void switchWindow(String handle) {
        post("window", Map.of("handle", handle));
    }

    public void closeWindow() {
        delete("window");
    }

    // ========== Cookies ==========

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCookie(String name) {
        try {
            return (Map<String, Object>) getValue(get("cookie/" + name));
        } catch (DriverException e) {
            // W3C returns 404 "no such cookie" when cookie doesn't exist
            if (e.getMessage() != null && e.getMessage().contains("no such cookie")) {
                return null;
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAllCookies() {
        return (List<Map<String, Object>>) getValue(get("cookie"));
    }

    public void addCookie(Map<String, Object> cookie) {
        post("cookie", Map.of("cookie", cookie));
    }

    public void deleteCookie(String name) {
        delete("cookie/" + name);
    }

    public void deleteAllCookies() {
        delete("cookie");
    }

    // ========== Session Lifecycle ==========

    // ========== Actions API ==========

    /**
     * Perform W3C Actions (keyboard, mouse, pointer).
     * POST /session/{id}/actions
     */
    public void performActions(List<Map<String, Object>> actions) {
        post("actions", Map.of("actions", actions));
    }

    /**
     * Release all actions (reset keyboard/mouse state).
     * DELETE /session/{id}/actions
     */
    public void releaseActions() {
        delete("actions");
    }

    // ========== Session Lifecycle ==========

    public void deleteSession() {
        try {
            delete("");
            logger.info("W3C session deleted: {}", sessionId);
        } catch (Exception e) {
            logger.warn("Error deleting W3C session {}: {}", sessionId, e.getMessage());
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    // ========== HTTP Primitives ==========

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path) {
        String url = baseUrl + "/session/" + sessionId + "/" + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .GET()
                .build();
        return execute(request);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> payload) {
        return postRaw(path, Json.of(payload).toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postRaw(String path, String json) {
        String url = baseUrl + "/session/" + sessionId + "/" + path;
        logger.trace("POST {} : {}", path, json);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return execute(request);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> delete(String path) {
        String url = baseUrl + "/session/" + sessionId
                + (path.isEmpty() ? "" : "/" + path);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .DELETE()
                .build();
        return execute(request);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> execute(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (body == null || body.isEmpty()) {
                java.util.HashMap<String, Object> nullResult = new java.util.HashMap<>();
                nullResult.put("value", null);
                return nullResult;
            }
            Map<String, Object> result = Json.of(body).asMap();

            // Check for W3C error response
            if (response.statusCode() >= 400) {
                Object value = result.get("value");
                String message = "WebDriver error";
                if (value instanceof Map) {
                    Map<String, Object> errorMap = (Map<String, Object>) value;
                    message = (String) errorMap.getOrDefault("message", message);
                    String error = (String) errorMap.get("error");
                    if (error != null) {
                        message = error + ": " + message;
                    }
                }
                throw new DriverException("WebDriver " + request.method() + " "
                        + request.uri().getPath() + " failed (" + response.statusCode() + "): " + message);
            }

            return result;
        } catch (DriverException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DriverException("WebDriver request failed: " + request.method() + " "
                    + request.uri() + ": " + e.getMessage(), e);
        }
    }

    private static Object getValue(Map<String, Object> response) {
        return response.get("value");
    }

    @SuppressWarnings("unchecked")
    private static String extractElementId(Map<String, Object> elementMap) {
        String id = (String) elementMap.get(W3C_ELEMENT_KEY);
        if (id == null) {
            // Fallback for legacy servers
            id = (String) elementMap.get("ELEMENT");
        }
        if (id == null) {
            throw new DriverException("WebDriver response missing element ID: " + elementMap);
        }
        return id;
    }

    /**
     * Check if a value is a W3C element reference.
     */
    @SuppressWarnings("unchecked")
    static boolean isElementReference(Object value) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            return map.containsKey(W3C_ELEMENT_KEY) || map.containsKey("ELEMENT");
        }
        return false;
    }

    /**
     * Extract element ID from a W3C element reference.
     */
    @SuppressWarnings("unchecked")
    static String elementIdFrom(Object value) {
        if (value instanceof Map) {
            return extractElementId((Map<String, Object>) value);
        }
        return null;
    }

}
