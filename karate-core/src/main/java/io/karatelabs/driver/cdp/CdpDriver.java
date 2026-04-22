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
package io.karatelabs.driver.cdp;

import io.karatelabs.driver.Dialog;
import io.karatelabs.driver.DialogHandler;
import io.karatelabs.driver.Driver;
import io.karatelabs.driver.DriverException;
import io.karatelabs.driver.DriverOptions;
import io.karatelabs.driver.BaseElement;
import io.karatelabs.driver.Element;
import io.karatelabs.driver.Finder;
import io.karatelabs.driver.InterceptHandler;
import io.karatelabs.driver.InterceptRequest;
import io.karatelabs.driver.InterceptResponse;
import io.karatelabs.driver.Keys;
import io.karatelabs.driver.Locators;
import io.karatelabs.driver.Mouse;
import io.karatelabs.driver.PageLoadStrategy;
import io.karatelabs.js.Terms;
import io.karatelabs.output.LogContext;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * CDP-based browser driver implementation.
 * Implements the Driver interface using Chrome DevTools Protocol.
 */
public class CdpDriver implements Driver {

    private static final Logger logger = LogContext.RUNTIME_LOGGER;

    // Karate JS runtime (wildcard resolver, shared utilities)
    private static final String DRIVER_JS = loadResource("driver.js");

    private static String loadResource(String name) {
        try (InputStream is = CdpDriver.class.getResourceAsStream("/io/karatelabs/driver/" + name)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + name, e);
        }
    }

    // Track active drivers for cleanup
    private static final Set<CdpDriver> ACTIVE = ConcurrentHashMap.newKeySet();

    private final CdpClient cdp;
    private final CdpDriverOptions options;
    private final CdpLauncher launcher; // null if connected to existing browser

    // Page load state
    private volatile boolean domContentEventFired;
    private final Set<String> framesStillLoading = ConcurrentHashMap.newKeySet();
    private String mainFrameId;
    private volatile String pendingNavigationUrl; // for better timeout diagnostics

    // Lifecycle
    private volatile boolean terminated = false;

    // Dialog handling
    private volatile String currentDialogText;
    private volatile CdpDialog currentDialog;
    private volatile DialogHandler dialogHandler;

    // Request interception
    private volatile InterceptHandler interceptHandler;

    // Frame tracking
    private Frame currentFrame;
    private final Map<String, Integer> frameContexts = new ConcurrentHashMap<>();

    // Tab/target tracking for close() support
    private String currentTargetId;

    // Tab-opened tracking — pushed by Target.targetCreated events.
    // knownTargetIds is seeded at init from Target.getTargets so existing tabs are
    // not reported as "new". openedTargets is the drain queue consumed by
    // drainOpenedTargets() — mirrors the getDialog() pattern so automation can detect
    // target="_blank" / window.open() without polling Target.getTargets after each
    // action.
    private final Set<String> knownTargetIds = ConcurrentHashMap.newKeySet();
    private final java.util.Queue<Map<String, Object>> openedTargets = new java.util.concurrent.ConcurrentLinkedQueue<>();

    // Keyboard state (reused to maintain modifier state across calls)
    private CdpKeys keysInstance;

    // ========== CDP Event Listener API ==========

    /**
     * Add an external event listener that receives all CDP events.
     * Used for traffic recording, debugging, etc.
     *
     * @param listener The listener to add
     */
    public void addCdpEventListener(CdpEventListener listener) {
        cdp.addExternalListener(listener);
    }

    /**
     * Remove an external event listener.
     *
     * @param listener The listener to remove
     */
    public void removeCdpEventListener(CdpEventListener listener) {
        cdp.removeExternalListener(listener);
    }

    /**
     * Internal representation of a frame.
     */
    private static class Frame {
        final String id;
        final String url;
        final String name;

        Frame(String id, String url, String name) {
            this.id = id;
            this.url = url;
            this.name = name;
        }

        @Override
        public String toString() {
            return "Frame{id='" + id + "', url='" + url + "', name='" + name + "'}";
        }
    }

    private CdpDriver(CdpLauncher launcher, CdpDriverOptions options) {
        this.launcher = launcher;
        this.options = options;
        this.cdp = CdpClient.connect(launcher.getWebSocketUrl(), options.getTimeoutDuration());
        this.currentTargetId = extractTargetIdFromUrl(launcher.getWebSocketUrl());
        ACTIVE.add(this);
        initialize();
    }

    private CdpDriver(String webSocketUrl, CdpDriverOptions options) {
        this.launcher = null;
        this.options = options;
        this.cdp = CdpClient.connect(webSocketUrl, options.getTimeoutDuration());
        this.currentTargetId = extractTargetIdFromUrl(webSocketUrl);
        ACTIVE.add(this);
        initialize();
    }

    /**
     * Extract the targetId from a page-specific WebSocket URL.
     * URL format: ws://host:port/devtools/page/<targetId>
     *
     * CRITICAL for parallel execution: When multiple CdpDriver instances connect to
     * the same browser (e.g., via PooledDriverProvider), each gets its own tab via
     * /json/new. We MUST extract our targetId from the URL we connected to, not from
     * getPages() which returns ALL tabs browser-wide. Using getPages().get(0) caused
     * flaky tests where parallel drivers would incorrectly close each other's tabs.
     *
     * @return the targetId, or null if URL doesn't match expected format
     */
    private static String extractTargetIdFromUrl(String webSocketUrl) {
        if (webSocketUrl == null) {
            return null;
        }
        // Format: ws://host:port/devtools/page/<targetId>
        int pageIndex = webSocketUrl.indexOf("/devtools/page/");
        if (pageIndex != -1) {
            return webSocketUrl.substring(pageIndex + "/devtools/page/".length());
        }
        // Browser-level URL format: ws://host:port/devtools/browser/<browserId>
        // In this case, we don't have a specific targetId
        return null;
    }

    /**
     * Launch a new browser and create driver.
     */
    public static CdpDriver start(CdpDriverOptions options) {
        CdpLauncher launcher = CdpLauncher.start(options);
        return new CdpDriver(launcher, options);
    }

    /**
     * Launch a new browser with default options.
     */
    public static CdpDriver start() {
        return start(CdpDriverOptions.builder().build());
    }

    /**
     * Launch a headless browser.
     */
    public static CdpDriver startHeadless() {
        return start(CdpDriverOptions.builder().headless(true).build());
    }

    /**
     * Connect to an existing browser via WebSocket URL.
     */
    public static CdpDriver connect(String webSocketUrl) {
        return connect(webSocketUrl, CdpDriverOptions.builder().build());
    }

    /**
     * Connect to an existing browser with options.
     */
    public static CdpDriver connect(String webSocketUrl, CdpDriverOptions options) {
        return new CdpDriver(webSocketUrl, options);
    }

    /**
     * Close all active drivers.
     */
    public static void closeAll() {
        ACTIVE.forEach(CdpDriver::quit);
    }

    private void initialize() {
        logger.debug("initializing CDP driver");

        // CRITICAL: Get main frame ID FIRST - needed by event handlers
        // This works without Page.enable and ensures handlers can filter by frame
        CdpResponse frameResponse = cdp.method("Page.getFrameTree").send();
        mainFrameId = frameResponse.getResult("frameTree.frame.id");
        logger.debug("main frame ID: {}", mainFrameId);

        // Track current target for close() support
        // currentTargetId is set in constructor by extracting from WebSocket URL (preferred)
        // Fallback: if URL extraction failed (e.g., browser-level URL), use getPages()
        // Note: getPages() fallback is NOT safe for parallel execution - all drivers would
        // get the same targetId. This is only for backwards compatibility with browser-level connections.
        if (currentTargetId == null) {
            List<String> pages = getPages();
            if (!pages.isEmpty()) {
                currentTargetId = pages.get(0);
                logger.warn("currentTargetId fallback to getPages()[0]: {} - parallel execution may be unreliable", currentTargetId);
            }
        } else {
            logger.debug("current target ID (from URL): {}", currentTargetId);
        }

        // Setup event handlers BEFORE enabling domains
        // This prevents race conditions where events fire before handlers are registered
        setupEventHandlers();

        // NOW enable domains - events will be properly captured
        cdp.method("Page.enable").send();
        cdp.method("Runtime.enable").send();
        cdp.method("Network.enable").send(); // Required for cookie operations

        // Enable lifecycle events (required for Page.lifecycleEvent)
        cdp.method("Page.setLifecycleEventsEnabled")
                .param("enabled", true)
                .send();

        // Seed knownTargetIds with existing tabs so the first batch of
        // Target.targetCreated events (fired for existing targets) don't look "new".
        try {
            CdpResponse initialTargets = cdp.browserMethod("Target.getTargets").send();
            List<Map<String, Object>> targets = initialTargets.getResult("targetInfos");
            if (targets != null) {
                for (Map<String, Object> target : targets) {
                    String targetId = (String) target.get("targetId");
                    if (targetId != null) {
                        knownTargetIds.add(targetId);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("initial Target.getTargets seed failed: {}", e.getMessage());
        }
        // Browser-level opt-in for Target.targetCreated / targetInfoChanged / targetDestroyed.
        // Without this, we never learn about new tabs opened by window.open / target="_blank".
        cdp.browserMethod("Target.setDiscoverTargets").param("discover", true).send();

        // Safety check: if page is already loaded (we connected late), set the flag
        // This handles the case where DOMContentLoaded fired before we were listening
        checkIfPageAlreadyLoaded();

        // Wait for main frame execution context to be ready
        // This prevents transient context errors on the first script() call
        waitForMainFrameContext();
    }

    /**
     * Wait for the main frame's execution context to be registered.
     * Called during initialization to prevent transient errors on first use.
     *
     * After Runtime.enable, CDP sends executionContextCreated events asynchronously.
     * Without this wait, the first script() call might happen before the context
     * is registered, causing a transient error and retry.
     */
    private void waitForMainFrameContext() {
        int maxWaitMs = 1000;
        int pollInterval = 50;
        long deadline = System.currentTimeMillis() + maxWaitMs;

        while (System.currentTimeMillis() < deadline) {
            Integer contextId = frameContexts.get(mainFrameId);
            if (contextId != null) {
                // Verify the context works
                try {
                    CdpResponse response = cdp.method("Runtime.evaluate")
                            .param("expression", "1")
                            .param("returnByValue", true)
                            .param("contextId", contextId)
                            .send();
                    if (!response.isError()) {
                        logger.debug("main frame context ready: contextId={}", contextId);
                        return;
                    }
                } catch (Exception e) {
                    // Context not ready yet
                }
            }
            sleep(pollInterval);
        }
        // Timeout is not fatal - retry logic will handle it, but log for diagnostics
        logger.warn("timeout waiting for main frame context during init (will retry on first use)");
    }

    /**
     * Check if the current page is already loaded (document.readyState is 'complete' or 'interactive').
     * This handles the case where we connect to an already-loaded page or the event was missed.
     */
    private void checkIfPageAlreadyLoaded() {
        try {
            CdpResponse response = cdp.method("Runtime.evaluate")
                    .param("expression", "document.readyState")
                    .param("returnByValue", true)
                    .send();
            String readyState = response.getResultAsString("result.value");
            if ("complete".equals(readyState) || "interactive".equals(readyState)) {
                if (!domContentEventFired) {
                    logger.debug("page already loaded (readyState={}), setting domContentEventFired", readyState);
                    domContentEventFired = true;
                }
            }
        } catch (Exception e) {
            // Ignore - this is just a safety check
            logger.warn("readyState check failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void setupEventHandlers() {
        // Listen to BOTH lifecycle events AND domContentEventFired for maximum compatibility
        // Page.lifecycleEvent is more reliable per-frame (Puppeteer approach)
        // Page.domContentEventFired is a fallback for environments where lifecycle events don't fire
        cdp.on("Page.lifecycleEvent", event -> {
            String name = event.get("name");
            String frameId = event.get("frameId");
            logger.trace("lifecycleEvent: name={}, frameId={}", name, frameId);
            // DOMContentLoaded on main frame signals DOM is ready
            if ("DOMContentLoaded".equals(name) && mainFrameId.equals(frameId)) {
                domContentEventFired = true;
                logger.trace("DOMContentLoaded on main frame (via lifecycleEvent)");
            }
        });

        // Fallback: also listen to Page.domContentEventFired for compatibility
        cdp.on("Page.domContentEventFired", event -> {
            domContentEventFired = true;
            logger.trace("domContentEventFired (fallback)");
        });

        cdp.on("Page.frameStartedLoading", event -> {
            String frameId = event.get("frameId");
            if (frameId != null && frameId.equals(mainFrameId)) {
                domContentEventFired = false;
                framesStillLoading.clear();
                logger.trace("frameStartedLoading: {} (main frame, reset state)", frameId);
            } else if (frameId != null) {
                framesStillLoading.add(frameId);
                logger.trace("frameStartedLoading: {} (child frame)", frameId);
            }
        });

        cdp.on("Page.frameStoppedLoading", event -> {
            String frameId = event.get("frameId");
            if (frameId != null) {
                framesStillLoading.remove(frameId);
            }
            logger.trace("frameStoppedLoading: {}, remaining: {}", frameId, framesStillLoading);
        });

        // CRITICAL: Handle frame detachment to prevent stale entries in framesStillLoading
        // If a child frame is detached (removed from DOM) before frameStoppedLoading fires,
        // the frame ID remains in framesStillLoading forever, causing a page load timeout.
        // This was observed as a flaky failure in CI where framesStillLoading was non-empty
        // despite domContentEventFired being true.
        cdp.on("Page.frameDetached", event -> {
            String frameId = event.get("frameId");
            if (frameId != null) {
                framesStillLoading.remove(frameId);
                frameContexts.remove(frameId);
            }
            logger.trace("frameDetached: {}, remaining: {}", frameId, framesStillLoading);
        });

        cdp.on("Page.javascriptDialogOpening", event -> {
            String message = event.get("message");
            String type = event.get("type");
            String defaultPrompt = event.get("defaultPrompt");
            currentDialogText = message;
            // Store the dialog for getDialog() - allows detection after actions
            // CRITICAL: Capture in local variable to prevent race condition with getDialog()
            // which can null out currentDialog from another thread after handler.handle() returns
            CdpDialog dialog = new CdpDialog(cdp, message, type, defaultPrompt);
            currentDialog = dialog;
            logger.debug("dialog opening: type={}, message={}", type, message);

            if (dialogHandler != null) {
                try {
                    dialogHandler.handle(dialog);
                } catch (Exception e) {
                    logger.error("dialog handler error: {}", e.getMessage());
                }
                // If handler didn't resolve the dialog, auto-dismiss
                // Use local 'dialog' reference, not 'currentDialog' which may be null due to race
                if (!dialog.isHandled()) {
                    logger.warn("dialog handler did not resolve dialog, auto-dismissing");
                    try {
                        dialog.dismiss();
                    } catch (Exception e) {
                        // Dialog may already be gone due to race with another event handler
                        logger.trace("auto-dismiss failed (dialog likely already handled): {}", e.getMessage());
                    }
                }
                // Clear after handling
                currentDialog = null;
                currentDialogText = null;
            }
            // If no handler registered, cancel pending Runtime.evaluate calls so they
            // fail fast instead of waiting for the 30-second CDP timeout
            if (dialogHandler == null) {
                cdp.cancelPendingEvaluations();
            }
        });

        // Track execution contexts for frames
        //
        // DESIGN NOTES (tab switching / session management):
        // - Each browser tab (CDP "target") has its own session when attached via Target.attachToTarget
        // - When switching tabs with switchPage(), we attach to a new target and get a new sessionId
        // - CDP events arrive on the WebSocket with a sessionId indicating which session they're from
        // - WITHOUT session filtering: events from old sessions pollute frameContexts map, causing
        //   script execution to use wrong context (e.g., after switchPage('Tab A'), we'd still
        //   execute JS in Tab B because its context was registered last)
        // - WITH session filtering: only events from current session update frameContexts, ensuring
        //   script execution targets the correct tab
        // - This was a critical fix for reliable tab switching - without it, switchPage() would
        //   appear to work (target activated) but subsequent operations would affect the wrong tab
        //
        cdp.on("Runtime.executionContextCreated", event -> {
            // Filter by session: only process events from current session
            String eventSession = event.getSessionId();
            String currentSession = cdp.getSessionId();
            if (eventSession != null && !eventSession.equals(currentSession)) {
                logger.trace("ignoring executionContextCreated from old session: {}", eventSession);
                return;
            }

            Map<String, Object> context = event.get("context");
            if (context != null) {
                Number contextId = (Number) context.get("id");
                Map<String, Object> auxData = (Map<String, Object>) context.get("auxData");
                if (auxData != null && contextId != null) {
                    String frameId = (String) auxData.get("frameId");
                    Boolean isDefault = (Boolean) auxData.get("isDefault");
                    if (frameId != null && Boolean.TRUE.equals(isDefault)) {
                        frameContexts.put(frameId, contextId.intValue());
                        logger.trace("execution context created: frameId={}, contextId={}", frameId, contextId);
                    }
                }
            }
        });

        cdp.on("Runtime.executionContextsCleared", event -> {
            // Filter by session: only process events from current session (see notes above)
            String eventSession = event.getSessionId();
            String currentSession = cdp.getSessionId();
            if (eventSession != null && !eventSession.equals(currentSession)) {
                logger.trace("ignoring executionContextsCleared from old session: {}", eventSession);
                return;
            }
            frameContexts.clear();
            logger.trace("execution contexts cleared");
        });

        // Tab tracking — Target.targetCreated fires for new tabs opened by
        // window.open(), target="_blank" clicks, ctrl+click, etc. We push entries
        // into openedTargets; callers drain via drainOpenedTargets() without any
        // polling of Target.getTargets.
        //
        // Events fire at browser scope (no sessionId) because DiscoverTargets is
        // a browser-level command. All CdpDriver instances sharing a browser will
        // see them — filter by knownTargetIds (seeded from the browser-wide target
        // list at init) so each driver reports only targets new to IT.
        cdp.on("Target.targetCreated", event -> {
            Map<String, Object> info = event.get("targetInfo");
            if (info == null) return;
            String type = (String) info.get("type");
            if (!"page".equals(type)) return;
            String targetId = (String) info.get("targetId");
            if (targetId == null) return;
            // knownTargetIds.add returns false if already present (seeded or earlier event)
            if (!knownTargetIds.add(targetId)) return;
            if (targetId.equals(currentTargetId)) return;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("targetId", targetId);
            entry.put("url", info.get("url"));
            entry.put("title", info.get("title"));
            openedTargets.add(entry);
            logger.debug("new page target opened: {} url={}", targetId, info.get("url"));
        });
        cdp.on("Target.targetInfoChanged", event -> {
            Map<String, Object> info = event.get("targetInfo");
            if (info == null) return;
            String targetId = (String) info.get("targetId");
            if (targetId == null) return;
            // Refresh queued entries if url/title landed after targetCreated
            for (Map<String, Object> entry : openedTargets) {
                if (targetId.equals(entry.get("targetId"))) {
                    entry.put("url", info.get("url"));
                    entry.put("title", info.get("title"));
                }
            }
        });
        cdp.on("Target.targetDestroyed", event -> {
            String targetId = event.get("targetId");
            if (targetId == null) return;
            knownTargetIds.remove(targetId);
            openedTargets.removeIf(entry -> targetId.equals(entry.get("targetId")));
        });

        // Request interception
        cdp.on("Fetch.requestPaused", this::onRequestPaused);
    }

    @SuppressWarnings("unchecked")
    private void onRequestPaused(CdpEvent event) {
        String requestId = event.get("requestId");
        Map<String, Object> request = event.get("request");
        String resourceType = event.get("resourceType");

        String url = request != null ? (String) request.get("url") : "";
        String method = request != null ? (String) request.get("method") : "GET";
        Map<String, Object> headers = request != null ? (Map<String, Object>) request.get("headers") : Map.of();
        String postData = request != null ? (String) request.get("postData") : null;

        logger.debug("request intercepted: {} {}", method, url);

        InterceptResponse response = null;
        if (interceptHandler != null) {
            InterceptRequest interceptRequest = new InterceptRequest(requestId, url, method, headers, postData, resourceType);
            try {
                response = interceptHandler.handle(interceptRequest);
            } catch (Exception e) {
                logger.error("intercept handler error: {}", e.getMessage());
            }
        }

        if (response != null) {
            // Fulfill with mock response
            fulfillRequest(requestId, response);
        } else {
            // Continue to network
            continueRequest(requestId);
        }
    }

    private void fulfillRequest(String requestId, InterceptResponse response) {
        CdpMessage message = cdp.method("Fetch.fulfillRequest")
                .param("requestId", requestId)
                .param("responseCode", response.getStatus());

        // Build response headers
        if (response.getHeaders() != null && !response.getHeaders().isEmpty()) {
            java.util.List<Map<String, String>> headersList = new java.util.ArrayList<>();
            for (Map.Entry<String, Object> entry : response.getHeaders().entrySet()) {
                headersList.add(Map.of("name", entry.getKey(), "value", String.valueOf(entry.getValue())));
            }
            message.param("responseHeaders", headersList);
        }

        // Body must be Base64 encoded
        String bodyBase64 = response.getBodyBase64();
        if (bodyBase64 != null && !bodyBase64.isEmpty()) {
            message.param("body", bodyBase64);
        }

        message.send();
    }

    private void continueRequest(String requestId) {
        cdp.method("Fetch.continueRequest")
                .param("requestId", requestId)
                .send();
    }

    // ========== Navigation ==========

    /**
     * Navigate to URL and wait for page load.
     */
    public void setUrl(String url) {
        logger.debug("navigating to: {}", url);

        // Track for diagnostics
        pendingNavigationUrl = url;

        // Reset page state
        domContentEventFired = false;
        framesStillLoading.clear();

        // Navigate
        cdp.method("Page.navigate")
                .param("url", url)
                .send();

        // Skip page load wait for data: and about: URLs - they load synchronously
        // and don't fire normal lifecycle events
        if (url.startsWith("data:") || url.startsWith("about:")) {
            pendingNavigationUrl = null;
            return;
        }

        // Wait for page load based on strategy
        try {
            waitForPageLoad(options.getPageLoadStrategy());
        } finally {
            pendingNavigationUrl = null;
        }
    }

    /**
     * Wait for page to load based on strategy.
     */
    public void waitForPageLoad(PageLoadStrategy strategy) {
        waitForPageLoad(strategy, options.getTimeoutDuration());
    }

    /**
     * Wait for page to load with custom timeout.
     */
    public void waitForPageLoad(PageLoadStrategy strategy, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int pollInterval = 50;
        long lastStaleCheck = 0;

        while (System.currentTimeMillis() < deadline) {
            if (isPageLoadComplete(strategy)) {
                // Verify JS execution works (execution context is ready)
                // This handles the case where page events fired but context isn't ready
                if (verifyJsExecution()) {
                    return;
                }
                // Context not ready yet, keep waiting
                logger.warn("page load complete but JS context not ready yet");
            } else if (domContentEventFired && !framesStillLoading.isEmpty()) {
                // DOM is ready but frames appear to still be loading
                // Periodically verify these frames still exist - frameStoppedLoading
                // event can be lost in CI environments (observed as flaky timeout)
                long now = System.currentTimeMillis();
                if (now - lastStaleCheck > 2000) {
                    lastStaleCheck = now;
                    pruneStaleFrames();
                }
            }
            sleep(pollInterval);
        }

        // Build diagnostic message with comprehensive state info
        String url = pendingNavigationUrl != null ? pendingNavigationUrl : "(unknown)";
        String readyStateInfo = getReadyStateForDiagnostic();
        String jsExecInfo = getJsExecStateForDiagnostic();
        String diagnostic = String.format(
                "page load timeout after %dms - url: %s, strategy: %s, " +
                "domContentEventFired: %s, framesStillLoading: %s, mainFrameId: %s, " +
                "readyState: %s, jsExec: %s, cdpOpen: %s",
                timeout.toMillis(), url, strategy,
                domContentEventFired, framesStillLoading, mainFrameId,
                readyStateInfo, jsExecInfo, cdp.isOpen());
        logger.warn(diagnostic);
        throw new RuntimeException(diagnostic);
    }

    /**
     * Verify that JS execution works in the current context.
     * This ensures the execution context is properly set up after page load.
     */
    private boolean verifyJsExecution() {
        try {
            CdpResponse response = cdp.method("Runtime.evaluate")
                    .param("expression", "typeof document !== 'undefined'")
                    .param("returnByValue", true)
                    .send();
            Object value = response.getResult("result.value");
            return Boolean.TRUE.equals(value);
        } catch (Exception e) {
            logger.warn("JS execution verification failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isPageLoadComplete(PageLoadStrategy strategy) {
        // First check if event was received
        boolean domReady = domContentEventFired;

        // Fallback: if event wasn't received, check document.readyState directly
        // This handles cases where the event fired before our handler was registered
        if (!domReady) {
            domReady = checkDocumentReadyState();
            if (domReady && !domContentEventFired) {
                logger.warn("retry succeeded: document.readyState fallback (event was missed)");
                domContentEventFired = true; // Update flag so we don't keep checking
            }
        }

        return switch (strategy) {
            case DOMCONTENT -> domReady;
            case DOMCONTENT_AND_FRAMES -> domReady && framesStillLoading.isEmpty();
            case LOAD, NETWORKIDLE -> {
                // For now, use DOMCONTENT_AND_FRAMES behavior
                // Full LOAD and NETWORKIDLE implementation will be added later
                yield domReady && framesStillLoading.isEmpty();
            }
        };
    }

    /**
     * Remove stale entries from framesStillLoading by checking the actual frame tree.
     * Handles the case where Page.frameStoppedLoading or Page.frameDetached events were lost.
     */
    @SuppressWarnings("unchecked")
    private void pruneStaleFrames() {
        if (framesStillLoading.isEmpty()) {
            return;
        }
        try {
            CdpResponse response = cdp.method("Page.getFrameTree").send();
            List<Map<String, Object>> childFrames = response.getResult("frameTree.childFrames");
            Set<String> activeFrameIds = new HashSet<>();
            if (childFrames != null) {
                for (Map<String, Object> frameData : childFrames) {
                    Map<String, Object> frame = (Map<String, Object>) frameData.get("frame");
                    if (frame != null) {
                        activeFrameIds.add((String) frame.get("id"));
                    }
                }
            }
            int before = framesStillLoading.size();
            framesStillLoading.removeIf(id -> !activeFrameIds.contains(id));
            int removed = before - framesStillLoading.size();
            if (removed > 0) {
                logger.warn("pruned {} stale frame(s) from framesStillLoading, remaining: {}", removed, framesStillLoading);
            }
        } catch (Exception e) {
            logger.trace("stale frame check failed: {}", e.getMessage());
        }
    }

    /**
     * Check document.readyState directly as fallback for missed events.
     */
    private boolean checkDocumentReadyState() {
        try {
            CdpResponse response = cdp.method("Runtime.evaluate")
                    .param("expression", "document.readyState")
                    .param("returnByValue", true)
                    .send();
            if (response.isError()) {
                // Log the error so we can diagnose CI failures
                logger.warn("readyState check CDP error: {}", response.getErrorMessage());
                return false;
            }
            String readyState = response.getResultAsString("result.value");
            logger.trace("readyState check returned: {}", readyState); // keep trace, this is success path
            return "complete".equals(readyState) || "interactive".equals(readyState);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("websocket not open")) {
                logger.trace("readyState check skipped: websocket not open");
            } else {
                logger.warn("readyState check exception: {}", msg);
            }
            return false;
        }
    }

    /**
     * Get document.readyState for diagnostic output (not for logic).
     */
    private String getReadyStateForDiagnostic() {
        try {
            CdpResponse response = cdp.method("Runtime.evaluate")
                    .param("expression", "document.readyState")
                    .param("returnByValue", true)
                    .send();
            if (response.isError()) {
                return "error:" + response.getErrorMessage();
            }
            return response.getResultAsString("result.value");
        } catch (Exception e) {
            return "exception:" + e.getMessage();
        }
    }

    /**
     * Check if JS execution works for diagnostic output.
     */
    private String getJsExecStateForDiagnostic() {
        try {
            CdpResponse response = cdp.method("Runtime.evaluate")
                    .param("expression", "typeof document")
                    .param("returnByValue", true)
                    .send();
            if (response.isError()) {
                return "error:" + response.getErrorMessage();
            }
            return "ok:" + response.getResultAsString("result.value");
        } catch (Exception e) {
            return "exception:" + e.getMessage();
        }
    }

    /**
     * Get current URL.
     */
    public String getUrl() {
        CdpResponse response = cdpEval("window.location.href");
        return response.getResultAsString("result.value");
    }

    /**
     * Get page title.
     */
    public String getTitle() {
        CdpResponse response = cdpEval("document.title");
        return response.getResultAsString("result.value");
    }

    // ========== JavaScript Evaluation ==========

    /**
     * Ensure Karate JS runtime is injected (wildcard locators, shared utilities).
     */
    private void ensureKjsRuntime() {
        try {
            Boolean exists = (Boolean) evalDirect("typeof window.__kjs !== 'undefined'");
            if (!Boolean.TRUE.equals(exists)) {
                evalDirect(DRIVER_JS);
            }
        } catch (Exception e) {
            // Ignore - may fail during navigation
        }
    }

    /**
     * Execute JavaScript and return result.
     */
    public Object script(String expression) {
        // Inject Karate JS runtime if needed
        if (expression.contains("__kjs")) {
            ensureKjsRuntime();
        }
        logger.trace("script: {}", truncate(expression, 200));
        CdpResponse response = cdpEval(expression);
        return extractJsValue(response, expression);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * Execute JavaScript without checking for wildcard support (used internally).
     */
    private Object evalDirect(String expression) {
        CdpResponse response = cdpEval(expression);
        return extractJsValue(response, expression);
    }

    /**
     * Execute JavaScript via CDP Runtime.evaluate with retry logic for transient errors.
     *
     * DESIGN NOTES (for future maintainers):
     * - Transient context errors occur when execution context is destroyed (e.g., navigation)
     * - Simple sleep-and-retry works well; CDP events will update frameContexts in background
     * - AVOID recreating frame contexts on error (calling ensureFrameContext) - this caused
     *   flaky tests because it could create a new isolated world while the page was still
     *   transitioning, leading to stale context IDs
     * - First retry is common and expected (e.g., click triggers navigation, next call fails)
     * - Multiple retries may indicate a real problem worth investigating
     */
    private CdpResponse cdpEval(String expression) {
        // Fail fast if a dialog is already open and blocking. A pre-existing
        // unhandled dialog means any new Runtime.evaluate would be cancelled
        // by our Page.javascriptDialogOpening handler (or hang until the 30s
        // CDP timeout). Users should register onDialog() or call dialog(true|false)
        // before further script activity.
        if (dialogHandler == null && currentDialog != null && !currentDialog.isHandled()) {
            throw new DialogOpenedException("dialog is blocking Runtime.evaluate");
        }
        int maxRetries = options.getRetryCount();
        int retryInterval = options.getRetryInterval();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            CdpMessage message = cdp.method("Runtime.evaluate")
                    .param("expression", expression)
                    .param("returnByValue", true);

            // Use explicit context ID for reliable frame targeting (see getFrameContext notes)
            Integer contextId = getFrameContext();
            if (contextId != null) {
                message.param("contextId", contextId);
            }

            CdpResponse response;
            try {
                response = message.send();
            } catch (DialogOpenedException e) {
                // The script itself triggered a blocking JS dialog (alert/confirm/
                // prompt/beforeunload). Chrome suspends Runtime.evaluate until the
                // dialog is handled, and our Page.javascriptDialogOpening handler
                // cancels the pending eval to avoid a 30s CDP timeout.
                //
                // From the user's perspective this is success, not failure — the
                // script did exactly what they asked. The dialog is captured and
                // accessible via getDialog() / getDialogText() / driver.dialogText
                // and can be resolved with dialog(true|false). Return an empty
                // response so extractJsValue yields null.
                //
                // See: https://github.com/karatelabs/karate/issues/2797
                logger.debug("script opened a dialog, returning null: {}", truncate(expression, 100));
                return new CdpResponse(Map.of());
            }

            // Check for transient context errors that should be retried
            if (isTransientContextError(response)) {
                if (attempt < maxRetries) {
                    // Simple retry with sleep - let CDP events update context in background
                    logger.warn("transient context error, retry {}/{}: {}", attempt + 1, maxRetries, truncate(expression, 100));
                    sleep(retryInterval);
                    continue;
                }
                logger.warn("retry exhausted for transient context error: {}", truncate(expression, 100));
            }

            // Success or non-transient error
            if (attempt > 0 && !response.isError()) {
                logger.warn("retry succeeded after {} attempt(s): eval {}", attempt, truncate(expression, 50));
            }
            return response;
        }

        // Should not reach here, but return last response if somehow we do
        throw new RuntimeException("eval retry logic error for: " + expression);
    }

    /**
     * Check if a CDP response indicates a transient execution context error
     * that should be retried.
     *
     * DESIGN NOTES:
     * - CDP error code -32000 is a generic "server error" used for ALL execution context issues
     * - Chrome uses this code for: context destroyed, context not found, target navigated, etc.
     * - Rather than matching specific messages (which may vary by Chrome version), we retry
     *   all -32000 errors since they're all related to transient context state
     * - This is more robust than Puppeteer's message-matching approach and handles edge cases
     *   like container/remote Chrome environments that may have different error messages
     */
    static boolean isTransientContextError(CdpResponse response) {
        if (!response.isError()) {
            return false;
        }
        // CDP error code -32000 is a generic "server error" used for execution context issues
        // BUT also for non-transient errors like serialization failures
        Integer errorCode = response.getErrorCode();
        if (errorCode != null && errorCode == -32000) {
            String message = response.getErrorMessage();
            // "Object reference chain is too long" means returnByValue can't serialize
            // the result (e.g. window.open() returns a Window object) - not transient
            if (message != null && message.contains("Object reference chain is too long")) {
                logger.trace("non-transient -32000 error, will not retry: {}", message);
                return false;
            }
            logger.trace("transient context error detected: {}", response.getErrorMessage());
            return true;
        }
        return false;
    }

    /**
     * Get execution context ID for the current frame.
     *
     * DESIGN NOTES:
     * - When in main frame (currentFrame == null), we explicitly use mainFrameId context
     * - Previously returned null for main frame, relying on CDP's "default" context
     * - This caused flaky "Switch back to main frame" tests because CDP's default context
     *   is unreliable immediately after frame navigation (switchFrame(null))
     * - The frameContexts map is updated by Runtime.executionContextCreated events
     * - Returns null if context not yet registered (rare, handled by retry logic)
     */
    private Integer getFrameContext() {
        if (currentFrame == null) {
            // Explicit main frame context - more reliable than CDP's default after frame switches
            return frameContexts.get(mainFrameId);
        }
        return frameContexts.get(currentFrame.id);
    }

    private Object extractJsValue(CdpResponse response, String expression) {
        if (response.isError()) {
            String message = response.getErrorMessage();
            if (message != null && message.contains("Object reference chain is too long")) {
                logger.debug("script result not serializable, returning null: {}", truncate(expression, 100));
                return null;
            }
            logger.warn("JS error for expression: {}", truncate(expression, 100));
            throw new RuntimeException("JS error: " + response.getError());
        }
        Object exceptionDetails = response.getResult("exceptionDetails");
        if (exceptionDetails != null) {
            // Try to get detailed error message from exception object
            String description = response.getResultAsString("exceptionDetails.exception.description");
            if (description != null && !description.isEmpty()) {
                logger.warn("JS exception for: {} -> {}", truncate(expression, 100), truncate(description, 200));
                throw new RuntimeException("JS exception: " + description);
            }
            // Fall back to text (usually just "Uncaught")
            String text = response.getResultAsString("exceptionDetails.text");
            logger.warn("JS exception for: {} -> {}", truncate(expression, 100), text);
            throw new RuntimeException("JS exception: " + text);
        }
        Object value = response.getResult("result.value");
        logger.trace("script result: {}", value);
        return value;
    }

    // ========== Screenshot ==========

    /**
     * Take screenshot and return PNG bytes.
     */
    public byte[] screenshot() {
        return screenshot(false);
    }

    /**
     * Take screenshot, optionally embed in report.
     */
    public byte[] screenshot(boolean embed) {
        CdpResponse response = cdp.method("Page.captureScreenshot")
                .param("format", "png")
                .send();

        String base64 = response.getResultAsString("data");
        byte[] bytes = Base64.getDecoder().decode(base64);

        if (embed) {
            LogContext ctx = LogContext.get();
            ctx.embed(bytes, "image/png", "screenshot.png");
        }

        return bytes;
    }

    // ========== Dialog Handling ==========

    /**
     * Register a handler for JavaScript dialogs (alert, confirm, prompt, beforeunload).
     * When a dialog opens, the handler is called with a Dialog object.
     * The handler should call dialog.accept() or dialog.dismiss() to resolve the dialog.
     *
     * @param handler the dialog handler, or null to remove the handler
     */
    public void onDialog(DialogHandler handler) {
        this.dialogHandler = handler;
    }

    /**
     * Get the current dialog message.
     *
     * @return the dialog message, or null if no dialog is open
     */
    public String getDialogText() {
        return currentDialogText;
    }

    /**
     * Get the current dialog if one is open.
     * This is useful for detecting dialogs that appear after actions (e.g., click).
     * The dialog remains available until handled via dialog() or the Dialog methods.
     *
     * @return the current Dialog, or null if no dialog is open or already handled
     */
    @Override
    public Dialog getDialog() {
        // Clear dialog reference if it was already handled
        if (currentDialog != null && currentDialog.isHandled()) {
            currentDialog = null;
            currentDialogText = null;
        }
        return currentDialog;
    }

    /**
     * Accept or dismiss the current dialog.
     * This is used when no DialogHandler is registered.
     *
     * @param accept true to accept (OK), false to dismiss (Cancel)
     */
    public void dialog(boolean accept) {
        dialog(accept, null);
    }

    /**
     * Accept or dismiss the current dialog with optional prompt input.
     * This is used when no DialogHandler is registered.
     *
     * @param accept true to accept (OK), false to dismiss (Cancel)
     * @param input the text to enter for prompt dialogs (ignored if accept is false)
     */
    public void dialog(boolean accept, String input) {
        CdpMessage message = cdp.method("Page.handleJavaScriptDialog")
                .param("accept", accept);
        if (accept && input != null) {
            message.param("promptText", input);
        }
        message.send();
        currentDialogText = null;
        currentDialog = null;
    }

    // ========== Frame Switching ==========
    //
    // FLAKY TEST HISTORY:
    // A frame test "Click and result in frame" occasionally failed with:
    //   waitForText('#frame-result', 'Frame button clicked!') timeout
    //
    // Root cause analysis and fixes applied:
    // 1. [FIXED] Execution context not ready: After switchFrame(), ensureFrameContext()
    //    now calls waitForFrameContextReady() which polls until JS execution succeeds
    //    in the frame. Previously we only ensured the context existed, not that it was
    //    ready for execution.
    //
    // Remaining theories (if issues persist):
    // 2. Parallel scenario timing: Multiple frame scenarios running concurrently on
    //    DIFFERENT tabs (via PooledDriverProvider) should be isolated, but if Chrome
    //    has any shared state or resource contention, it could cause timing issues.
    // 3. Click didn't register: The button click might have fired before the iframe's
    //    JavaScript handler was fully attached.
    //
    // Mitigation options (if issues persist):
    // - Add @lock=frames tag to serialize frame tests (like @lock=tabs for tab tests)
    //

    /**
     * Switch to an iframe by index in the frame tree.
     *
     * @param index the zero-based index of the frame
     */
    @SuppressWarnings("unchecked")
    public void switchFrame(int index) {
        // Wait for frame to appear in frame tree (frames may load after page)
        List<Map<String, Object>> childFrames = waitForChildFrames(index);

        Map<String, Object> frameData = childFrames.get(index);
        Map<String, Object> frame = (Map<String, Object>) frameData.get("frame");
        String frameId = (String) frame.get("id");
        String url = (String) frame.get("url");
        String name = (String) frame.get("name");

        currentFrame = new Frame(frameId, url, name);
        logger.debug("switched to frame by index {}: {}", index, currentFrame);

        // Ensure we have execution context for this frame
        ensureFrameContext(frameId);
    }

    /**
     * Wait for child frames to be available in the frame tree.
     * Frames may load asynchronously after the main page.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> waitForChildFrames(int minIndex) {
        // Use holder to capture result since lambda needs effectively final variable
        final List<Map<String, Object>>[] holder = new List[1];

        boolean found = retry("frame at index " + minIndex, () -> {
            CdpResponse response = cdp.method("Page.getFrameTree").send();
            List<Map<String, Object>> childFrames = response.getResult("frameTree.childFrames");
            if (childFrames != null && minIndex >= 0 && minIndex < childFrames.size()) {
                holder[0] = childFrames;
                return true;
            }
            return false;
        });

        if (!found) {
            throw new DriverException("no frame at index: " + minIndex + " after " + options.getRetryCount() + " retries");
        }
        return holder[0];
    }

    /**
     * Switch to an iframe by locator (CSS, XPath, or wildcard).
     * Pass null to switch back to the main frame.
     *
     * @param locator the locator for the iframe element, or null to return to main frame
     */
    @SuppressWarnings("unchecked")
    public void switchFrame(String locator) {
        if (locator == null) {
            // Switch back to main frame
            currentFrame = null;
            logger.debug("switched to main frame");
            return;
        }

        // Wait for frame element to exist (same retry logic as other element operations)
        retryIfNeeded(locator);

        // Find the iframe element and get its frame ID
        String js = Locators.wrapInFunctionInvoke(
                "var e = " + Locators.selector(locator) + ";" +
                        " if (!e) return null;" +
                        " if (e.tagName !== 'IFRAME' && e.tagName !== 'FRAME') return { error: 'not a frame element' };" +
                        " return { " +
                        "   name: e.name || ''," +
                        "   src: e.src || ''" +
                        " }");
        Object result = script(js);

        if (result == null) {
            throw new DriverException("frame not found: " + locator);
        }

        Map<String, Object> frameInfo = (Map<String, Object>) result;
        if (frameInfo.containsKey("error")) {
            throw new DriverException("locator is not a frame: " + locator);
        }

        // Get frame ID from frame tree by matching name or src
        String targetName = (String) frameInfo.get("name");
        String targetSrc = (String) frameInfo.get("src");

        CdpResponse response = cdp.method("Page.getFrameTree").send();
        List<Map<String, Object>> childFrames = response.getResult("frameTree.childFrames");

        if (childFrames == null || childFrames.isEmpty()) {
            throw new DriverException("no child frames in page");
        }

        // Find matching frame in tree
        String frameId = null;
        String url = null;
        String name = null;

        for (Map<String, Object> frameData : childFrames) {
            Map<String, Object> frame = (Map<String, Object>) frameData.get("frame");
            String fId = (String) frame.get("id");
            String fUrl = (String) frame.get("url");
            String fName = (String) frame.get("name");

            // Match by name if provided, otherwise by URL
            boolean matches = false;
            if (targetName != null && !targetName.isEmpty() && targetName.equals(fName)) {
                matches = true;
            } else if (targetSrc != null && !targetSrc.isEmpty() && fUrl != null && fUrl.contains(targetSrc)) {
                matches = true;
            } else if ((targetName == null || targetName.isEmpty()) && (targetSrc == null || targetSrc.isEmpty())) {
                // No name or src - use first frame
                matches = true;
            }

            if (matches) {
                frameId = fId;
                url = fUrl;
                name = fName;
                break;
            }
        }

        if (frameId == null) {
            throw new DriverException("could not find frame for locator: " + locator);
        }

        currentFrame = new Frame(frameId, url, name);
        logger.debug("switched to frame by locator {}: {}", locator, currentFrame);

        // Ensure we have execution context for this frame
        ensureFrameContext(frameId);
    }

    /**
     * Get the current frame, or null if in main frame.
     *
     * @return the current frame info, or null
     */
    public Map<String, Object> getCurrentFrame() {
        if (currentFrame == null) {
            return null;
        }
        return Map.of(
                "id", currentFrame.id,
                "url", currentFrame.url != null ? currentFrame.url : "",
                "name", currentFrame.name != null ? currentFrame.name : ""
        );
    }

    private void ensureFrameContext(String frameId) {
        // First, wait for the main world context to arrive via Runtime.executionContextCreated.
        // IMPORTANT: Do NOT immediately fall back to Page.createIsolatedWorld - isolated worlds
        // are separate JS contexts where variables set by page scripts (e.g., window.frameValue)
        // are not visible. This caused flaky "Switch back to main frame" tests where
        // script('window.frameValue') returned null because it ran in an isolated world
        // instead of the iframe's main world.
        if (!frameContexts.containsKey(frameId)) {
            int maxWaitMs = 1000;
            int pollInterval = 50;
            long deadline = System.currentTimeMillis() + maxWaitMs;
            while (System.currentTimeMillis() < deadline) {
                if (frameContexts.containsKey(frameId)) {
                    logger.debug("frame context arrived via event: frameId={}", frameId);
                    break;
                }
                sleep(pollInterval);
            }
        }
        // Last resort: create an isolated world if the main world context never arrived.
        // This is less ideal (page-set variables won't be visible) but allows basic DOM access.
        if (!frameContexts.containsKey(frameId)) {
            logger.warn("frame context not received via event, falling back to isolated world: {}", frameId);
            try {
                CdpResponse response = cdp.method("Page.createIsolatedWorld")
                        .param("frameId", frameId)
                        .send();
                Integer contextId = response.getResult("executionContextId");
                if (contextId != null) {
                    frameContexts.put(frameId, contextId);
                    logger.debug("created isolated world for frame {}: contextId={}", frameId, contextId);
                }
            } catch (Exception e) {
                logger.warn("failed to create isolated world for frame {}: {}", frameId, e.getMessage());
            }
        }

        // Verify the frame context is alive and ready for JS execution
        // This is critical for flaky test prevention - the context may exist but
        // the frame's document might not be ready yet (still loading)
        waitForFrameContextReady(frameId);
    }

    /**
     * Wait for a frame's execution context to be ready for JS execution.
     * Similar to waitForMainFrameContext() but for iframe contexts.
     */
    private void waitForFrameContextReady(String frameId) {
        int maxWaitMs = 1000;
        int pollInterval = 50;
        long deadline = System.currentTimeMillis() + maxWaitMs;

        while (System.currentTimeMillis() < deadline) {
            Integer contextId = frameContexts.get(frameId);
            if (contextId != null) {
                try {
                    CdpResponse response = cdp.method("Runtime.evaluate")
                            .param("expression", "typeof document !== 'undefined'")
                            .param("returnByValue", true)
                            .param("contextId", contextId)
                            .send();
                    if (!response.isError() && Boolean.TRUE.equals(response.getResult("result.value"))) {
                        logger.trace("frame context ready: frameId={}, contextId={}", frameId, contextId);
                        return;
                    }
                } catch (Exception e) {
                    // Context not ready yet, will retry
                    logger.trace("frame context not ready yet: {}", e.getMessage());
                }
            }
            sleep(pollInterval);
        }
        // Timeout is not fatal - retry logic in script() will handle it
        logger.warn("timeout waiting for frame context: frameId={} (will retry on first use)", frameId);
    }

    // ========== Lifecycle ==========

    /**
     * Close driver and browser.
     */
    public void quit() {
        if (terminated) {
            return;
        }
        terminated = true;
        ACTIVE.remove(this);

        logger.debug("quitting CDP driver");

        // Close CDP connection
        if (cdp != null) {
            try {
                cdp.close();
            } catch (Exception e) {
                logger.warn("error closing CDP: {}", e.getMessage());
            }
        }

        // Close browser if we launched it
        if (launcher != null) {
            launcher.closeAndWait();
        }
    }

    /**
     * Close the current tab/page.
     * After closing, automatically switches to another open tab if available.
     * If this is the last tab, creates a blank page first to keep the browser alive.
     *
     * DESIGN NOTE: We must switch to another tab BEFORE closing the current one.
     * When connected via page-specific WebSocket URL (e.g., /devtools/page/xxx),
     * closing that page also closes the WebSocket connection. By switching first,
     * we establish a new session on a different target, then safely close the old one.
     */
    public void close() {
        if (terminated || currentTargetId == null) {
            return;
        }

        String targetToClose = currentTargetId;
        logger.debug("close() - target to close: {}", targetToClose);

        // Get all pages and find one to switch to before closing
        List<String> allPages = getPages();
        logger.debug("close() - all pages: {}", allPages);

        String nextTarget = null;
        for (String pageId : allPages) {
            if (!pageId.equals(targetToClose)) {
                nextTarget = pageId;
                break;
            }
        }
        logger.debug("close() - switching to: {}", nextTarget);

        if (nextTarget == null) {
            // Last tab - create a blank page to keep browser alive
            // Uses browserMethod() because Target.createTarget is browser-level
            CdpResponse response = cdp.browserMethod("Target.createTarget")
                    .param("url", "about:blank")
                    .send();
            nextTarget = response.getResult("targetId");
            logger.debug("close() - created blank tab: {}", nextTarget);
            if (nextTarget == null) {
                logger.warn("failed to create blank tab, quitting driver");
                quit();
                return;
            }
        }

        // Switch to another tab first (establishes new session)
        activateTarget(nextTarget);
        logger.debug("close() - switched to: {}, now closing: {}", nextTarget, targetToClose);

        // Now safe to close the old target (browser-level command)
        cdp.browserMethod("Target.closeTarget")
                .param("targetId", targetToClose)
                .send();
        logger.debug("close() - closed: {}", targetToClose);

        // Wait for target to actually be removed from the page list
        // Target.closeTarget returns before the target is fully removed, causing
        // race conditions where getPages() still sees the old tab
        waitForTargetRemoved(targetToClose);
    }

    /**
     * Wait for a target to be removed from the page list after closing.
     * This prevents race conditions where getPages() is called immediately after close().
     */
    private void waitForTargetRemoved(String targetId) {
        int maxWaitMs = 1000;
        int pollInterval = 50;
        long deadline = System.currentTimeMillis() + maxWaitMs;

        while (System.currentTimeMillis() < deadline) {
            List<String> pages = getPages();
            if (!pages.contains(targetId)) {
                logger.trace("target removed from page list: {}", targetId);
                return;
            }
            sleep(pollInterval);
        }
        logger.warn("timeout waiting for target to be removed: {}", targetId);
    }

    public boolean isTerminated() {
        return terminated;
    }

    /**
     * Bounded liveness probe for {@link PooledDriverProvider} reset.
     * <p>
     * Sends a trivial {@code Runtime.evaluate("1")} with a 3-second CDP timeout (vs
     * the default 30s). Returns false on any error, timeout, or closed socket — the
     * caller treats false as "discard this driver, create a fresh one for the next
     * scenario."
     * </p>
     * <p>
     * <b>Why this exists (context for future maintainers):</b> observed in CI as
     * deterministic "CDP timeout for: Page.navigate" failures in cookie.feature and
     * keys.feature (same scenarios every run, alternating cookie pattern — classic
     * "poisoned pool driver" signature). Every failing run was preceded by
     * tab-switch.feature, which makes 5 activateTarget / Target.attachToTarget calls
     * back-to-back. Each attach creates a new session on the driver's websocket and
     * old sessions are never detached — the accumulated session state appears to
     * leave Chrome unable to respond to Page.navigate on that specific driver, while
     * the other pool driver stays healthy. Without this probe the broken driver kept
     * getting handed back out and every other cookie scenario timed out at 30s.
     * </p>
     * <p>
     * We probe Runtime.evaluate rather than Page.navigate because it's cheap and
     * local (no network). If even that is stuck, the driver is definitively done.
     * </p>
     */
    @Override
    public boolean isResponsive() {
        if (terminated || !cdp.isOpen()) {
            return false;
        }
        try {
            CdpResponse response = cdp.method("Runtime.evaluate")
                    .param("expression", "1")
                    .param("returnByValue", true)
                    .timeout(Duration.ofSeconds(3))
                    .send();
            return !response.isError();
        } catch (Exception e) {
            logger.warn("isResponsive check failed: {}", e.getMessage());
            return false;
        }
    }

    // ========== Accessors ==========

    public CdpClient getCdpClient() {
        return cdp;
    }

    @Override
    public DriverOptions getOptions() {
        return options;
    }

    /**
     * Get the CDP-specific options.
     */
    public CdpDriverOptions getCdpOptions() {
        return options;
    }

    // ========== Element Operations ==========

    /**
     * Click an element.
     */
    public Element click(String locator) {
        logger.debug("click: {}", locator);
        retryIfNeeded(locator);
        script(Locators.clickJs(locator));
        return BaseElement.of(this, locator);
    }

    /**
     * Focus an element.
     */
    public Element focus(String locator) {
        logger.debug("focus: {}", locator);
        retryIfNeeded(locator);
        script(Locators.focusJs(locator));
        return BaseElement.of(this, locator);
    }

    /**
     * Clear an input element.
     */
    public Element clear(String locator) {
        logger.debug("clear: {}", locator);
        retryIfNeeded(locator);
        script(Locators.clearJs(locator));
        return BaseElement.of(this, locator);
    }

    /**
     * Input text into an element using CDP keypresses.
     * For time/date inputs that don't respond to CDP keystrokes, uses JS value assignment.
     */
    public Element input(String locator, String value) {
        logger.debug("input: {} <- {}", locator, value);
        retryIfNeeded(locator);
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
     * Set the value of an input element.
     */
    public Element value(String locator, String value) {
        logger.debug("value: {} <- {}", locator, value);
        retryIfNeeded(locator);
        script(Locators.inputJs(locator, value));
        return BaseElement.of(this, locator);
    }

    /**
     * Select an option from a dropdown by text or value.
     */
    public Element select(String locator, String text) {
        logger.debug("select: {} <- {}", locator, text);
        retryIfNeeded(locator);
        script(Locators.optionSelector(locator, text));
        return BaseElement.of(this, locator);
    }

    /**
     * Select an option from a dropdown by index.
     */
    public Element select(String locator, int index) {
        logger.debug("select: {} <- index {}", locator, index);
        retryIfNeeded(locator);
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
    public Element scroll(String locator) {
        logger.debug("scroll: {}", locator);
        retryIfNeeded(locator);
        script(Locators.scrollJs(locator));
        return BaseElement.of(this, locator);
    }

    /**
     * Highlight an element.
     */
    public Element highlight(String locator) {
        logger.debug("highlight: {}", locator);
        retryIfNeeded(locator);
        script(Locators.highlight(locator, options.getHighlightDuration()));
        return BaseElement.of(this, locator);
    }

    // ========== Element State ==========

    /**
     * Get the text content of an element.
     */
    public String text(String locator) {
        retryIfNeeded(locator);
        return (String) script(Locators.textJs(locator));
    }

    /**
     * Get the outer HTML of an element.
     */
    public String html(String locator) {
        retryIfNeeded(locator);
        return (String) script(Locators.outerHtmlJs(locator));
    }

    /**
     * Get the value of an input element.
     */
    public String value(String locator) {
        retryIfNeeded(locator);
        return (String) script(Locators.valueJs(locator));
    }

    /**
     * Get an attribute of an element.
     */
    public String attribute(String locator, String name) {
        retryIfNeeded(locator);
        return (String) script(Locators.attributeJs(locator, name));
    }

    /**
     * Get a property of an element.
     */
    public Object property(String locator, String name) {
        retryIfNeeded(locator);
        return script(Locators.propertyJs(locator, name));
    }

    /**
     * Check if an element is enabled.
     */
    public boolean enabled(String locator) {
        retryIfNeeded(locator);
        Object result = script(Locators.enabledJs(locator));
        return Boolean.TRUE.equals(result);
    }

    /**
     * Check if an element exists.
     */
    public boolean exists(String locator) {
        Object result = script(Locators.existsJs(locator));
        return Boolean.TRUE.equals(result);
    }

    /**
     * Get the position of an element (absolute).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> position(String locator) {
        retryIfNeeded(locator);
        return (Map<String, Object>) script(Locators.getPositionJs(locator));
    }

    /**
     * Get the position of an element.
     * @param relative if true, returns viewport-relative position
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> position(String locator, boolean relative) {
        retryIfNeeded(locator);
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
     */
    public Element locate(String locator) {
        return BaseElement.of(this, locator);
    }

    /**
     * Find all elements matching a locator.
     */
    public List<Element> locateAll(String locator) {
        // Count matching elements
        String countJs = Locators.countJs(locator);
        Object countResult = script(countJs);
        int count = countResult instanceof Number ? ((Number) countResult).intValue() : 0;

        // Always return mutable ArrayList for JS engine compatibility
        // (List.of() returns ImmutableCollections which have module access restrictions)
        List<Element> elements = new ArrayList<>();
        if (count == 0) {
            return elements;
        }

        // Create Element objects with indexed locators
        for (int i = 0; i < count; i++) {
            // Use JS expression to select nth element from querySelectorAll/evaluate
            String indexedLocator = createIndexedLocator(locator, i);
            elements.add(new BaseElement(this, indexedLocator, true));
        }
        return elements;
    }

    /**
     * Create a JS expression locator for the nth element matching a locator.
     */
    private String createIndexedLocator(String locator, int index) {
        // Expand wildcard to XPath if needed
        if (locator.startsWith("{")) {
            locator = Locators.expandWildcard(locator);
        }

        if (Locators.isXpath(locator)) {
            // For XPath, wrap in () and add index (1-based in XPath)
            String escapedXpath = Locators.escapeForJs(locator);
            return "(document.evaluate(\"(" + escapedXpath + ")[" + (index + 1) + "]\", document, null, 9, null).singleNodeValue)";
        } else {
            // For CSS, use querySelectorAll with array index
            String escapedCss = Locators.escapeForJs(locator);
            return "(document.querySelectorAll(\"" + escapedCss + "\")[" + index + "])";
        }
    }

    /**
     * Find an element that may not exist (optional).
     */
    public Element optional(String locator) {
        return BaseElement.optional(this, locator);
    }

    // ========== Script on Element ==========

    /**
     * Execute a script on an element.
     * The element is available as '_' in the expression.
     * Expression can be a String ("_.value") or JsFunction (_ => _.value).
     */
    public Object script(String locator, Object expression) {
        retryIfNeeded(locator);
        String js = Locators.scriptSelector(locator, expression);
        return script(js);
    }

    /**
     * Execute a script on all matching elements.
     * Each element is available as '_' in the expression.
     * Expression can be a String or JsFunction.
     */
    @SuppressWarnings("unchecked")
    public List<Object> scriptAll(String locator, Object expression) {
        retryIfNeeded(locator);
        String js = Locators.scriptAllSelector(locator, expression);
        return (List<Object>) script(js);
    }

    // ========== Auto-Wait Helper ==========

    /**
     * Auto-wait for element to exist before operations.
     * Uses retryCount and retryInterval from options.
     * This is called automatically before element operations to reduce flaky tests.
     */
    private void retryIfNeeded(String locator) {
        boolean found = retry("element: " + locator, () -> exists(locator));
        if (!found) {
            // Include driver state in exception for better diagnostics
            throw new DriverException("element not found after " + options.getRetryCount() +
                    " retries: " + locator + " | " + getDriverState());
        }
    }

    // ========== Wait Methods ==========

    /**
     * Wait for an element to exist.
     */
    public Element waitFor(String locator) {
        return waitFor(locator, options.getTimeoutDuration());
    }

    /**
     * Wait for an element to exist with custom timeout.
     */
    public Element waitFor(String locator, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int pollInterval = options.getRetryInterval();

        while (System.currentTimeMillis() < deadline) {
            if (exists(locator)) {
                return BaseElement.of(this, locator);
            }
            sleep(pollInterval);
        }

        throw new DriverException("timeout waiting for element: " + locator);
    }

    /**
     * Wait for any of the locators to match.
     */
    public Element waitForAny(String locator1, String locator2) {
        return waitForAny(new String[]{locator1, locator2});
    }

    /**
     * Wait for any of the locators to match.
     */
    public Element waitForAny(String[] locators) {
        return waitForAny(locators, options.getTimeoutDuration());
    }

    /**
     * Wait for any of the locators to match with custom timeout.
     */
    public Element waitForAny(String[] locators, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int pollInterval = options.getRetryInterval();

        while (System.currentTimeMillis() < deadline) {
            for (String locator : locators) {
                if (exists(locator)) {
                    return BaseElement.of(this, locator);
                }
            }
            sleep(pollInterval);
        }

        throw new DriverException("timeout waiting for any element: " + String.join(", ", locators));
    }

    /**
     * Wait for an element to contain specific text.
     */
    public Element waitForText(String locator, String expected) {
        return waitForText(locator, expected, options.getTimeoutDuration());
    }

    /**
     * Wait for an element to contain specific text with custom timeout.
     */
    public Element waitForText(String locator, String expected, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int pollInterval = options.getRetryInterval();

        while (System.currentTimeMillis() < deadline) {
            if (exists(locator)) {
                String text = text(locator);
                if (text != null && text.contains(expected)) {
                    return BaseElement.of(this, locator);
                }
            }
            sleep(pollInterval);
        }

        throw new DriverException("timeout waiting for text '" + expected + "' in element: " + locator);
    }

    /**
     * Wait for an element to be enabled.
     */
    public Element waitForEnabled(String locator) {
        return waitForEnabled(locator, options.getTimeoutDuration());
    }

    /**
     * Wait for an element to be enabled with custom timeout.
     */
    public Element waitForEnabled(String locator, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int pollInterval = options.getRetryInterval();

        while (System.currentTimeMillis() < deadline) {
            if (exists(locator) && enabled(locator)) {
                return BaseElement.of(this, locator);
            }
            sleep(pollInterval);
        }

        throw new DriverException("timeout waiting for element to be enabled: " + locator);
    }

    /**
     * Wait for URL to contain expected string.
     */
    public String waitForUrl(String expected) {
        return waitForUrl(expected, options.getTimeoutDuration());
    }

    /**
     * Wait for URL to contain expected string with custom timeout.
     */
    public String waitForUrl(String expected, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int pollInterval = options.getRetryInterval();

        while (System.currentTimeMillis() < deadline) {
            String url = getUrl();
            if (url != null && url.contains(expected)) {
                return url;
            }
            sleep(pollInterval);
        }

        throw new DriverException("timeout waiting for URL to contain: " + expected);
    }

    /**
     * Wait until a JavaScript expression on an element evaluates to truthy.
     * The element is available as '_' in the expression.
     */
    public Element waitUntil(String locator, String expression) {
        return waitUntil(locator, expression, options.getTimeoutDuration());
    }

    /**
     * Wait until a JavaScript expression on an element evaluates to truthy.
     */
    public Element waitUntil(String locator, String expression, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int pollInterval = options.getRetryInterval();

        while (System.currentTimeMillis() < deadline) {
            if (exists(locator)) {
                Object result = script(locator, expression);
                if (Terms.isTruthy(result)) {
                    return BaseElement.of(this, locator);
                }
            }
            sleep(pollInterval);
        }

        throw new DriverException("timeout waiting for condition '" + expression + "' on element: " + locator);
    }

    /**
     * Wait until a JavaScript expression evaluates to truthy.
     */
    public boolean waitUntil(String expression) {
        return waitUntil(expression, options.getTimeoutDuration());
    }

    /**
     * Wait until a JavaScript expression evaluates to truthy with custom timeout.
     */
    public boolean waitUntil(String expression, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int pollInterval = options.getRetryInterval();

        while (System.currentTimeMillis() < deadline) {
            Object result = script(expression);
            if (Terms.isTruthy(result)) {
                return true;
            }
            sleep(pollInterval);
        }

        throw new DriverException("timeout waiting for condition: " + expression);
    }

    /**
     * Wait until a supplier returns a truthy value.
     */
    public Object waitUntil(Supplier<Object> condition) {
        return waitUntil(condition, options.getTimeoutDuration());
    }

    /**
     * Wait until a supplier returns a truthy value with custom timeout.
     */
    public Object waitUntil(Supplier<Object> condition, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int pollInterval = options.getRetryInterval();

        while (System.currentTimeMillis() < deadline) {
            Object result = condition.get();
            if (Terms.isTruthy(result)) {
                return result;
            }
            sleep(pollInterval);
        }

        throw new DriverException("timeout waiting for condition");
    }

    /**
     * Wait for a specific number of elements to match.
     */
    public List<Element> waitForResultCount(String locator, int count) {
        return waitForResultCount(locator, count, options.getTimeoutDuration());
    }

    /**
     * Wait for a specific number of elements to match with custom timeout.
     */
    public List<Element> waitForResultCount(String locator, int count, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int pollInterval = options.getRetryInterval();

        while (System.currentTimeMillis() < deadline) {
            Object result = script(Locators.countJs(locator));
            int actual = ((Number) result).intValue();
            if (actual == count) {
                return locateAll(locator);
            }
            sleep(pollInterval);
        }

        throw new DriverException("timeout waiting for " + count + " elements: " + locator);
    }

    // ========== Cookies ==========

    /**
     * Get a cookie by name.
     *
     * @param name the cookie name
     * @return the cookie as a Map, or null if not found
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> cookie(String name) {
        CdpResponse response = cdp.method("Network.getCookies").send();
        List<Map<String, Object>> cookies = response.getResult("cookies");
        if (cookies != null) {
            for (Map<String, Object> cookie : cookies) {
                if (name.equals(cookie.get("name"))) {
                    return cookie;
                }
            }
        }
        return null;
    }

    /**
     * Set a cookie.
     *
     * @param cookie the cookie as a Map with keys: name, value, domain, path, secure, httpOnly, sameSite, expires
     */
    public void cookie(Map<String, Object> cookie) {
        logger.debug("set cookie: {}", cookie.get("name"));
        CdpMessage message = cdp.method("Network.setCookie");
        cookie.forEach(message::param);
        message.send();
    }

    /**
     * Delete a cookie by name.
     *
     * @param name the cookie name
     */
    public void deleteCookie(String name) {
        logger.debug("delete cookie: {}", name);
        // Get cookie first to get domain
        Map<String, Object> cookie = cookie(name);
        if (cookie != null) {
            String domain = (String) cookie.get("domain");
            cdp.method("Network.deleteCookies")
                    .param("name", name)
                    .param("domain", domain)
                    .send();
        }
    }

    /**
     * Clear all cookies.
     */
    public void clearCookies() {
        logger.debug("clear all cookies");
        cdp.method("Network.clearBrowserCookies").send();
    }

    /**
     * Get all cookies.
     *
     * @return list of cookies as Maps
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCookies() {
        CdpResponse response = cdp.method("Network.getCookies").send();
        List<Map<String, Object>> cookies = response.getResult("cookies");
        return cookies != null ? cookies : new ArrayList<>();
    }

    // ========== Window Management ==========

    /**
     * Maximize the browser window.
     */
    public void maximize() {
        logger.debug("maximize window");
        CdpResponse response = cdp.method("Browser.getWindowForTarget").send();
        Integer windowId = response.getResult("windowId");
        if (windowId != null) {
            cdp.method("Browser.setWindowBounds")
                    .param("windowId", windowId)
                    .param("bounds", Map.of("windowState", "maximized"))
                    .send();
        }
    }

    /**
     * Minimize the browser window.
     */
    public void minimize() {
        logger.debug("minimize window");
        CdpResponse response = cdp.method("Browser.getWindowForTarget").send();
        Integer windowId = response.getResult("windowId");
        if (windowId != null) {
            cdp.method("Browser.setWindowBounds")
                    .param("windowId", windowId)
                    .param("bounds", Map.of("windowState", "minimized"))
                    .send();
        }
    }

    /**
     * Make the browser window fullscreen.
     */
    public void fullscreen() {
        logger.debug("fullscreen window");
        CdpResponse response = cdp.method("Browser.getWindowForTarget").send();
        Integer windowId = response.getResult("windowId");
        if (windowId != null) {
            cdp.method("Browser.setWindowBounds")
                    .param("windowId", windowId)
                    .param("bounds", Map.of("windowState", "fullscreen"))
                    .send();
        }
    }

    /**
     * Get window dimensions and position.
     *
     * @return Map with keys: x, y, width, height, windowState
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDimensions() {
        CdpResponse response = cdp.method("Browser.getWindowForTarget").send();
        Integer windowId = response.getResult("windowId");
        if (windowId != null) {
            CdpResponse boundsResponse = cdp.method("Browser.getWindowBounds")
                    .param("windowId", windowId)
                    .send();
            return boundsResponse.getResult("bounds");
        }
        return Map.of();
    }

    /**
     * Set window dimensions and/or position.
     *
     * @param dimensions Map with optional keys: x, y, width, height, windowState
     */
    public void setDimensions(Map<String, Object> dimensions) {
        logger.debug("set dimensions: {}", dimensions);
        CdpResponse response = cdp.method("Browser.getWindowForTarget").send();
        Integer windowId = response.getResult("windowId");
        if (windowId != null) {
            // First restore to normal state if changing size/position
            if (dimensions.containsKey("width") || dimensions.containsKey("height") ||
                    dimensions.containsKey("x") || dimensions.containsKey("y")) {
                cdp.method("Browser.setWindowBounds")
                        .param("windowId", windowId)
                        .param("bounds", Map.of("windowState", "normal"))
                        .send();
            }
            cdp.method("Browser.setWindowBounds")
                    .param("windowId", windowId)
                    .param("bounds", dimensions)
                    .send();
        }
    }

    // ========== PDF Generation ==========

    /**
     * Generate a PDF of the current page.
     *
     * @param options Map with optional keys: scale, displayHeaderFooter, headerTemplate, footerTemplate,
     *                printBackground, landscape, pageRanges, paperWidth, paperHeight,
     *                marginTop, marginBottom, marginLeft, marginRight
     * @return PDF bytes
     */
    public byte[] pdf(Map<String, Object> options) {
        logger.debug("generate PDF");
        CdpMessage message = cdp.method("Page.printToPDF");
        if (options != null) {
            options.forEach(message::param);
        }
        CdpResponse response = message.send();
        String base64 = response.getResultAsString("data");
        return Base64.getDecoder().decode(base64);
    }

    /**
     * Generate a PDF of the current page with default options.
     *
     * @return PDF bytes
     */
    public byte[] pdf() {
        return pdf(null);
    }

    // ========== Navigation Extras ==========

    /**
     * Refresh the current page.
     */
    public void refresh() {
        logger.debug("refresh page");
        domContentEventFired = false;
        framesStillLoading.clear();
        cdp.method("Page.reload").send();
        waitForPageLoad(options.getPageLoadStrategy());
    }

    /**
     * Reload the page ignoring cache.
     */
    public void reload() {
        logger.debug("reload page (ignore cache)");
        domContentEventFired = false;
        framesStillLoading.clear();
        cdp.method("Page.reload")
                .param("ignoreCache", true)
                .send();
        waitForPageLoad(options.getPageLoadStrategy());
    }

    /**
     * Navigate back in history.
     */
    public void back() {
        logger.debug("navigate back");
        CdpResponse response = cdp.method("Page.getNavigationHistory").send();
        Integer currentIndex = response.getResult("currentIndex");
        List<Map<String, Object>> entries = response.getResult("entries");
        if (currentIndex != null && currentIndex > 0 && entries != null) {
            Map<String, Object> prevEntry = entries.get(currentIndex - 1);
            Integer entryId = (Integer) prevEntry.get("id");
            if (entryId != null) {
                domContentEventFired = false;
                framesStillLoading.clear();
                cdp.method("Page.navigateToHistoryEntry")
                        .param("entryId", entryId)
                        .send();
                waitForPageLoad(options.getPageLoadStrategy());
            }
        }
    }

    /**
     * Navigate forward in history.
     */
    public void forward() {
        logger.debug("navigate forward");
        CdpResponse response = cdp.method("Page.getNavigationHistory").send();
        Integer currentIndex = response.getResult("currentIndex");
        List<Map<String, Object>> entries = response.getResult("entries");
        if (currentIndex != null && entries != null && currentIndex < entries.size() - 1) {
            Map<String, Object> nextEntry = entries.get(currentIndex + 1);
            Integer entryId = (Integer) nextEntry.get("id");
            if (entryId != null) {
                domContentEventFired = false;
                framesStillLoading.clear();
                cdp.method("Page.navigateToHistoryEntry")
                        .param("entryId", entryId)
                        .send();
                waitForPageLoad(options.getPageLoadStrategy());
            }
        }
    }

    /**
     * Activate the browser window (bring to front).
     */
    public void activate() {
        logger.debug("activate window");
        cdp.method("Page.bringToFront").send();
    }

    // ========== Mouse and Keyboard ==========

    /**
     * Get a Mouse object at position (0, 0).
     *
     * @return a new Mouse object
     */
    public Mouse mouse() {
        return new CdpMouse(cdp);
    }

    /**
     * Get a Mouse object positioned at an element's center.
     *
     * @param locator the element locator
     * @return a new Mouse object positioned at the element's center
     */
    @SuppressWarnings("unchecked")
    public Mouse mouse(String locator) {
        Map<String, Object> pos = position(locator, true);
        double x = ((Number) pos.get("x")).doubleValue();
        double y = ((Number) pos.get("y")).doubleValue();
        double width = ((Number) pos.get("width")).doubleValue();
        double height = ((Number) pos.get("height")).doubleValue();
        // Center of element
        return new CdpMouse(cdp, x + width / 2, y + height / 2);
    }

    /**
     * Get a Mouse object at specified coordinates.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return a new Mouse object at the specified position
     */
    public Mouse mouse(Number x, Number y) {
        return new CdpMouse(cdp, x.doubleValue(), y.doubleValue());
    }

    /**
     * Get a Keys object for keyboard input.
     * Returns the same instance to maintain modifier state (e.g., holding Shift across multiple calls).
     *
     * @return the Keys object for this driver
     */
    public Keys keys() {
        if (keysInstance == null) {
            keysInstance = new CdpKeys(cdp);
        }
        return keysInstance;
    }

    // ========== Pages/Tabs Management ==========

    /**
     * Get list of all page targets (tabs).
     * Uses browserMethod() because Target.getTargets is a browser-level command.
     *
     * @return list of target IDs
     */
    @SuppressWarnings("unchecked")
    public List<String> getPages() {
        CdpResponse response = cdp.browserMethod("Target.getTargets").send();
        List<Map<String, Object>> targets = response.getResult("targetInfos");
        List<String> pages = new ArrayList<>();
        if (targets != null) {
            for (Map<String, Object> target : targets) {
                String type = (String) target.get("type");
                if ("page".equals(type)) {
                    String targetId = (String) target.get("targetId");
                    pages.add(targetId);
                }
            }
        }
        return pages;
    }

    /**
     * Get info for all page targets (tabs) — targetId, url, title, active flag.
     * Richer counterpart to {@link #getPages()} for callers that need to present
     * tab info to a user (or LLM) before switching.
     *
     * @return list of maps with keys: targetId, url, title, active
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTargetInfos() {
        CdpResponse response = cdp.browserMethod("Target.getTargets").send();
        List<Map<String, Object>> targets = response.getResult("targetInfos");
        List<Map<String, Object>> result = new ArrayList<>();
        if (targets != null) {
            for (Map<String, Object> target : targets) {
                if (!"page".equals(target.get("type"))) continue;
                Map<String, Object> info = new LinkedHashMap<>();
                String targetId = (String) target.get("targetId");
                info.put("targetId", targetId);
                info.put("url", target.get("url"));
                info.put("title", target.get("title"));
                info.put("active", targetId != null && targetId.equals(currentTargetId));
                result.add(info);
            }
        }
        return result;
    }

    /**
     * Switch to a page by title or URL substring.
     * Includes verification that the switch was successful.
     *
     * @param titleOrUrl the title or URL substring to match
     */
    public void switchPage(String titleOrUrl) {
        logger.debug("switch page by title/url: {}", titleOrUrl);
        CdpResponse response = cdp.method("Target.getTargets").send();
        List<Map<String, Object>> targets = response.getResult("targetInfos");
        if (targets != null) {
            for (Map<String, Object> target : targets) {
                String type = (String) target.get("type");
                if (!"page".equals(type)) continue;

                String title = (String) target.get("title");
                String url = (String) target.get("url");
                if ((title != null && title.contains(titleOrUrl)) ||
                        (url != null && url.contains(titleOrUrl))) {
                    String targetId = (String) target.get("targetId");
                    activateTarget(targetId);
                    return;
                }
            }
        }
        throw new DriverException("no page found matching: " + titleOrUrl);
    }

    /**
     * Switch to a page by its backend target ID (unambiguous — avoids URL/title
     * collisions that {@link #switchPage(String)} is vulnerable to).
     */
    @Override
    public void switchPageById(String targetId) {
        logger.debug("switch page by targetId: {}", targetId);
        if (targetId == null) {
            throw new DriverException("targetId is null");
        }
        // Verify the target exists as a page target before activating
        List<String> pages = getPages();
        if (!pages.contains(targetId)) {
            throw new DriverException("no page found with targetId: " + targetId);
        }
        activateTarget(targetId);
    }

    /**
     * Drain the queue of page targets (tabs) opened since the last call.
     * Event-driven — no CDP round-trip. See {@link Driver#drainOpenedTargets()}.
     */
    @Override
    public List<Map<String, Object>> drainOpenedTargets() {
        if (openedTargets.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Map<String, Object>> drained = new ArrayList<>();
        Map<String, Object> entry;
        while ((entry = openedTargets.poll()) != null) {
            drained.add(entry);
        }
        return drained;
    }

    /**
     * Switch to a page by index.
     *
     * @param index the zero-based index
     */
    public void switchPage(int index) {
        logger.debug("switch page by index: {}", index);
        List<String> pages = getPages();
        if (index < 0 || index >= pages.size()) {
            throw new DriverException("no page at index: " + index);
        }
        activateTarget(pages.get(index));
    }

    // ========== Positional Locators ==========

    /**
     * Create a finder for elements to the right of the reference element.
     *
     * @param locator the reference element locator
     * @return a Finder for elements to the right
     */
    public Finder rightOf(String locator) {
        return new Finder(this, locator, Finder.Position.RIGHT_OF);
    }

    /**
     * Create a finder for elements to the left of the reference element.
     *
     * @param locator the reference element locator
     * @return a Finder for elements to the left
     */
    public Finder leftOf(String locator) {
        return new Finder(this, locator, Finder.Position.LEFT_OF);
    }

    /**
     * Create a finder for elements above the reference element.
     *
     * @param locator the reference element locator
     * @return a Finder for elements above
     */
    public Finder above(String locator) {
        return new Finder(this, locator, Finder.Position.ABOVE);
    }

    /**
     * Create a finder for elements below the reference element.
     *
     * @param locator the reference element locator
     * @return a Finder for elements below
     */
    public Finder below(String locator) {
        return new Finder(this, locator, Finder.Position.BELOW);
    }

    /**
     * Create a finder for elements near the reference element.
     *
     * @param locator the reference element locator
     * @return a Finder for nearby elements
     */
    public Finder near(String locator) {
        return new Finder(this, locator, Finder.Position.NEAR);
    }

    // ========== Request Interception ==========

    /**
     * Enable request interception with a handler.
     * The handler is called for each matching request. Return an InterceptResponse to mock,
     * or null to continue to the network.
     *
     * @param patterns URL patterns to intercept (e.g., "*api/*", "https://example.com/*")
     * @param handler the intercept handler
     */
    public void intercept(List<String> patterns, InterceptHandler handler) {
        logger.debug("enable request interception for patterns: {}", patterns);
        this.interceptHandler = handler;

        // Build request patterns for Fetch.enable
        List<Map<String, Object>> requestPatterns = new ArrayList<>();
        for (String pattern : patterns) {
            requestPatterns.add(Map.of("urlPattern", pattern));
        }

        cdp.method("Fetch.enable")
                .param("patterns", requestPatterns)
                .send();
    }

    /**
     * Enable request interception for all requests.
     *
     * @param handler the intercept handler
     */
    public void intercept(InterceptHandler handler) {
        intercept(List.of("*"), handler);
    }

    /**
     * Stop request interception.
     */
    public void stopIntercept() {
        logger.debug("disable request interception");
        this.interceptHandler = null;
        cdp.method("Fetch.disable").send();
    }

    /**
     * Activate and attach to a browser tab (CDP target) for tab switching.
     *
     * DESIGN NOTES (tab switching flow):
     * 1. Target.activateTarget - brings the tab to visual focus in the browser
     * 2. Target.attachToTarget - creates a NEW CDP session for this tab
     *    - Each attachment creates a fresh session, even if previously attached
     *    - The "flatten: true" param sends events on main WebSocket (not nested)
     * 3. Update sessionId - all subsequent CDP commands use this session
     * 4. Re-enable domains - Page/Runtime must be enabled per-session
     *    - This triggers Runtime.executionContextCreated events for new session
     * 5. Get mainFrameId - each tab has its own frame tree
     * 6. Clear frameContexts - old contexts are invalid for new session
     * 7. Wait for context - ensures executionContextCreated event is processed
     *    before returning (prevents "context not found" errors on first script())
     *
     * CRITICAL: Session filtering in event handlers (see setupEventHandlers) ensures
     * that only events from the CURRENT session update frameContexts. Without this,
     * events from old sessions would corrupt the context map.
     */
    private void activateTarget(String targetId) {
        this.currentTargetId = targetId;

        // Browser-level command to bring tab to focus
        cdp.browserMethod("Target.activateTarget")
                .param("targetId", targetId)
                .send();

        // Browser-level command to create session for this tab
        CdpResponse attachResponse = cdp.browserMethod("Target.attachToTarget")
                .param("targetId", targetId)
                .param("flatten", true)
                .send();

        String sessionId = attachResponse.getResultAsString("sessionId");
        if (sessionId != null) {
            // Update the CDP client to use this session
            cdp.setSessionId(sessionId);
        }

        // Re-enable required domains on new target (triggers executionContextCreated)
        cdp.method("Page.enable").send();
        cdp.method("Runtime.enable").send();
        cdp.method("Page.setLifecycleEventsEnabled")
                .param("enabled", true)
                .send();

        // Get new main frame ID (each tab has its own frame tree)
        CdpResponse frameResponse = cdp.method("Page.getFrameTree").send();
        mainFrameId = frameResponse.getResult("frameTree.frame.id");

        // Reset frame state (old contexts are invalid for new session)
        currentFrame = null;
        frameContexts.clear();

        // Wait for execution context to be ready before returning
        // Without this, script() calls immediately after switchPage() fail
        // with "Cannot find context" errors because the executionContextCreated
        // event hasn't been processed yet
        waitForMainFrameContext();
    }

    // ========== Utilities ==========

    /**
     * Centralized retry mechanism with warning logging.
     * Use this for all retry operations to ensure consistent behavior and logging.
     *
     * @param description what we're waiting for (used in warning logs)
     * @param condition   returns true when the condition is met
     * @param maxAttempts maximum number of retry attempts (0 means try once)
     * @param interval    milliseconds between retries
     * @return true if condition was met, false if all retries exhausted
     */
    private boolean retry(String description, Supplier<Boolean> condition, int maxAttempts, int interval) {
        // First attempt (not a retry)
        if (Boolean.TRUE.equals(condition.get())) {
            return true;
        }

        // Log that we're starting retries (helps diagnose flaky tests)
        logger.warn("retry started: {} (max {} attempts, {}ms interval)", description, maxAttempts, interval);

        // Retry loop
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            sleep(interval);
            if (Boolean.TRUE.equals(condition.get())) {
                logger.warn("retry succeeded after {} attempt(s): {}", attempt, description);
                return true;
            }
            logger.warn("retry attempt {}/{} failed for: {}", attempt, maxAttempts, description);
        }

        // Log failure with driver state for diagnostics
        logger.warn("retry FAILED after {} attempts: {} | {}", maxAttempts, description, getDriverState());
        return false;
    }

    /**
     * Centralized retry mechanism using default options.
     */
    private boolean retry(String description, Supplier<Boolean> condition) {
        return retry(description, condition, options.getRetryCount(), options.getRetryInterval());
    }

    /**
     * Get current driver state for diagnostic logging.
     * Captures key state that helps debug flaky test failures.
     */
    private String getDriverState() {
        StringBuilder sb = new StringBuilder();
        sb.append("url=");
        try {
            sb.append(getUrl());
        } catch (Exception e) {
            sb.append("(error: ").append(e.getMessage()).append(")");
        }
        sb.append(", domContentEventFired=").append(domContentEventFired);
        sb.append(", framesLoading=").append(framesStillLoading.size());
        sb.append(", mainFrameId=").append(mainFrameId);
        if (currentFrame != null) {
            sb.append(", currentFrame=").append(currentFrame.id);
        }
        sb.append(", frameContexts=").append(frameContexts.size());
        sb.append(", terminated=").append(terminated);
        return sb.toString();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
