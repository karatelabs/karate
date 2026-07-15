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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
    // volatile: written on the scenario thread (initialize / activateTarget), read by
    // every Page.* event handler on the CDP dispatch thread for main-frame filtering
    private volatile String mainFrameId;
    private volatile String pendingNavigationUrl; // for better timeout diagnostics

    // Document identity for page-load waits. The load signals above are
    // document-anonymous: a late DOMContentLoaded from the PREVIOUS document (on a
    // pooled driver, typically the reset's about:blank navigation, whose setUrl
    // returns without waiting) arriving after setUrl() reset the flags is
    // indistinguishable from the new document's own signal — so setUrl() could
    // declare the page loaded while the tab still showed the old document. Observed
    // in CI as waitFor()/exists()/driver.title landing on the stale page. CDP names
    // every document load with a loaderId: Page.navigate returns the loaderId it
    // started, and Page.lifecycleEvent / Page.frameNavigated carry the loaderId they
    // belong to. Recording the loader alongside each signal and matching against the
    // navigation's own loaderId binds the wait to the exact document requested.
    // Page.reload / Page.navigateToHistoryEntry return no loaderId — but the document
    // they load is by definition a new loader, so refresh/reload/back/forward instead
    // wait for the CURRENT loader to be superseded. Exactly one of expected/superseded
    // is set during a driver-initiated navigation wait; both null = unbound legacy wait.
    private volatile String expectedLoaderId;   // loader the current setUrl() waits on
    private volatile String supersededLoaderId; // loader that refresh/reload/back/forward must replace
    private volatile String domContentLoaderId; // loader of the last main-frame DOMContentLoaded
    private volatile String committedLoaderId;  // loader of the last committed main-frame document (Page.frameNavigated)
    // The committed loader as it stood the instant before the current setUrl() issued
    // Page.navigate. Chrome can commit the requested navigation under a DIFFERENT
    // loaderId than the one Page.navigate returned (it restarts/replaces the navigation
    // internally) — observed in CI as expectedLoaderId never matching committedLoaderId
    // while the requested URL is fully loaded, deadlocking BOTH the isDomReady() match
    // and the readyState-fallback gate until the wait times out on a ready page. This
    // snapshot lets the readyState fallback recognise "a NEW document has committed"
    // (committed loader advanced past this) even when the exact-loader match can't be
    // made, and gate acceptance on the committed document being the requested URL so a
    // stale document (pooled-reset about:blank / previous page) is still rejected.
    private volatile String preNavCommittedLoaderId;
    // Set only during a back()/forward() wait: the target history entry's URL. A
    // same-document history traversal (pushState/fragment entries) produces NO loader
    // activity at all — no frameStartedLoading, no frameNavigated, no DOMContentLoaded —
    // so the superseded-loader wait above could never complete for it. The readyState
    // fallback instead verifies window.location.href against this URL, which also
    // rescues cross-document traversals whose events were lost.
    private volatile String historyTargetUrl;

    // Wake-up latch for waitForPageLoad(). Every load-relevant event (main-frame
    // DOMContentLoaded / frameNavigated, frameStoppedLoading, frameDetached, main
    // context arrival) rotates-then-completes it via nudgeLoadWaiter(), so the wait
    // loop reacts to the event instead of discovering it on the next fixed-interval
    // poll. It is a NUDGE, not the completion signal itself: the waiter re-evaluates
    // isPageLoadComplete()/verifyJsExecution() after every wake-up, and its wait on
    // the latch is capped (2s) so the readyState fallback and pruneStaleFrames still
    // run even if every event is lost (the CI condition those safety nets exist for).
    private volatile CompletableFuture<Void> loadTick = new CompletableFuture<>();

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

    // Readiness futures for NON-main frames, keyed by frameId and completed by the
    // Runtime.executionContextCreated handler — the same event-completed-future
    // pattern as mainContextReady, replacing the old poll of the frameContexts map.
    // A waiter (ensureFrameContext) awaits the frame's future for a short bound; a
    // frame that detaches completes its future with null so the waiter fails fast
    // to the isolated-world last resort instead of sleeping out the bound.
    private final Map<String, CompletableFuture<Integer>> frameContextReady = new ConcurrentHashMap<>();

    // Readiness of the main frame's default execution context, modelled as an
    // event-completed future rather than a polled map read. This is the single
    // authoritative "the page can run script now" signal:
    //   - Runtime.executionContextCreated for the main frame COMPLETES it (the id).
    //   - executionContextsCleared / a tab switch / a cross-document navigation
    //     INVALIDATES it (a fresh, incomplete future), so any operation that needs
    //     the context simply awaits the NEXT one instead of racing a stale/absent id.
    // Replaces the old poll-with-timeout (waitForMainFrameContext) + null/stale
    // getFrameContext + transient-context-error retry duct tape, all of which existed
    // only because there was no reliable readiness signal.
    private volatile CompletableFuture<Integer> mainContextReady = new CompletableFuture<>();
    // Short bound for the per-eval context wait (getFrameContext). The context normally
    // settles in well under this; if it is exceeded we fall back to CDP's default
    // context rather than block the eval. Explicit barriers use the full timeout.
    private static final long CONTEXT_READY_POLL_MS = 2000;

    // Short bound for the best-effort failure-path screenshot (failureScreenshot).
    // A healthy renderer encodes a full-page PNG in well under a second; this cap
    // exists so a stalled renderer can't turn a swallowed diagnostic capture into a
    // full-timeout hang (seen in CI as "CDP timeout for: Page.captureScreenshot").
    private static final Duration FAILURE_SCREENSHOT_TIMEOUT = Duration.ofSeconds(5);

    // OOPIF (Out-of-Process Iframe) tracking
    private final Map<String, Map<String, Object>> oopifTargets = new ConcurrentHashMap<>();
    private final Map<String, String> oopifSessions = new ConcurrentHashMap<>();
    private volatile String pageSessionId; // Tracks the main page's session to switch back

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

    // Pending tab-removal futures keyed by targetId, completed by the
    // Target.targetDestroyed handler. close() registers the future BEFORE sending
    // Target.closeTarget — registering after the send would race the event and
    // strand the waiter on its timeout.
    private final Map<String, CompletableFuture<Void>> pendingTargetRemovals = new ConcurrentHashMap<>();

    // Named init-script modules registered via addInitScript() (insertion-ordered = install order).
    private final Map<String, InitScript> initScripts = new LinkedHashMap<>();
    private final Object initScriptLock = new Object();
    private String runtimeInitId; // install id of the built-in runtime as an on-new-document script (null until first registration)

    private record InitScript(String source, List<String> deps, String cdpId) {
    }

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

    // ========== Script & binding injection ==========

    /**
     * Install a JavaScript source that is evaluated in every new document — the
     * top frame and same-process child frames — before the page's own scripts
     * run, and re-applied on each navigation. Returns an identifier that
     * {@link #removeScriptToEvaluateOnNewDocument(String)} takes to uninstall it.
     *
     * @param source the JavaScript source to install
     * @return the install identifier, or {@code null} if none was returned
     */
    public String addScriptToEvaluateOnNewDocument(String source) {
        CdpResponse response = cdp.method("Page.addScriptToEvaluateOnNewDocument")
                .param("source", source)
                .send();
        return scriptIdentifier(response);
    }

    /**
     * Uninstall a script previously added with
     * {@link #addScriptToEvaluateOnNewDocument(String)}.
     *
     * @param identifier the install identifier returned at add time
     */
    public void removeScriptToEvaluateOnNewDocument(String identifier) {
        cdp.method("Page.removeScriptToEvaluateOnNewDocument")
                .param("identifier", identifier)
                .send();
    }

    /**
     * Expose a global function {@code window.<name>(payload)} in every execution
     * context. Each call from page script surfaces as a {@code Runtime.bindingCalled}
     * CDP event delivered to listeners registered via
     * {@link #addCdpEventListener(CdpEventListener)} — a one-way channel for page
     * code to push string payloads back to the driver.
     *
     * @param name the global function name to expose
     */
    public void addBinding(String name) {
        cdp.method("Runtime.addBinding")
                .param("name", name)
                .send();
    }

    /**
     * Remove a binding previously added with {@link #addBinding(String)}.
     *
     * @param name the global function name to remove
     */
    public void removeBinding(String name) {
        cdp.method("Runtime.removeBinding")
                .param("name", name)
                .send();
    }

    /**
     * Extract the install identifier from a {@code Page.addScriptToEvaluateOnNewDocument}
     * response, or {@code null} if the command returned no result (e.g. an error).
     */
    static String scriptIdentifier(CdpResponse response) {
        return response == null ? null : response.getResultAsString("identifier");
    }

    /**
     * Register a named JavaScript module to run in every new document and inject it into the
     * document that is already open. Modules are installed after the built-in page runtime and
     * after any modules named in {@code deps}, so a module can rely on the shared {@code window}
     * utilities the runtime and its dependencies provide without managing injection order itself.
     * The built-in runtime is installed (once) on the first registration; with no modules
     * registered it stays injected lazily on demand, so the default page footprint is unchanged.
     * <p>
     * Idempotent by {@code name} — a second call for an already-registered name is a no-op (so a
     * module may carry its own re-entry guard and be safely re-evaluated on each navigation).
     * Pair with {@link #removeInitScript(String)}.
     *
     * @param name   a stable identifier for the module
     * @param source the JavaScript source
     * @param deps   names of already-registered modules that must run before this one
     * @throws DriverException if a declared dependency has not been registered
     */
    public void addInitScript(String name, String source, String... deps) {
        synchronized (initScriptLock) {
            if (initScripts.containsKey(name)) {
                return;
            }
            for (String dep : deps) {
                if (!initScripts.containsKey(dep)) {
                    throw new DriverException("init script '" + name + "' depends on unregistered '" + dep + "'");
                }
            }
            // install the built-in runtime first so every module can rely on its window utilities
            if (runtimeInitId == null) {
                runtimeInitId = addScriptToEvaluateOnNewDocument(DRIVER_JS);
            }
            String cdpId = addScriptToEvaluateOnNewDocument(source);
            initScripts.put(name, new InitScript(source, List.of(deps), cdpId));
            // new-document installs fire on the NEXT document — also inject into the current one
            try {
                ensureKjsRuntime();
                evalDirect(source);
            } catch (Exception e) {
                logger.debug("init script '{}' inject into current document deferred: {}", name, e.getMessage());
            }
        }
    }

    /**
     * Unregister a module added with {@link #addInitScript(String, String, String...)} so it no
     * longer installs on future navigations. The copy already running in the current document is
     * left in place. No-op if {@code name} is not registered.
     *
     * @param name the module identifier passed to {@code addInitScript}
     */
    public void removeInitScript(String name) {
        synchronized (initScriptLock) {
            InitScript script = initScripts.remove(name);
            if (script != null && script.cdpId() != null) {
                removeScriptToEvaluateOnNewDocument(script.cdpId());
            }
        }
    }

    // ========== Frame ownership (DOM) ==========

    /**
     * The {@code backendNodeId} of the {@code <iframe>}/{@code <frame>} element that owns the given
     * frame ({@code DOM.getFrameOwner}). The owner element always lives in the <i>parent</i> document
     * — only its <i>content</i> may be cross-origin — so this resolves even for an out-of-process
     * (OOPIF) child frame, which is what lets the caller name a within-parent selector for a frame it
     * cannot reach from inside. Enables the DOM domain lazily (idempotent). Returns {@code null} for the
     * top-level frame (no owner) or on any error.
     *
     * @param frameId the CDP frame id (e.g. correlated from {@code Runtime.executionContextCreated})
     * @return the owner element's backend node id, or {@code null}
     */
    public Integer getFrameOwnerBackendNodeId(String frameId) {
        if (frameId == null) {
            return null;
        }
        cdp.method("DOM.enable").send();
        CdpResponse response = cdp.method("DOM.getFrameOwner")
                .param("frameId", frameId)
                .send();
        return response == null || response.isError() ? null : response.getResultAsInt("backendNodeId");
    }

    /**
     * Describe a node by backend node id ({@code DOM.describeNode}) → the node descriptor map
     * ({@code {nodeName, attributes:[name,val,…], …}}), or {@code null} on error. Pairs with
     * {@link #getFrameOwnerBackendNodeId(String)} to derive an owner-{@code <iframe>} selector.
     */
    public Map<String, Object> describeNode(int backendNodeId) {
        CdpResponse response = cdp.method("DOM.describeNode")
                .param("backendNodeId", backendNodeId)
                .send();
        return response == null || response.isError() ? null : response.getResult("node");
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
        // Seed the committed-document tracker so the first setUrl's readyState
        // fallback can already tell the pre-existing document from the requested one.
        committedLoaderId = frameResponse.getResultAsString("frameTree.frame.loaderId");
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

        // Record the main page's session id BEFORE enabling domains so the Page.*
        // session filter has a reference value for the first wave of events.
        this.pageSessionId = cdp.getSessionId();

        // NOW enable domains - events will be properly captured
        cdp.method("Page.enable").send();
        cdp.method("Runtime.enable").send();
        cdp.method("Network.enable").send(); // Required for cookie operations

        // Tell Chrome to auto-attach to isolated cross-origin iframes (OOPIFs)
        cdp.method("Target.setAutoAttach")
                .param("autoAttach", true)
                .param("waitForDebuggerOnStart", false)
                .param("flatten", true)
                .send();

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

        // Block until the main frame execution context is live (completed by the
        // Runtime.executionContextCreated that Runtime.enable above triggers). This is
        // the readiness gate that prevents transient context errors on first script().
        awaitMainContext(options.getTimeoutDuration().toMillis());
    }

    /**
     * Wake the waitForPageLoad() loop so it re-evaluates its completion conditions
     * now instead of on its next capped wait. Rotate BEFORE completing: a waiter
     * snapshots the latch before checking conditions, so a signal landing between
     * its check and its wait completes the snapshot it holds — never lost. Called
     * only from the single-threaded CDP event dispatcher (no rotation race). Only
     * complete() is ever invoked — no work is chained on the future, so nothing
     * executes on the event-dispatch thread.
     */
    private void nudgeLoadWaiter() {
        CompletableFuture<Void> tick = loadTick;
        loadTick = new CompletableFuture<>();
        tick.complete(null);
    }

    /**
     * Record that the main frame's default execution context is live (driven by
     * Runtime.executionContextCreated). Completes the readiness future so any thread
     * blocked in {@link #awaitMainContext} proceeds immediately.
     */
    private void completeMainContext(int contextId) {
        // Kept in frameContexts for diagnostics/consistency; the main-frame READ path is
        // the readiness future below, not this map entry.
        frameContexts.put(mainFrameId, contextId);
        CompletableFuture<Integer> f = mainContextReady;
        if (f.isDone()) {
            // A new context for an already-ready frame (re-enable / same-frame replace).
            mainContextReady = CompletableFuture.completedFuture(contextId);
        } else {
            f.complete(contextId);
        }
        // The verifyJsExecution() gate inside waitForPageLoad may have been the last
        // thing holding the wait open — wake it to re-probe now.
        nudgeLoadWaiter();
    }

    /**
     * Invalidate the main frame's execution context (navigation / tab switch tore it
     * down). Installs a fresh, incomplete future so the next {@link #awaitMainContext}
     * blocks until the replacement context arrives, instead of handing out a dead id.
     * <p>
     * CONCURRENCY INVARIANT: this is called from the CDP event-dispatch thread
     * (executionContextsCleared) and from the scenario thread (activateTarget). The
     * scenario-thread caller (activateTarget) ALWAYS issues Runtime.enable immediately
     * after, which guarantees a fresh executionContextCreated that completes the new
     * future. Do NOT add a scenario-thread invalidate WITHOUT a following Runtime.enable
     * (or other guaranteed context creation) or the future will strand incomplete and
     * every later main-frame eval will wait out the bound before falling back.
     */
    private void invalidateMainContext() {
        if (mainFrameId != null) {
            frameContexts.remove(mainFrameId);
        }
        if (mainContextReady.isDone()) {
            mainContextReady = new CompletableFuture<>();
        }
        // if already incomplete, a replacement context is already being awaited
    }

    /**
     * Block until the main frame's default execution context is live, returning its
     * id, or {@code null} if it does not become ready within {@code timeoutMs} (the
     * caller then falls back to CDP's default context). When the context is already
     * live this returns immediately. This is the replacement for the old poll loop:
     * it waits exactly as long as needed (the executionContextCreated event), never a
     * fixed 1s that the event can miss under load.
     */
    private Integer awaitMainContext(long timeoutMs) {
        try {
            return mainContextReady.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Record that a non-main frame's default execution context is live, completing
     * (or, for a same-frame replacement context, re-seeding) its readiness future.
     * Mirrors {@link #completeMainContext} for iframe frames.
     */
    private void completeFrameContext(String frameId, int contextId) {
        CompletableFuture<Integer> f = frameContextReady.computeIfAbsent(frameId, k -> new CompletableFuture<>());
        if (f.isDone()) {
            frameContextReady.put(frameId, CompletableFuture.completedFuture(contextId));
        } else {
            f.complete(contextId);
        }
    }

    /**
     * A frame is gone (detached from the DOM, or its OOPIF session detached) —
     * drop its readiness entry, completing any in-flight future with {@code null}
     * so a waiter fails fast to its fallback instead of sleeping out the bound.
     */
    private void releaseFrameContextWaiters(String frameId) {
        CompletableFuture<Integer> f = frameContextReady.remove(frameId);
        if (f != null && !f.isDone()) {
            f.complete(null);
        }
    }

    /**
     * Block until a non-main frame's default execution context arrives via
     * Runtime.executionContextCreated, returning its id or {@code null} on timeout
     * or frame detach. The caller falls back to an isolated world on null.
     */
    private Integer awaitFrameContext(String frameId, long timeoutMs) {
        CompletableFuture<Integer> f = frameContextReady.computeIfAbsent(frameId, k -> new CompletableFuture<>());
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return null;
        }
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
        //
        // NOTE on session filtering: with OOPIF support, Page.enable is called on every isolated
        // iframe session, so those sessions also stream Page.* events into this client. Without
        // a sessionId filter, an OOPIF's DOMContentLoaded would flip the parent's domContentEventFired
        // prematurely, and an OOPIF's frameStartedLoading would leak its frameId into
        // framesStillLoading. Every Page.* handler that mutates parent state must reject events
        // whose sessionId is not the current page session.
        cdp.on("Page.lifecycleEvent", event -> {
            if (isFromOtherSession(event)) return;
            String name = event.get("name");
            String frameId = event.get("frameId");
            logger.trace("lifecycleEvent: name={}, frameId={}", name, frameId);
            // DOMContentLoaded on main frame signals DOM is ready
            if ("DOMContentLoaded".equals(name) && mainFrameId.equals(frameId)) {
                domContentLoaderId = event.get("loaderId");
                domContentEventFired = true;
                nudgeLoadWaiter();
                logger.trace("DOMContentLoaded on main frame (via lifecycleEvent)");
            }
        });

        // Fallback: also listen to Page.domContentEventFired for compatibility
        cdp.on("Page.domContentEventFired", event -> {
            if (isFromOtherSession(event)) return;
            domContentEventFired = true;
            nudgeLoadWaiter();
            logger.trace("domContentEventFired (fallback)");
        });

        cdp.on("Page.frameStartedLoading", event -> {
            if (isFromOtherSession(event)) return;
            String frameId = event.get("frameId");
            if (frameId != null && frameId.equals(mainFrameId)) {
                domContentEventFired = false;
                // A new main-frame load supersedes the previous document's
                // DOMContentLoaded (Chrome emits frameStartedLoading strictly before
                // the same loader's DOMContentLoaded, so this never erases the signal
                // of the load that just started).
                domContentLoaderId = null;
                framesStillLoading.clear();
                logger.trace("frameStartedLoading: {} (main frame, reset state)", frameId);
            } else if (frameId != null) {
                framesStillLoading.add(frameId);
                logger.trace("frameStartedLoading: {} (child frame)", frameId);
            }
        });

        cdp.on("Page.frameStoppedLoading", event -> {
            if (isFromOtherSession(event)) return;
            String frameId = event.get("frameId");
            if (frameId != null) {
                framesStillLoading.remove(frameId);
                nudgeLoadWaiter(); // frames barrier may just have emptied
            }
            logger.trace("frameStoppedLoading: {}, remaining: {}", frameId, framesStillLoading);
        });

        // Track which document is actually committed in the main frame. This is what
        // lets the document.readyState fallback in waitForPageLoad distinguish "the
        // requested document is showing" from "the previous document still reports
        // readyState=complete" — without it the fallback is blind to WHICH document
        // it is reading.
        cdp.on("Page.frameNavigated", event -> {
            if (isFromOtherSession(event)) return;
            String frameId = event.get("frame.id");
            if (frameId != null && frameId.equals(mainFrameId)) {
                committedLoaderId = event.get("frame.loaderId");
                nudgeLoadWaiter(); // commit unblocks the readyState-fallback gate
                logger.trace("frameNavigated (main frame): loaderId={}, url={}",
                        committedLoaderId, (String) event.get("frame.url"));
            }
        });

        // CRITICAL: Handle frame detachment to prevent stale entries in framesStillLoading
        // If a child frame is detached (removed from DOM) before frameStoppedLoading fires,
        // the frame ID remains in framesStillLoading forever, causing a page load timeout.
        // This was observed as a flaky failure in CI where framesStillLoading was non-empty
        // despite domContentEventFired being true.
        cdp.on("Page.frameDetached", event -> {
            if (isFromOtherSession(event)) return;
            String frameId = event.get("frameId");
            if (frameId != null) {
                framesStillLoading.remove(frameId);
                frameContexts.remove(frameId);
                releaseFrameContextWaiters(frameId);
                nudgeLoadWaiter(); // frames barrier may just have emptied
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
                    // A handler that calls accept()/dismiss() can lose a race with the
                    // dialog already being gone — auto-dismissed, or resolved by another
                    // event on the cdp-event thread — for which CDP returns "No dialog is
                    // showing" (-32602). That is benign (the dialog IS resolved, just not
                    // by this call), so don't surface it at ERROR; only genuine handler
                    // failures should be loud.
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("No dialog is showing")) {
                        logger.debug("dialog handler: dialog already resolved (benign race): {}", msg);
                    } else {
                        logger.error("dialog handler error: {}", msg);
                    }
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
            if (dialogHandler == null) {
                // A beforeunload prompt ("Leave page?") with no handler registered is
                // always auto-confirmed: it only ever fires on an unload/navigation, so
                // "proceed" is the only sane default. We must NOT gate this on
                // pendingNavigationUrl — that flag is cleared as soon as setUrl returns,
                // and for data:/about: navigations setUrl returns synchronously (no
                // page-load wait), so the gate can already be null by the time the
                // (async) dialog event lands on the cdp-event thread. A missed accept
                // leaves the dialog OPEN, and an open beforeunload blocks every later
                // Runtime.evaluate on this driver — which, on a pooled driver, then
                // poisons the next scenario ("dialog is blocking Runtime.evaluate").
                if ("beforeunload".equals(type)) {
                    logger.debug("auto-accepting beforeunload (no handler), pendingNavigation={}", pendingNavigationUrl);
                    try {
                        dialog.accept();
                    } catch (Exception e) {
                        logger.trace("beforeunload accept failed (likely already handled): {}", e.getMessage());
                    }
                    currentDialog = null;
                    currentDialogText = null;
                } else {
                    // No handler registered - cancel pending Runtime.evaluate calls so they
                    // fail fast instead of waiting for the 30-second CDP timeout
                    cdp.cancelPendingEvaluations();
                }
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
            // Filter by session: only process events from the current page session.
            //
            // OOPIF context ids are deliberately NOT stored: they are scoped to the OOPIF's
            // own CDP session, and routing Runtime.evaluate to the right session (we set
            // cdp.sessionId on switchFrame) makes the session's *default* context the OOPIF
            // main world — no explicit contextId needed. See getFrameContext() /
            // ensureFrameContext() for the OOPIF short-circuit.
            String eventSession = event.getSessionId();
            String currentSession = cdp.getSessionId();
            if (eventSession != null && !eventSession.equals(currentSession)) {
                logger.trace("ignoring executionContextCreated from other session: {}", eventSession);
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
                        if (frameId.equals(mainFrameId)) {
                            // Authoritative readiness signal for the main frame.
                            completeMainContext(contextId.intValue());
                        } else {
                            // Map first, then future: an awaitFrameContext return
                            // implies the frameContexts entry is already readable.
                            frameContexts.put(frameId, contextId.intValue());
                            completeFrameContext(frameId, contextId.intValue());
                        }
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
            // Main context is gone until the new document creates one - flip the
            // readiness future to incomplete so callers await the replacement.
            invalidateMainContext();
            // Same for iframe frames: replace COMPLETED futures with fresh incomplete
            // ones so the next waiter blocks for the replacement context. In-flight
            // futures stay put — the replacement executionContextCreated will complete
            // them; swapping them out here would strand their waiters.
            frameContextReady.replaceAll((id, f) -> f.isDone() ? new CompletableFuture<>() : f);
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
            CompletableFuture<Void> removal = pendingTargetRemovals.remove(targetId);
            if (removal != null) {
                removal.complete(null);
            }
        });

        // Catch cross-origin iframes as they attach. Runtime.enable on the OOPIF session
        // wires up its default execution context so Runtime.evaluate works against the
        // iframe's main world once cdp.sessionId is routed there by switchFrame.
        //
        // We deliberately do NOT call Page.enable: that would have the OOPIF session stream
        // Page.* lifecycle events into this client and they would have to be session-filtered
        // (or worse, leak into parent page-load tracking). The defensive isFromOtherSession
        // check in the Page.* handlers is a belt-and-suspenders for the same reason.
        cdp.on("Target.attachedToTarget", event -> {
            String attachedSessionId = event.get("sessionId");
            Map<String, Object> targetInfo = event.get("targetInfo");

            if (targetInfo != null && "iframe".equals(targetInfo.get("type"))) {
                String targetId = (String) targetInfo.get("targetId"); // targetId = frameId
                oopifTargets.put(targetId, targetInfo);
                oopifSessions.put(targetId, attachedSessionId);
                logger.debug("Attached to isolated iframe (OOPIF): {} session={}",
                        targetInfo.get("url"), attachedSessionId);
                // Fire-and-forget: the response is unused, and this handler runs on
                // the serialized CDP event-dispatch thread — a blocking send here
                // would stall every queued event for a round-trip per OOPIF.
                cdp.method("Runtime.enable").sessionId(attachedSessionId).sendWithoutWaiting();
            }
        });

        // Remove tracked sessions when detached. If the OOPIF that detached was the
        // currently-active session (user was inside the frame when it navigated away
        // or was removed from the DOM), revert to the main page session so subsequent
        // CDP commands don't hit a dead session.
        cdp.on("Target.detachedFromTarget", event -> {
            String detachedSessionId = event.get("sessionId");
            if (detachedSessionId == null) return;
            String detachedFrameId = null;
            for (Map.Entry<String, String> e : oopifSessions.entrySet()) {
                if (detachedSessionId.equals(e.getValue())) {
                    detachedFrameId = e.getKey();
                    break;
                }
            }
            if (detachedFrameId != null) {
                oopifSessions.remove(detachedFrameId);
                oopifTargets.remove(detachedFrameId);
                frameContexts.remove(detachedFrameId);
                releaseFrameContextWaiters(detachedFrameId);
                logger.debug("OOPIF detached: frameId={}, sessionId={}", detachedFrameId, detachedSessionId);
            }
            if (detachedSessionId.equals(cdp.getSessionId()) && pageSessionId != null) {
                cdp.setSessionId(pageSessionId);
                currentFrame = null;
                logger.debug("active OOPIF session detached, reverted to main page session");
            }
        });

        // Request interception
        cdp.on("Fetch.requestPaused", this::onRequestPaused);
    }

    /**
     * Returns true when an event's sessionId belongs to a session other than the
     * current page session — typically an OOPIF session. Page.* handlers that
     * mutate parent-page state (domContentEventFired, framesStillLoading, etc.)
     * must drop such events; otherwise OOPIF load events leak into parent tracking.
     */
    private boolean isFromOtherSession(CdpEvent event) {
        String eventSession = event.getSessionId();
        if (eventSession == null) return false; // browser-level event
        String currentSession = cdp.getSessionId();
        if (eventSession.equals(currentSession)) return false;
        // The current session may be an OOPIF session (we're switched into a frame).
        // Compare to pageSessionId so an OOPIF event still counts as "other" while
        // we're inside that very frame — its events still target frameId != mainFrameId
        // and would not flip mainFrame-gated state anyway, but reject defensively.
        return !eventSession.equals(pageSessionId);
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

        // Snapshot the currently-committed loader BEFORE navigating so the readyState
        // fallback can tell a freshly-committed document from the one showing now — even
        // when Chrome commits this navigation under a loaderId other than the one
        // Page.navigate returns (see preNavCommittedLoaderId / checkDocumentReadyState).
        preNavCommittedLoaderId = committedLoaderId;

        // Navigate, retrying on two transient conditions that a fresh send resolves:
        //  (1) a CDP timeout on the Page.navigate command itself (busy/stateful page);
        //  (2) an ERR_ABORTED response for a URL that SHOULD load. Chrome returns
        //      ERR_ABORTED both for a deliberate download / 204-205 / window.stop()
        //      (the current document is retained on purpose) AND for a legitimate
        //      top-level navigation that merely lost a race — e.g. against the
        //      pooled-reset about:blank still settling under 2-vCPU load. The latter
        //      was seen in CI as /wait, /input, /shadow-dom aborting, after which the
        //      scenario ran against the STALE reset document and every step failed
        //      (element-not-found, waitUntil timeouts, value mismatches). A deliberate
        //      abort re-aborts on every attempt, so after the bounded retries we accept
        //      the retention (history.feature's 204 test still passes); a spurious
        //      abort commits on a retry and the scenario gets the document it asked for.
        // (A beforeunload prompt is auto-accepted in the dialog handler above, so the
        // timeout retry is for genuine transient timeouts, not the leave-page case.)
        int navAttempts = 3;
        CdpResponse navResponse = null;
        boolean aborted = false;
        for (int attempt = 0; attempt < navAttempts; attempt++) {
            try {
                navResponse = cdp.method("Page.navigate")
                        .param("url", url)
                        .send();
            } catch (RuntimeException e) {
                boolean transientNavTimeout = e.getMessage() != null
                        && e.getMessage().contains("CDP timeout for: Page.navigate");
                if (transientNavTimeout && attempt < navAttempts - 1) {
                    logger.warn("Page.navigate timed out, retrying ({}/{}): {}", attempt + 1, navAttempts - 1, url);
                    continue;
                }
                throw e;
            }
            aborted = "net::ERR_ABORTED".equals(navResponse.getResultAsString("errorText"));
            if (!aborted) {
                break; // committed, an error page under the same loader, or a normal response
            }
            if (attempt < navAttempts - 1) {
                logger.warn("Page.navigate returned ERR_ABORTED, retrying ({}/{}): {}", attempt + 1, navAttempts - 1, url);
                sleep(150); // let an in-flight/racing navigation (e.g. pooled-reset about:blank) settle
            }
        }

        // Every attempt aborted — a deliberate download / 204-205 / window.stop() that
        // retains the current document. The returned loader never commits, never fires
        // DOMContentLoaded, and a loader-bound wait for it could only end in a timeout,
        // so return with the page as-is (genuine load failures instead commit an error
        // page under the SAME loader, and the normal wait below handles those).
        if (aborted) {
            logger.warn("navigation aborted by browser, current document retained: {}", url);
            pendingNavigationUrl = null;
            return;
        }

        // Skip page load wait for data: and about: URLs - they load synchronously
        // and don't fire normal lifecycle events
        if (url.startsWith("data:") || url.startsWith("about:")) {
            pendingNavigationUrl = null;
            return;
        }

        // Wait for THIS navigation's document, identified by the loaderId that
        // Page.navigate returned — not for whichever document happens to fire load
        // signals next. On a pooled driver the reset's about:blank navigation (which
        // setUrl does not wait on, see above) is often still in flight here; without
        // the loader binding its DOMContentLoaded would satisfy this wait and the
        // scenario would start against the wrong document. Null for same-document
        // navigations (e.g. '#fragment'), where waitForPageLoad falls back to the
        // unbound legacy behavior.
        expectedLoaderId = navResponse.getResultAsString("loaderId");

        // Wait for page load based on strategy
        try {
            waitForPageLoad(options.getPageLoadStrategy());
        } finally {
            pendingNavigationUrl = null;
            expectedLoaderId = null;
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
        long lastStaleCheck = 0;

        while (true) {
            // Snapshot the wake-up latch BEFORE evaluating conditions: an event
            // landing between the check below and the wait completes THIS snapshot,
            // so the signal cannot be lost (see nudgeLoadWaiter).
            CompletableFuture<Void> tick = loadTick;

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

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0 || Thread.currentThread().isInterrupted()) {
                break;
            }
            // Event-driven wait with a CAP, not a pure future wait: the conditions
            // above include polling fallbacks (document.readyState, pruneStaleFrames)
            // that exist precisely because load events get lost under CI load — a
            // capped wait guarantees they still run with no event traffic at all.
            try {
                tick.get(Math.min(remaining, 500), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // timeout (or exceptional completion): fall through and re-check
            }
        }

        // Build diagnostic message with comprehensive state info
        String url = pendingNavigationUrl != null ? pendingNavigationUrl : "(unknown)";
        String readyStateInfo = getReadyStateForDiagnostic();
        String jsExecInfo = getJsExecStateForDiagnostic();
        String diagnostic = String.format(
                "page load timeout after %dms - url: %s, strategy: %s, " +
                "domContentEventFired: %s, framesStillLoading: %s, mainFrameId: %s, " +
                "expectedLoaderId: %s, supersededLoaderId: %s, committedLoaderId: %s, domContentLoaderId: %s, " +
                "readyState: %s, jsExec: %s, cdpOpen: %s",
                timeout.toMillis(), url, strategy,
                domContentEventFired, framesStillLoading, mainFrameId,
                expectedLoaderId, supersededLoaderId, committedLoaderId, domContentLoaderId,
                readyStateInfo, jsExecInfo, cdp.isOpen());
        logger.warn(diagnostic);
        throw new RuntimeException(diagnostic);
    }

    /**
     * Verify that JS execution works in the context script() will actually use.
     * <p>
     * The page is only truly "loaded" once the main frame's default execution context
     * is live - that is the single thing the readiness future tracks. We await it
     * briefly (it is usually already complete; during a navigation swap it settles in
     * milliseconds) and probe THAT context.
     * <p>
     * Crucially, an <i>error</i> from the explicit-context probe is NOT taken as "JS not
     * ready": {@link #mainContextReady} can hand out a contextId that a loader
     * replacement has already torn down when the matching executionContextsCleared was
     * never delivered (routine under CI load). The stale id then errors forever and, on
     * a fully-loaded page, wedges waitForPageLoad() to a 30s timeout — observed in CI as
     * "page load complete but JS context not ready yet" while the timeout diagnostic
     * reports {@code jsExec: ok} (it probes the default context). So on an explicit-
     * context error we re-probe the default context: this is a liveness check
     * ({@code typeof document}), not a document-identity check — identity is already
     * established by isPageLoadComplete (loaderId / readyState + URL) — so a live default
     * context is a valid confirmation that the renderer can run script. A stale readiness
     * id must not be able to veto a page that is demonstrably executing JS.
     */
    private boolean verifyJsExecution() {
        Integer contextId = awaitMainContext(1000);
        JsProbe probe = probeJsAlive(contextId);
        if (probe == JsProbe.ERROR && contextId != null) {
            // explicit context is dead/missing — fall back to the default context
            probe = probeJsAlive(null);
        }
        return probe == JsProbe.ALIVE;
    }

    private enum JsProbe { ALIVE, DEAD, ERROR }

    /**
     * Probe whether script runs in the given context ({@code null} = CDP default context).
     * ALIVE = ran and saw a document; DEAD = ran but returned false; ERROR = the context
     * was missing/torn down or the send failed (the caller may retry the default context).
     */
    private JsProbe probeJsAlive(Integer contextId) {
        try {
            CdpMessage probe = cdp.method("Runtime.evaluate")
                    .param("expression", "typeof document !== 'undefined'")
                    .param("returnByValue", true);
            if (contextId != null) {
                probe.param("contextId", contextId);
            }
            CdpResponse response = probe.send();
            if (response.isError()) {
                return JsProbe.ERROR;
            }
            return Boolean.TRUE.equals(response.getResult("result.value")) ? JsProbe.ALIVE : JsProbe.DEAD;
        } catch (Exception e) {
            logger.debug("JS execution probe failed (contextId={}): {}", contextId, e.getMessage());
            return JsProbe.ERROR;
        }
    }

    private boolean isPageLoadComplete(PageLoadStrategy strategy) {
        // First check if event was received
        boolean domReady = isDomReady();

        // Fallback: if event wasn't received, check document.readyState directly
        // This handles cases where the event fired before our handler was registered
        if (!domReady) {
            domReady = checkDocumentReadyState();
            if (domReady) {
                // Expected, benign recovery: the DOMContentLoaded lifecycle EVENT lagged
                // the document's real readyState (routine under CI renderer load), so the
                // direct readyState probe completed the wait first. This is a success, not
                // a fault — kept at DEBUG so it doesn't bury genuine warnings (a real
                // failure surfaces as the page-load-timeout diagnostic, logged at WARN).
                logger.debug("page-load wait completed via document.readyState fallback (DOMContentLoaded event lagged)");
                // Update flag + loader so we don't keep checking. Tagging the committed
                // loader is safe: checkDocumentReadyState only returned true after
                // verifying the committed document satisfies the wait (exact match for
                // setUrl, superseded for refresh/back/forward, anything when unbound).
                // Known TOCTOU: a client-side redirect committing between that check
                // and this tag mis-tags the redirect's loader — the worst outcome is a
                // LOUD page-load timeout (loader fields in the diagnostic expose it),
                // never a silent wrong-document success, because isDomReady requires
                // an exact match and the gate re-checks the commit on every poll.
                domContentLoaderId = committedLoaderId;
                domContentEventFired = true;
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
     * The DOMContentLoaded signal for the document the caller is actually waiting on.
     * When a navigation owns the wait (expectedLoaderId set by setUrl), the signal
     * only counts if it was recorded for that navigation's loader — a stale event
     * from the previous document (or a pool-reset about:blank) no longer qualifies.
     * With no owning navigation (refresh/reload/back/forward, external callers) the
     * legacy boolean applies unchanged.
     */
    private boolean isDomReady() {
        String expected = expectedLoaderId;
        if (expected != null) {
            if (expected.equals(domContentLoaderId)) {
                return true;
            }
            // Chrome can commit the requested navigation under a loaderId OTHER than the
            // one Page.navigate returned (it restarts/replaces the navigation internally).
            // The exact-loader match then never lands and the wait times out on a fully
            // loaded page (seen in CI: expectedLoaderId != committedLoaderId with
            // readyState complete and the requested URL live). Accept the replacement
            // purely from event state — no eval, no URL round-trip: the NEWLY-committed
            // main document (its loader advanced past the one showing when setUrl()
            // snapshotted preNavCommittedLoaderId) has fired its OWN DOMContentLoaded
            // (domContentLoaderId == committedLoaderId). A stale document (pooled-reset
            // about:blank, the previous page) commits no loader newer than the snapshot,
            // so it is still rejected and the anti-stale guarantee holds.
            String committed = committedLoaderId;
            return committed != null
                    && committed.equals(domContentLoaderId)
                    && !committed.equals(preNavCommittedLoaderId);
        }
        String superseded = supersededLoaderId;
        if (superseded != null) {
            String domLoader = domContentLoaderId;
            return domLoader != null && !superseded.equals(domLoader);
        }
        return domContentEventFired;
    }

    /**
     * Check document.readyState directly as fallback for missed events.
     */
    private boolean checkDocumentReadyState() {
        // readyState is a property of whichever document is CURRENTLY showing. When a
        // specific navigation owns this wait, the old document's 'complete' must not
        // count — require the requested loader to have committed (Page.frameNavigated)
        // before trusting the read.
        String requireUrl = null;
        String expected = expectedLoaderId;
        if (expected != null && !expected.equals(committedLoaderId)) {
            // The loader that committed is not the one Page.navigate returned. Usually
            // that means the requested document has simply not committed yet, so reject.
            // But Chrome sometimes commits the requested navigation under a DIFFERENT
            // loaderId than it returned (it restarts/replaces the navigation) — seen in
            // CI as expected != committed with the requested URL fully loaded, which
            // would otherwise deadlock this gate (and isDomReady()) until the wait times
            // out on a ready page. Recover only when a NEW document has actually
            // committed (the committed loader advanced past the pre-navigation one) AND
            // it is the requested URL: a stale document (pooled-reset about:blank, the
            // previous scenario's page) has a different URL and is still rejected, so the
            // anti-stale guarantee the exact-loader match provides is preserved.
            String committed = committedLoaderId;
            if (committed == null || committed.equals(preNavCommittedLoaderId)) {
                return false; // no new document has committed yet — still the old page
            }
            requireUrl = pendingNavigationUrl;
            if (requireUrl == null) {
                return false;
            }
        }
        // Same gate for the superseded-loader wait (refresh/reload/back/forward):
        // until a DIFFERENT document commits, any 'complete' read is the old one.
        // (BFCache restores don't re-fire DOMContentLoaded, so this fallback is the
        // path that completes a back()/forward() wait on a cache-restored document.)
        String superseded = supersededLoaderId;
        if (superseded != null) {
            String committed = committedLoaderId;
            if (committed == null || superseded.equals(committed)) {
                // No new document has committed. For refresh()/reload() the old
                // document is still showing and its readyState must not count. A
                // back()/forward() traversal to a same-document history entry
                // (pushState/fragment) commits nothing at all, though — verify by URL
                // instead: location.href only becomes the target entry's URL once the
                // traversal has actually happened.
                requireUrl = historyTargetUrl;
                if (requireUrl == null) {
                    return false;
                }
            }
        }
        try {
            CdpResponse response = cdp.method("Runtime.evaluate")
                    .param("expression", "({r: document.readyState, u: window.location.href})")
                    .param("returnByValue", true)
                    .send();
            if (response.isError()) {
                // Log the error so we can diagnose CI failures
                logger.warn("readyState check CDP error: {}", response.getErrorMessage());
                return false;
            }
            String readyState = response.getResultAsString("result.value.r");
            logger.trace("readyState check returned: {}", readyState); // keep trace, this is success path
            boolean ready = "complete".equals(readyState) || "interactive".equals(readyState);
            if (ready && requireUrl != null) {
                ready = urlsEquivalent(requireUrl, response.getResultAsString("result.value.u"));
            }
            return ready;
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
     * Whether a requested navigation/history URL and the document's live
     * {@code location.href} name the same resource, tolerating a lone trailing-slash
     * difference (a request for {@code …/path} commonly settles as {@code …/path/}).
     * Exact otherwise — this gates accepting a readyState=complete read as the target
     * document, so it must not treat genuinely different URLs as equal.
     */
    static boolean urlsEquivalent(String requested, String actual) {
        if (requested == null || actual == null) {
            return false;
        }
        if (requested.equals(actual)) {
            return true;
        }
        String r = requested.endsWith("/") ? requested.substring(0, requested.length() - 1) : requested;
        String a = actual.endsWith("/") ? actual.substring(0, actual.length() - 1) : actual;
        return r.equals(a);
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
            // Guard on __kjs.resolve (the wildcard resolver this runtime owns), not merely __kjs:
            // a co-installed helper (e.g. karate-max's agent-look.js) may seed a PARTIAL window.__kjs
            // without resolve, and a bare existence check would then skip driver.js injection,
            // leaving "{tag}text" wildcard locators unresolvable. driver.js extends (never clobbers)
            // an existing __kjs, so re-injecting to add resolve is safe and idempotent.
            Boolean exists = (Boolean) evalDirect(
                    "typeof window.__kjs !== 'undefined' && typeof window.__kjs.resolve === 'function'");
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
        return cdpEval(expression, true);
    }

    /**
     * As {@link #cdpEval(String)}, but lets the caller choose the serialization mode.
     * {@code returnByValue=false} yields a CDP {@code RemoteObject} handle (its {@code objectId})
     * instead of the value — required when the result is a live DOM node that a CDP domain method
     * must address by reference (see {@link #objectId(String)}).
     */
    private CdpResponse cdpEval(String expression, boolean returnByValue) {
        // Fail fast if a dialog is already open and blocking. A pre-existing
        // unhandled dialog means any new Runtime.evaluate would be cancelled
        // by our Page.javascriptDialogOpening handler (or hang until the 30s
        // CDP timeout). Users should register onDialog() or call dialog(true|false)
        // before further script activity.
        if (dialogHandler == null && currentDialog != null && !currentDialog.isHandled()) {
            throw new DialogOpenedException("dialog is blocking Runtime.evaluate");
        }
        // A destroyed execution context (navigation tore it down) is re-created by Chrome
        // within tens of milliseconds, NOT the 500ms element-poll interval. Sleeping the
        // full retryInterval per transient error makes every post-navigation eval cost
        // ~500ms+, and with wildcard locators / waitUntil polling that snowballs into
        // spurious waitUntil/waitFor timeouts under parallel load. Poll fast instead, but
        // keep the SAME overall time budget (retryCount * retryInterval) so a genuinely
        // slow context swap on a loaded CI box still recovers.
        int transientInterval = fastPollInterval();
        int maxRetries = fastPollAttempts();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            CdpMessage message = cdp.method("Runtime.evaluate")
                    .param("expression", expression)
                    .param("returnByValue", returnByValue);

            // Use explicit context ID for reliable frame targeting (see getFrameContext notes)
            Integer contextId = getFrameContext();
            // We INTEND to run in the main frame but its execution context is not
            // confirmed live (currentFrame == null && getFrameContext() == null). This
            // is distinct from the OOPIF null (currentFrame != null) and the same-origin
            // child-frame null, both of which legitimately fall through to the default
            // context below.
            boolean mainContextUnresolved = currentFrame == null && contextId == null;
            if (mainContextUnresolved && attempt < maxRetries) {
                // Do NOT silently fall back to CDP's ambient default context here. Right
                // after a switchFrame(null), the ambient default can still resolve to the
                // child frame we just left, so the script runs in the wrong world and
                // returns a misleading null instead of the main-frame value - the source
                // of a rare CI flake (frame.feature "Multiple switches" reading
                // window.mainValue as null under a slow context swap). Self-heal instead:
                // give the replacement executionContextCreated more time via the retry
                // loop, which re-awaits the readiness future on the next getFrameContext().
                // Logged at WARN so a recurrence is visible in CI for the next investigation.
                logger.warn("main-frame context not ready, retry {}/{}: {}", attempt + 1, maxRetries, truncate(expression, 100));
                sleep(transientInterval);
                continue;
            }
            if (contextId != null) {
                message.param("contextId", contextId);
            } else if (mainContextUnresolved) {
                // Retries exhausted and the main context never registered - e.g. a
                // download / 204 / aborted navigation that clears the context without a
                // replacement. Degrade to CDP's default context as a last resort rather
                // than hard-failing, matching the documented graceful-degradation intent,
                // but log loudly so a recurrence stands out in CI logs.
                logger.warn("main-frame context never became ready after {} attempts, falling back to default context: {}", maxRetries, truncate(expression, 100));
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
                logger.debug("script opened a dialog, returning null: {}", truncate(expression, 100));
                return new CdpResponse(Map.of());
            }

            // Check for transient context errors that should be retried
            if (isTransientContextError(response)) {
                if (attempt < maxRetries) {
                    // Safety net only: the readiness future (getFrameContext awaits it)
                    // normally guarantees a live context here. A context can still die
                    // between the await and the send (e.g. a click triggered a navigation);
                    // the navigation's executionContextsCleared event invalidates the
                    // future, so the retry's getFrameContext() blocks for the replacement
                    // context on its own. We deliberately do NOT invalidate from here: a
                    // -32000 that is NOT a navigation would otherwise strand the future
                    // incomplete (no new executionContextCreated would ever arrive) and
                    // make every later eval wait out the full timeout.
                    logger.warn("transient context error, retry {}/{}: {}", attempt + 1, maxRetries, truncate(expression, 100));
                    sleep(transientInterval);
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
     * - When in main frame (currentFrame == null), we AWAIT the readiness future so we
     *   never hand out a stale or not-yet-registered id. When the context is already
     *   live this returns immediately; during the brief window after a navigation it
     *   blocks until the replacement executionContextCreated arrives. This replaced the
     *   old "return possibly-null/stale map value and let retry logic cope" approach,
     *   which was the source of the transient-context-error flakiness under load.
     * - Using CDP's default context for the main frame is unreliable right after a
     *   frame switch (switchFrame(null)), hence the explicit id.
     * - Returns null if the context is not ready within a SHORT bound. The bound is
     *   deliberately short (not the full page-load timeout): this is a per-eval hot path.
     *   For the MAIN frame, a null return does NOT mean "use CDP's default context" - the
     *   caller (cdpEval) treats main-frame-unresolved as a retriable condition and only
     *   degrades to the default context as a last resort after exhausting retries (a
     *   download / 204 / aborted navigation that clears the context without a
     *   replacement). Falling back to the default context eagerly was unsafe: right after
     *   a switchFrame(null) the default can still resolve to the child frame just left,
     *   silently running in the wrong world. The long, authoritative wait lives only in
     *   the explicit barriers (initialize / activateTarget / waitUntilReady).
     */
    private Integer getFrameContext() {
        if (currentFrame == null) {
            return awaitMainContext(CONTEXT_READY_POLL_MS);
        }
        // For OOPIFs, cdp.sessionId is routed to the iframe's own CDP session. Each session
        // has its own "default" execution context, which IS the OOPIF's main world. Passing
        // a contextId from frameContexts (registered against a different session) would yield
        // "Cannot find context with specified id". Returning null lets CDP pick the default.
        if (oopifSessions.containsKey(currentFrame.id)) {
            return null;
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
        return screenshot(embed, null);
    }

    /**
     * Take screenshot, optionally embed, with an optional CDP timeout override.
     * A null timeout uses the default CDP timeout (the regular, user-requested
     * path). The failure path passes a short bound — see {@link #failureScreenshot()}.
     */
    private byte[] screenshot(boolean embed, Duration timeout) {
        CdpMessage message = cdp.method("Page.captureScreenshot")
                .param("format", "png");
        if (timeout != null) {
            message.timeout(timeout);
        }
        CdpResponse response = message.send();

        String base64 = response.getResultAsString("data");
        byte[] bytes = Base64.getDecoder().decode(base64);

        if (embed) {
            LogContext ctx = LogContext.get();
            ctx.embed(bytes, "image/png", "screenshot.png");
        }

        return bytes;
    }

    /**
     * Best-effort failure-path screenshot, bounded by {@link #FAILURE_SCREENSHOT_TIMEOUT}
     * so a stalled renderer can't turn a swallowed diagnostic into a full-timeout hang.
     */
    @Override
    public byte[] failureScreenshot() {
        return screenshot(false, FAILURE_SCREENSHOT_TIMEOUT);
    }

    /**
     * Take a screenshot clipped to the bounding rect of the element matched by
     * the locator. Uses CDP's {@code Page.captureScreenshot} with a {@code clip}
     * param — matches v1 behavior.
     */
    @Override
    public byte[] screenshot(String locator, boolean embed) {
        Map<String, Object> pos = position(locator);
        if (pos == null) {
            // fall back to full-page when the element can't be located
            return screenshot(embed);
        }
        Map<String, Object> clip = new java.util.LinkedHashMap<>(pos);
        clip.put("scale", 1);
        CdpResponse response = cdp.method("Page.captureScreenshot")
                .param("format", "png")
                .param("clip", clip)
                .send();
        String base64 = response.getResultAsString("data");
        byte[] bytes = Base64.getDecoder().decode(base64);
        if (embed) {
            LogContext.get().embed(bytes, "image/png", "screenshot.png");
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
            cdp.setSessionId(pageSessionId); // Restore main session
            logger.debug("switched to main frame");
            return;
        }

        // If we're currently inside an OOPIF, the iframe element lives in the parent's
        // DOM — JS lookup has to run there, not in the current frame's session. Reset
        // to the main session for the lookup; we'll re-switch below if the matched
        // frame turns out to be another OOPIF.
        if (pageSessionId != null && !pageSessionId.equals(cdp.getSessionId())) {
            cdp.setSessionId(pageSessionId);
            currentFrame = null;
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

        // Find matching frame in tree
        String frameId = null;
        String url = null;
        String name = null;

        // 1. Try standard, same-origin frames first
        if (childFrames != null) {
            for (Map<String, Object> frameData : childFrames) {
                Map<String, Object> frame = (Map<String, Object>) frameData.get("frame");
                String fId = (String) frame.get("id");
                String fUrl = (String) frame.get("url");
                String fName = (String) frame.get("name");

                // A frame that has its own isolated CDP session is an OOPIF: its document is
                // out-of-process and is ONLY queryable via that session, never the main page
                // session. Page.getFrameTree still lists it here, so a name/URL match would set
                // cdp.sessionId to the main session and every subsequent command (readiness
                // poll, element lookup) would run against the parent document — element-not-found
                // and "timeout waiting for OOPIF readiness" with the wrong expectedUrl. Defer to
                // the OOPIF-aware matching below, which routes the session and validates the live
                // URL. Covers the case where Target.attachedToTarget has already fired.
                if (oopifSessions.containsKey(fId)) {
                    continue;
                }

                // Match by name if provided, otherwise by URL
                boolean matches = false;
                if (targetName != null && !targetName.isEmpty() && targetName.equals(fName)) {
                    // Name match alone is not enough when the element also carries a concrete src:
                    // during a same-origin -> cross-origin navigation the OOPIF may not have
                    // attached yet (so the guard above can't catch it), while this same-origin tree
                    // entry still reports the OLD document URL. Matching by name there would bind to
                    // the stale document on the main session. Require the committed URL to be
                    // consistent with the requested src; a mismatch means "still transitioning" —
                    // fall through to the OOPIF retry loop, which waits for the attach and matches
                    // the live URL. Same-origin frames driven via srcdoc have an empty src, so this
                    // guard is a no-op for them.
                    matches = targetSrc == null || targetSrc.isEmpty()
                            || (fUrl != null && fUrl.contains(targetSrc));
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
                    cdp.setSessionId(pageSessionId); // Ensure we are on the main session
                    break;
                }
            }
        }

        // 2. If not found, check isolated OOPIFs. Target.attachedToTarget is async — when
        // the test set `iframe.src` to a cross-origin URL just before calling switchFrame,
        // the OOPIF may not have attached yet. Retry briefly so callers don't need their
        // own sleep before switchFrame.
        if (frameId == null) {
            final String fName = targetName;
            final String fSrc = targetSrc;
            final String[] matched = new String[3]; // frameId, url, name
            retry("OOPIF matching locator " + locator, () -> {
                for (Map.Entry<String, String> entry : oopifSessions.entrySet()) {
                    String potentialFrameId = entry.getKey();
                    String sessionId = entry.getValue();
                    cdp.setSessionId(sessionId);
                    try {
                        CdpResponse evalName = cdp.method("Runtime.evaluate")
                                .param("expression", "window.name")
                                .param("returnByValue", true).send();
                        CdpResponse evalUrl = cdp.method("Runtime.evaluate")
                                .param("expression", "window.location.href")
                                .param("returnByValue", true).send();
                        String internalName = evalName.getResultAsString("result.value");
                        String internalUrl = evalUrl.getResultAsString("result.value");
                        // Strict matching: if BOTH name and src are specified on the iframe
                        // element, require BOTH to match this OOPIF. window.name persists across
                        // cross-process navigations of the same iframe, so a stale OOPIF entry
                        // (e.g., the same iframe before the test promoted it cross-origin) still
                        // has the matching window.name — name-only matching would happily pick it
                        // up and we'd query an outdated document. Requiring window.location.href
                        // to also contain the iframe's current src filters those out; the retry
                        // loop absorbs the brief window before Target.attachedToTarget fires for
                        // the new OOPIF.
                        boolean noCriteria = (fName == null || fName.isEmpty())
                                && (fSrc == null || fSrc.isEmpty());
                        boolean nameOk = fName == null || fName.isEmpty()
                                || fName.equals(internalName);
                        boolean srcOk = fSrc == null || fSrc.isEmpty()
                                || (internalUrl != null && internalUrl.contains(fSrc));
                        boolean matches = noCriteria || (nameOk && srcOk);
                        if (matches) {
                            matched[0] = potentialFrameId;
                            matched[1] = internalUrl;
                            matched[2] = internalName;
                            // Leave cdp.sessionId on the OOPIF so future commands route here.
                            return true;
                        }
                    } catch (Exception e) {
                        logger.trace("Failed to query OOPIF internal state: {}", e.getMessage());
                    }
                    cdp.setSessionId(pageSessionId);
                }
                return false;
            });
            frameId = matched[0];
            url = matched[1];
            name = matched[2];
        }

        if (frameId == null) {
            cdp.setSessionId(pageSessionId);
            StringBuilder diag = new StringBuilder("could not find frame for locator: ").append(locator);
            diag.append(" (targetName='").append(targetName).append("', targetSrc='").append(targetSrc).append("'");
            if (!oopifTargets.isEmpty()) {
                diag.append(", knownOopifs=[");
                boolean first = true;
                for (Map<String, Object> info : oopifTargets.values()) {
                    if (!first) diag.append(", ");
                    diag.append(info.get("url"));
                    first = false;
                }
                diag.append("]");
            }
            diag.append(")");
            throw new DriverException(diag.toString());
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
        // OOPIF: cdp.sessionId is routed to the iframe's own CDP session, whose default
        // execution context IS the OOPIF's main world. No registration is needed — and the
        // contextId-from-event path won't work here anyway since the id is scoped to a
        // different session than the one we route commands on. But we DO need to wait
        // for the OOPIF document to parse before returning: the switchFrame OOPIF match
        // succeeds as soon as window.location.href is set (at document creation, before
        // HTML parsing), so without this wait a caller that immediately queries DOM
        // races the parser. Same-origin frames get this via waitForFrameContextReady().
        if (oopifSessions.containsKey(frameId)) {
            // Route to the OOPIF's own session before polling/using it. switchFrame(String)
            // already leaves cdp.sessionId here after an OOPIF match, but route defensively so
            // any other caller (e.g. index-based switchFrame) is correct too — the readiness
            // poll and all subsequent commands MUST hit the OOPIF session, not the main page.
            cdp.setSessionId(oopifSessions.get(frameId));
            // Pass the matched URL through so the readiness poll can also assert that the
            // OOPIF's document.URL has actually become the expected one — guards against
            // a transient state where readyState briefly reports non-'loading' before the
            // cross-origin navigation has committed the new document in the new process.
            String expectedUrl = currentFrame != null ? currentFrame.url : null;
            waitForOopifReady(frameId, expectedUrl);
            return;
        }
        // First, wait for the main world context to arrive via Runtime.executionContextCreated —
        // event-completed future (awaitFrameContext), same pattern as the main frame's
        // mainContextReady, with the SAME 1s bound the old map-poll had so stranding can
        // never be worse than before.
        // IMPORTANT: Do NOT immediately fall back to Page.createIsolatedWorld - isolated worlds
        // are separate JS contexts where variables set by page scripts (e.g., window.frameValue)
        // are not visible. This caused flaky "Switch back to main frame" tests where
        // script('window.frameValue') returned null because it ran in an isolated world
        // instead of the iframe's main world.
        if (!frameContexts.containsKey(frameId)) {
            Integer contextId = awaitFrameContext(frameId, 1000);
            if (contextId != null) {
                logger.debug("frame context arrived via event: frameId={}", frameId);
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
                    // Complete the readiness future too, so a concurrent waiter on the
                    // same frame observes the isolated world instead of timing out.
                    completeFrameContext(frameId, contextId);
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
     * Wait for an OOPIF's document to leave the 'loading' state. cdp.sessionId is
     * already routed to the OOPIF session when this is called, so a contextId-less
     * Runtime.evaluate hits the OOPIF's default (main world) execution context.
     *
     * <p>When {@code expectedUrl} is non-empty, also require {@code document.URL} to
     * contain it. {@code window.location.href} updates as soon as a navigation is
     * committed in the browsing context — that's what {@code switchFrame} matches on
     * — but {@code document.URL} only updates when the new {@link org.w3c.dom.Document}
     * instance for that URL actually becomes current. Between those two points, a
     * {@code Runtime.evaluate} can momentarily see {@code readyState !== 'loading'}
     * against the prior/transient document (often "about:blank-ish") in the new
     * RenderFrameHost, which would let us return before the real document is queryable.
     * The {@code document.URL} match is the "the new doc is now real" signal.</p>
     */
    private void waitForOopifReady(String frameId, String expectedUrl) {
        boolean checkUrl = expectedUrl != null && !expectedUrl.isEmpty();
        String script = checkUrl
                ? "({r: document.readyState, u: document.URL})"
                : "document.readyState";
        boolean ready = pollUntil(2000, 50, () -> {
            try {
                CdpResponse response = cdp.method("Runtime.evaluate")
                        .param("expression", script)
                        .param("returnByValue", true)
                        .send();
                if (response.isError()) {
                    return false;
                }
                if (checkUrl) {
                    Map<String, Object> value = response.getResult("result.value");
                    if (value != null) {
                        Object r = value.get("r");
                        Object u = value.get("u");
                        if (r instanceof String && !"loading".equals(r)
                                && u instanceof String && ((String) u).contains(expectedUrl)) {
                            logger.trace("OOPIF ready: frameId={}, readyState={}, url={}", frameId, r, u);
                            return true;
                        }
                    }
                } else {
                    Object value = response.getResult("result.value");
                    if (value instanceof String && !"loading".equals(value)) {
                        logger.trace("OOPIF ready: frameId={}, readyState={}", frameId, value);
                        return true;
                    }
                }
            } catch (Exception e) {
                logger.trace("OOPIF readiness check failed (will retry): {}", e.getMessage());
            }
            return false;
        });
        if (!ready) {
            // Not fatal — element-level retry in retryIfNeeded() will absorb any remainder.
            logger.warn("timeout waiting for OOPIF readiness: frameId={}, expectedUrl={} (will retry on first use)",
                    frameId, expectedUrl);
        }
    }

    /**
     * Verify a (non-main) frame's execution context is actually ready for JS
     * execution. Context-id ARRIVAL is event-driven (see awaitFrameContext); this
     * probe is deliberately still a poll — "the id exists" does not mean "the
     * frame's document is ready to run script" (it may still be loading), and no
     * CDP event announces that transition for a contextId-addressed world.
     */
    private void waitForFrameContextReady(String frameId) {
        boolean ready = pollUntil(1000, 50, () -> {
            Integer contextId = frameContexts.get(frameId);
            if (contextId == null) {
                return false;
            }
            try {
                CdpResponse response = cdp.method("Runtime.evaluate")
                        .param("expression", "typeof document !== 'undefined'")
                        .param("returnByValue", true)
                        .param("contextId", contextId)
                        .send();
                if (!response.isError() && Boolean.TRUE.equals(response.getResult("result.value"))) {
                    logger.trace("frame context ready: frameId={}, contextId={}", frameId, contextId);
                    return true;
                }
            } catch (Exception e) {
                // Context not ready yet, will retry
                logger.trace("frame context not ready yet: {}", e.getMessage());
            }
            return false;
        });
        if (!ready) {
            // Timeout is not fatal - retry logic in script() will handle it
            logger.warn("timeout waiting for frame context: frameId={} (will retry on first use)", frameId);
        }
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

        // Unblock anyone awaiting readiness (e.g. a script() racing this quit) - once
        // the CDP connection is closed no executionContextCreated will ever complete it.
        if (!mainContextReady.isDone()) {
            mainContextReady.completeExceptionally(new DriverException("driver quit"));
        }

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

        // Register the removal future BEFORE sending closeTarget, then close.
        // Target.closeTarget returns before the target is fully removed, so callers
        // that enumerate tabs right after close() would still see the old one.
        CompletableFuture<Void> removed = new CompletableFuture<>();
        pendingTargetRemovals.put(targetToClose, removed);
        try {
            cdp.browserMethod("Target.closeTarget")
                    .param("targetId", targetToClose)
                    .send();
            logger.debug("close() - closed: {}", targetToClose);
            waitForTargetRemoved(targetToClose, removed);
        } finally {
            pendingTargetRemovals.remove(targetToClose);
        }
    }

    /**
     * Wait for a closed target to actually disappear, event-driven: the future is
     * completed by the Target.targetDestroyed handler. That event's delivery depends
     * on Target.setDiscoverTargets, which initialize() arms best-effort — so on
     * timeout, fall back to ONE page-list check before declaring a problem.
     */
    private void waitForTargetRemoved(String targetId, CompletableFuture<Void> removed) {
        try {
            removed.get(1000, TimeUnit.MILLISECONDS);
            logger.trace("target removal confirmed by event: {}", targetId);
            return;
        } catch (Exception e) {
            // fall through to the direct check
        }
        if (!getPages().contains(targetId)) {
            logger.trace("target removed from page list: {}", targetId);
            return;
        }
        logger.warn("timeout waiting for target to be removed: {}", targetId);
    }

    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public boolean isReady() {
        CompletableFuture<Integer> f = mainContextReady;
        return f.isDone() && !f.isCompletedExceptionally();
    }

    @Override
    public void waitUntilReady() {
        // The main frame execution context being live is the authoritative readiness
        // signal (see mainContextReady). Block on it up to the configured timeout;
        // returns immediately when already ready.
        awaitMainContext(options.getTimeoutDuration().toMillis());
    }

    /**
     * Bounded liveness probe for {@link PooledDriverProvider} reset.
     * <p>
     * Evaluates {@code new Promise(r => setTimeout(() => r(true), 0))} with
     * {@code awaitPromise} and a 3-second CDP timeout (vs the default 30s). Returns
     * false on any error, timeout, or closed socket — the caller treats false as
     * "discard this driver, create a fresh one for the next scenario."
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
     * <b>Why a timer, not {@code evaluate("1")}:</b> a CPU-starved renderer under a
     * slow CI container can evaluate synchronous JS instantly yet never fire a
     * {@code setTimeout} — the "timer never fires" degradation that starves
     * page-driven {@code waitUntil} conditions and lets a poisoned tab go back into
     * the pool. Gating on a {@code setTimeout(…, 0)} that must resolve through the
     * macrotask queue makes the probe fail for a stalled timer queue as well as a
     * wedged channel (a microtask like {@code Promise.resolve()} would not — it can
     * settle while timers are starved). {@code awaitPromise} blocks the CDP call
     * until the timer resolves, so a stuck renderer trips the bounded timeout. We
     * probe Runtime.evaluate rather than Page.navigate because it's cheap and local
     * (no network).
     * </p>
     */
    @Override
    public boolean isResponsive() {
        if (terminated || !cdp.isOpen()) {
            return false;
        }
        try {
            CdpResponse response = cdp.method("Runtime.evaluate")
                    .param("expression", "new Promise(function(r){ setTimeout(function(){ r(true); }, 0); })")
                    .param("awaitPromise", true)
                    .param("returnByValue", true)
                    .timeout(Duration.ofSeconds(3))
                    .send();
            return !response.isError() && Boolean.TRUE.equals(response.getResult("result.value"));
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
        elementAction(locator, Locators.clickJs(locator));
        return BaseElement.existing(this, locator);
    }

    /**
     * Focus an element.
     */
    public Element focus(String locator) {
        logger.debug("focus: {}", locator);
        elementAction(locator, Locators.focusJs(locator));
        return BaseElement.existing(this, locator);
    }

    /**
     * Clear an input element.
     */
    public Element clear(String locator) {
        logger.debug("clear: {}", locator);
        elementAction(locator, Locators.clearJs(locator));
        return BaseElement.existing(this, locator);
    }

    /**
     * Input text into an element using CDP keypresses.
     * For time/date inputs that don't respond to CDP keystrokes, uses JS value assignment.
     */
    public Element input(String locator, String value) {
        logger.debug("input: {} <- {}", locator, value);
        focus(locator);
        Boolean isDateTimeInput = (Boolean) script(locator,
            "_ && ['time','date','datetime-local','month','week'].indexOf(_.type) >= 0");
        if (Boolean.TRUE.equals(isDateTimeInput)) {
            elementAction(locator, Locators.inputJs(locator, value));
        } else {
            clear(locator);
            keys().type(value);
        }
        return BaseElement.existing(this, locator);
    }

    /**
     * Set the value of an input element.
     */
    public Element value(String locator, String value) {
        logger.debug("value: {} <- {}", locator, value);
        elementAction(locator, Locators.inputJs(locator, value));
        return BaseElement.existing(this, locator);
    }

    /**
     * Select an option from a dropdown by text or value.
     */
    public Element select(String locator, String text) {
        logger.debug("select: {} <- {}", locator, text);
        Object matched = elementAction(locator, Locators.optionSelector(locator, text));
        if (!Boolean.TRUE.equals(matched)) {
            throw new DriverException("select failed, no option matching: '" + text + "' for: " + locator);
        }
        return BaseElement.existing(this, locator);
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
                        " " + Locators.commitFieldEventsJs("e"));
        script(js);
        return BaseElement.existing(this, locator);
    }

    /**
     * Scroll an element into view.
     */
    public Element scroll(String locator) {
        logger.debug("scroll: {}", locator);
        retryIfNeeded(locator);
        script(Locators.scrollJs(locator));
        return BaseElement.existing(this, locator);
    }

    /**
     * Highlight an element.
     */
    public Element highlight(String locator) {
        logger.debug("highlight: {}", locator);
        retryIfNeeded(locator);
        script(Locators.highlight(locator, options.getHighlightDuration()));
        return BaseElement.existing(this, locator);
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
        return (Map<String, Object>) elementAction(locator, Locators.getPositionJs(locator));
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
     * Resolve a locator to the CDP {@code RemoteObject} id ({@code objectId}) of its DOM node, or
     * {@code null} if it does not resolve. {@link #script(String, Object)} returns a node BY VALUE
     * (no remote handle); a CDP domain method that addresses a node by reference — e.g.
     * {@code DOM.setFileInputFiles} for a file upload — needs the live {@code objectId}. This is the
     * one locator→CDP-node primitive those operations require. It runs in the resolved frame context
     * (same as {@link #script(String)}) and, unlike the click/input path, does NOT require the element
     * to be visible — a file {@code <input>} is routinely {@code display:none}. Does not poll/retry for
     * existence; the caller decides how to handle a {@code null}.
     */
    public String objectId(String locator) {
        String expression = Locators.wrapInFunctionInvoke("return " + Locators.selector(locator) + ";");
        if (expression.contains("__kjs")) {
            ensureKjsRuntime();
        }
        CdpResponse response = cdpEval(expression, false);
        return response == null || response.isError() ? null : response.getResultAsString("result.objectId");
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

    /**
     * Re-resolve attempts for an element that vanished between the existence check and
     * the action eval. Small on purpose: each attempt already carries a full
     * {@link #retryIfNeeded} poll, and a locator that keeps vanishing is a genuinely
     * unstable page, not a transient worth papering over.
     */
    private static final int ELEMENT_ACTION_ATTEMPTS = 3;

    /**
     * Wait for {@code locator} to exist, then run an action script that re-resolves it
     * inside the page, retrying if it vanished in between.
     * <p>
     * The locator is resolved twice — once here by {@link #retryIfNeeded} (existence)
     * and again by the action JS itself — so a document that swaps between the two evals
     * leaves the action resolving null. Observed in CI as input() reporting
     * "TypeError: Cannot read properties of null (reading 'focus')" against generated
     * source, on a page still settling under a slow container. The action JS now names
     * the locator (see {@link Locators#ELEMENT_NOT_FOUND}) so a transient miss can be
     * re-resolved rather than surfaced.
     * </p>
     * <p>
     * Only the element-not-found marker is retried. Any other page-side JS error
     * propagates untouched — a real bug in the page under test must not be retried into
     * silence.
     * </p>
     */
    private Object elementAction(String locator, String js) {
        RuntimeException lastError = null;
        for (int i = 0; i < ELEMENT_ACTION_ATTEMPTS; i++) {
            retryIfNeeded(locator);
            try {
                return script(js);
            } catch (RuntimeException e) {
                if (!isElementVanished(e)) {
                    throw e;
                }
                lastError = e;
                logger.warn("element vanished between existence check and action, re-resolving ({}/{}): {}",
                        i + 1, ELEMENT_ACTION_ATTEMPTS, locator);
                sleep(options.getRetryInterval());
            }
        }
        throw new DriverException("element vanished during action after " + ELEMENT_ACTION_ATTEMPTS
                + " re-resolve attempts: " + locator + " | " + getDriverState(), lastError);
    }

    private static boolean isElementVanished(RuntimeException e) {
        return e.getMessage() != null && e.getMessage().contains(Locators.ELEMENT_NOT_FOUND);
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
        Element found = pollFor(timeout.toMillis(), options.getRetryInterval(),
                () -> exists(locator) ? BaseElement.existing(this, locator) : null);
        if (found == null) {
            throw new DriverException("timeout waiting for element: " + locator);
        }
        return found;
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
        Element found = pollFor(timeout.toMillis(), options.getRetryInterval(), () -> {
            for (String locator : locators) {
                if (exists(locator)) {
                    return BaseElement.existing(this, locator);
                }
            }
            return null;
        });
        if (found == null) {
            throw new DriverException("timeout waiting for any element: " + String.join(", ", locators));
        }
        return found;
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
        Element found = pollFor(timeout.toMillis(), options.getRetryInterval(), () -> {
            if (exists(locator)) {
                String text = text(locator);
                if (text != null && text.contains(expected)) {
                    return BaseElement.existing(this, locator);
                }
            }
            return null;
        });
        if (found == null) {
            throw new DriverException("timeout waiting for text '" + expected + "' in element: " + locator);
        }
        return found;
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
        Element found = pollFor(timeout.toMillis(), options.getRetryInterval(),
                () -> exists(locator) && enabled(locator) ? BaseElement.existing(this, locator) : null);
        if (found == null) {
            throw new DriverException("timeout waiting for element to be enabled: " + locator);
        }
        return found;
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
        String found = pollFor(timeout.toMillis(), options.getRetryInterval(), () -> {
            String url = getUrl();
            return url != null && url.contains(expected) ? url : null;
        });
        if (found == null) {
            throw new DriverException("timeout waiting for URL to contain: " + expected);
        }
        return found;
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
        Element found = pollFor(timeout.toMillis(), options.getRetryInterval(),
                () -> exists(locator) && Terms.isTruthy(script(locator, expression))
                        ? BaseElement.existing(this, locator) : null);
        if (found == null) {
            throw new DriverException("timeout waiting for condition '" + expression + "' on element: " + locator);
        }
        return found;
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
        boolean met = pollUntil(timeout.toMillis(), options.getRetryInterval(),
                () -> Terms.isTruthy(script(expression)));
        if (!met) {
            throw new DriverException("timeout waiting for condition: " + expression);
        }
        return true;
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
        Object result = pollFor(timeout.toMillis(), options.getRetryInterval(), () -> {
            Object r = condition.get();
            return Terms.isTruthy(r) ? r : null;
        });
        if (result == null) {
            throw new DriverException("timeout waiting for condition");
        }
        return result;
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
        boolean met = pollUntil(timeout.toMillis(), options.getRetryInterval(),
                () -> ((Number) script(Locators.countJs(locator))).intValue() == count);
        if (!met) {
            throw new DriverException("timeout waiting for " + count + " elements: " + locator);
        }
        return locateAll(locator);
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

        // CDP requires either a url or a domain. If neither is provided, default to current URL
        if (!cookie.containsKey("url") && !cookie.containsKey("domain")) {
            message.param("url", getUrl());
        }

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
        beginSupersedingNavigation();
        try {
            cdp.method("Page.reload").send();
            waitForPageLoad(options.getPageLoadStrategy());
        } finally {
            supersededLoaderId = null;
        }
    }

    /**
     * Reload the page ignoring cache.
     */
    public void reload() {
        logger.debug("reload page (ignore cache)");
        beginSupersedingNavigation();
        try {
            cdp.method("Page.reload")
                    .param("ignoreCache", true)
                    .send();
            waitForPageLoad(options.getPageLoadStrategy());
        } finally {
            supersededLoaderId = null;
        }
    }

    /**
     * Arm the page-load wait for a navigation whose loaderId CDP does not report
     * (Page.reload, Page.navigateToHistoryEntry): reset the load flags and record the
     * current committed loader as the one the new document must supersede, so a stale
     * signal from the still-showing document cannot satisfy the coming wait.
     * <p>
     * Deliberately lax in one direction: ANY different loader satisfies the wait, so a
     * stray navigation racing the reload (page JS setting location.href) can complete
     * it against that document instead. That is inherent to Page.reload returning no
     * loaderId, and is strictly no worse than the pre-binding behavior, which accepted
     * even the same still-showing document.
     */
    private void beginSupersedingNavigation() {
        domContentEventFired = false;
        framesStillLoading.clear();
        supersededLoaderId = committedLoaderId;
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
                beginSupersedingNavigation();
                historyTargetUrl = (String) prevEntry.get("url");
                try {
                    cdp.method("Page.navigateToHistoryEntry")
                            .param("entryId", entryId)
                            .send();
                    waitForPageLoad(options.getPageLoadStrategy());
                } finally {
                    supersededLoaderId = null;
                    historyTargetUrl = null;
                }
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
                beginSupersedingNavigation();
                historyTargetUrl = (String) nextEntry.get("url");
                try {
                    cdp.method("Page.navigateToHistoryEntry")
                            .param("entryId", entryId)
                            .send();
                    waitForPageLoad(options.getPageLoadStrategy());
                } finally {
                    supersededLoaderId = null;
                    historyTargetUrl = null;
                }
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
        // Retry the enumeration: Target.getTargets can transiently lag a freshly
        // opened/closed tab, or report a page whose url/title field is not yet
        // populated while it is still loading - especially under parallel load. A
        // single-shot lookup then spuriously throws "no page found" (observed flaky
        // on tab-switch under CI). Poll within the same budget as other auto-waits.
        int interval = fastPollInterval();
        int maxAttempts = fastPollAttempts();
        for (int attempt = 0; ; attempt++) {
            String targetId = findPageTarget(titleOrUrl);
            if (targetId != null) {
                activateTarget(targetId);
                return;
            }
            if (attempt >= maxAttempts) {
                break;
            }
            sleep(interval);
        }
        throw new DriverException("no page found matching: " + titleOrUrl);
    }

    /**
     * Find a page target whose title or URL contains the given substring, or null.
     */
    private String findPageTarget(String titleOrUrl) {
        CdpResponse response = cdp.method("Target.getTargets").send();
        List<Map<String, Object>> targets = response.getResult("targetInfos");
        if (targets != null) {
            for (Map<String, Object> target : targets) {
                if (!"page".equals(target.get("type"))) continue;
                String title = (String) target.get("title");
                String url = (String) target.get("url");
                if ((title != null && title.contains(titleOrUrl)) ||
                        (url != null && url.contains(titleOrUrl))) {
                    return (String) target.get("targetId");
                }
            }
        }
        return null;
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
        // Verify the target exists as a page target before activating, retrying within
        // the auto-wait budget: Target.getTargets can briefly lag a freshly opened tab.
        int interval = fastPollInterval();
        int maxAttempts = fastPollAttempts();
        for (int attempt = 0; ; attempt++) {
            if (getPages().contains(targetId)) {
                activateTarget(targetId);
                return;
            }
            if (attempt >= maxAttempts) {
                break;
            }
            sleep(interval);
        }
        throw new DriverException("no page found with targetId: " + targetId);
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
     *    - Target.setAutoAttach is also per-session and must be re-armed, or
     *      OOPIF (cross-origin iframe) tracking goes dark for this tab
     * 5. Get mainFrameId - each tab has its own frame tree
     * 6. Invalidate the readiness future - old context is invalid for the new session
     * 7. Enable domains - Runtime.enable triggers executionContextCreated for the new
     *    main frame, which COMPLETES the readiness future
     * 8. Await readiness - block until that context is live before returning
     *
     * ORDER MATTERS: mainFrameId is resolved and the readiness future invalidated
     * BEFORE Runtime.enable, so the executionContextCreated that Runtime.enable triggers
     * is attributed to the correct (new) main frame and completes the fresh future. The
     * previous code cleared the context map AFTER Runtime.enable, racing the event it had
     * just triggered (the event could populate then be wiped), which is why switchPage()
     * intermittently timed out waiting for a context that never re-fired.
     *
     * CRITICAL: Session filtering in event handlers (see setupEventHandlers) ensures
     * that only events from the CURRENT session update frame state.
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
            // Sync the new page session
            this.pageSessionId = sessionId;
        }

        // Resolve the new tab's main frame id BEFORE enabling domains (works without
        // Page.enable, same as initialize()), then invalidate the readiness future so
        // the executionContextCreated that Runtime.enable triggers below completes a
        // fresh future for THIS frame rather than racing a clear() afterwards.
        CdpResponse frameResponse = cdp.method("Page.getFrameTree").send();
        mainFrameId = frameResponse.getResult("frameTree.frame.id");
        currentFrame = null;
        // Loader tracking belongs to the previously-driven tab — re-seed the committed
        // document from this tab's frame tree and drop the stale DOMContentLoaded tag.
        committedLoaderId = frameResponse.getResultAsString("frameTree.frame.loaderId");
        domContentLoaderId = null;
        invalidateMainContext();

        // Re-enable required domains on new target (triggers executionContextCreated)
        cdp.method("Page.enable").send();
        cdp.method("Runtime.enable").send();
        cdp.method("Page.setLifecycleEventsEnabled")
                .param("enabled", true)
                .send();

        // Target.setAutoAttach is session-scoped: initialize() armed it on the
        // websocket's root session, but this tab is driven through the fresh
        // flattened session created above, where auto-attach is OFF by default.
        // Without re-arming it here, cross-origin iframes (OOPIFs) in this tab
        // never fire Target.attachedToTarget, oopifSessions stays empty, and
        // switchFrame() on such an iframe fails with "could not find frame" —
        // observed with payment-provider iframes on pooled drivers after a prior
        // scenario called switchPage()/close(). Stale OOPIF entries belong to the
        // previously-driven tab and are unreachable from this session, so drop
        // them; Chrome re-fires attachedToTarget for this tab's existing OOPIFs
        // as soon as auto-attach lands.
        oopifTargets.clear();
        oopifSessions.clear();
        // Frame-context readiness entries belong to the previously-driven tab's
        // frames — unreachable from this session, so drop them. No waiter can be
        // in flight here (frame waits happen on this same scenario thread).
        frameContextReady.clear();
        cdp.method("Target.setAutoAttach")
                .param("autoAttach", true)
                .param("waitForDebuggerOnStart", false)
                .param("flatten", true)
                .send();

        // Block until the new tab's execution context is live before returning, so a
        // script() immediately after switchPage() never races a "context not found".
        awaitMainContext(options.getTimeoutDuration().toMillis());
    }

    // ========== Utilities ==========

    /**
     * Fast poll interval (ms) for transient driver conditions (execution-context
     * recovery, target enumeration lag). Capped well below the element-poll interval
     * so a routine post-navigation hiccup recovers quickly instead of burning ~500ms.
     */
    private int fastPollInterval() {
        return Math.min(options.getRetryInterval(), 100);
    }

    /**
     * Number of fast-poll attempts that preserve the configured retry time budget
     * (retryCount * retryInterval) - so a genuinely slow box still gets the full wait,
     * but a fast recovery returns promptly.
     */
    private int fastPollAttempts() {
        int interval = fastPollInterval();
        return Math.max(options.getRetryCount(),
                (int) Math.ceil((double) options.getRetryCount() * options.getRetryInterval() / Math.max(1, interval)));
    }

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
            // Abort on interruption instead of degrading into a busy spin: sleep()
            // preserves the interrupt flag, so every later sleep would return
            // instantly and the remaining attempts would hammer CDP back-to-back.
            if (Thread.currentThread().isInterrupted()) {
                logger.warn("retry aborted (thread interrupted): {}", description);
                return false;
            }
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

    /**
     * Deadline-based value poll shared by the wait machinery: {@code attempt} runs
     * immediately, then every {@code intervalMs} until it returns non-null or the
     * deadline passes — with one final attempt after the last sleep so a condition
     * that lands right at the deadline is not missed. Returns {@code null} on
     * timeout.
     * <p>
     * Iterations are deliberately silent — unlike {@link #retry}, whose per-attempt
     * WARN is a flaky-test diagnostic for the element auto-wait and whose
     * attempt-count budget ({@code retryCount × retryInterval}) is a documented,
     * user-configurable contract. The two are intentionally separate.
     * <p>
     * Interruption ABORTS the wait (null return). {@link #sleep} preserves the
     * interrupt flag, so without this check an interrupted thread would sail through
     * every subsequent sleep instantly and burn the rest of the budget as a busy
     * spin of CDP round trips.
     */
    private static <T> T pollFor(long timeoutMs, long intervalMs, Supplier<T> attempt) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            T result = attempt.get();
            if (result != null) {
                return result;
            }
            if (System.currentTimeMillis() >= deadline || Thread.currentThread().isInterrupted()) {
                return null;
            }
            sleep(intervalMs);
        }
    }

    /**
     * Boolean-condition form of {@link #pollFor}.
     */
    private static boolean pollUntil(long timeoutMs, long intervalMs, Supplier<Boolean> condition) {
        return pollFor(timeoutMs, intervalMs, () -> Boolean.TRUE.equals(condition.get()) ? Boolean.TRUE : null) != null;
    }

}
