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
package io.karatelabs.core;

import io.karatelabs.common.Json;
import io.karatelabs.common.Resource;
import io.karatelabs.common.StringUtils;
import io.karatelabs.driver.Driver;
import io.karatelabs.driver.cdp.CdpDriver;
import io.karatelabs.driver.cdp.CdpDriverOptions;
import io.karatelabs.driver.DriverApi;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.Step;
import io.karatelabs.gherkin.Tag;
import io.karatelabs.http.HttpRequestBuilder;
import io.karatelabs.js.JavaCallable;
import io.karatelabs.output.LogContext;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ScenarioRuntime implements Callable<ScenarioResult>, KarateJsContext {

    private static final Logger logger = LogContext.RUNTIME_LOGGER;

    // The scenario actually executing on this thread, set for the duration of call().
    // Report collection (logs and embeds via LogContext, and feature-call results via
    // StepExecutor) follows this live scenario rather than the runtime captured by a
    // karate bridge — so a JS function defined in one scenario and reused in another
    // (e.g. a helper loaded once via callSingle and shared through config) still attaches
    // its karate.call results to the scenario that actually invoked it. Deliberately NOT
    // consulted by getRuntime(): execution and variable scope keep honoring the bridge's
    // captured context, leaving room to honor the closure for future async JS calls.
    private static final ThreadLocal<ScenarioRuntime> CURRENT = new ThreadLocal<>();

    /** The scenario executing on the current thread, or null outside a scenario's call(). */
    public static ScenarioRuntime currentOrNull() {
        return CURRENT.get();
    }

    private final FeatureRuntime featureRuntime;
    private final Scenario scenario;
    private final KarateJs karate;
    private final StepExecutor executor;
    private final ScenarioResult result;
    private final KarateConfig config;

    private Step currentStep;
    private boolean stopped;
    private boolean aborted;
    private boolean skipBackground;
    private Throwable error;
    private boolean hasIgnoredFailure;
    private Throwable firstIgnoredError;
    // True when the scenario is tagged @report=false. Step detail is suppressed in
    // HTML / Cucumber-JSON / JUnit-XML / JSONL outputs. Failures still count and
    // surface a generic redacted error so sensitive content doesn't leak into
    // CI artifacts. Full failure detail goes only to runtime logs.
    private final boolean reportDisabled;

    // Signal/listen mechanism for async process integration
    private volatile Object listenResult;
    private CountDownLatch listenLatch = new CountDownLatch(1);

    // Browser driver (lazily initialized)
    private Driver driver;
    private String driverScope = "scenario"; // "scenario" (default) or "caller"

    // Channels (kafka, grpc, etc.) registered during this scenario
    private List<Channel> channels;

    // Performance testing - tracks the previous HTTP request's perf event
    // Events are held until the next HTTP request or scenario end, so that
    // assertion failures can be attributed to the preceding HTTP request
    private PerfEvent prevPerfEvent;
    private boolean driverFromProvider;  // true if driver came from provider (use release, not quit)
    private boolean driverInherited;     // true if driver was inherited from caller (don't close)

    // Cookie jar for auto-sending responseCookies on subsequent requests (V1 compatibility)
    private final Map<String, Map<String, Object>> cookieJar = new LinkedHashMap<>();

    // Identity set of values that originated from an untrusted HTTP request (Mock Server only).
    // Such values must not have their `#(...)` strings evaluated as embedded expressions — that
    // would let a client inject Karate/Java code. Only MockHandler populates this; it stays empty
    // (and the guard below is free) for ordinary execution. See markRequestDerived / isRequestDerived.
    private final java.util.Set<Object> requestDerived =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    // When true a mock evaluates request-derived embedded expressions (the pre-fix behavior) -
    // opt-in only, since it re-opens the injection surface. Defaults to off (request data is data).
    private boolean requestExpressionsEnabled;

    // Debug support - step navigation
    private List<Step> steps;
    private int stepIndex;
    private io.karatelabs.js.RunInterceptor<?> interceptor;
    private io.karatelabs.js.DebugPointFactory<?> pointFactory;

    public ScenarioRuntime(FeatureRuntime featureRuntime, Scenario scenario) {
        this.featureRuntime = featureRuntime;
        this.scenario = scenario;
        // Propagate from the caller chain so a @report=false scenario hides any
        // features it calls (matches v1's `reportDisabled` inheritance).
        ScenarioRuntime callerScenario = featureRuntime != null ? featureRuntime.getCallerScenario() : null;
        this.reportDisabled = isReportDisabledTag(scenario)
                || (callerScenario != null && callerScenario.reportDisabled);

        // KarateJs owns the Engine and HTTP infrastructure
        Resource featureResource = scenario.getFeature().getResource();
        // Use custom HTTP client factory from Suite if configured
        Suite suite = featureRuntime != null ? featureRuntime.getSuite() : null;
        io.karatelabs.http.HttpClientFactory factory = suite != null ? suite.getHttpClientFactory() : null;
        this.karate = factory != null ? new KarateJs(featureResource, factory) : new KarateJs(featureResource);

        this.executor = new StepExecutor(this);
        this.result = new ScenarioResult(scenario);
        this.result.setReportDisabled(reportDisabled);
        this.config = new KarateConfig();

        // Initialize HTTP builder with default charset from config (V1 compatibility)
        if (config.getCharset() != null) {
            karate.http.charset(config.getCharset().name());
        }

        // Set up debug support from Suite if configured (needed for config evaluation)
        if (suite != null && suite.debugInterceptor != null && suite.debugPointFactory != null) {
            @SuppressWarnings("unchecked")
            io.karatelabs.js.RunInterceptor<Object> debugIntcptr = (io.karatelabs.js.RunInterceptor<Object>) suite.debugInterceptor;
            @SuppressWarnings("unchecked")
            io.karatelabs.js.DebugPointFactory<Object> debugFactory = (io.karatelabs.js.DebugPointFactory<Object>) suite.debugPointFactory;
            setDebugSupport(debugIntcptr, debugFactory);
        }

        initEngine();
    }

    /**
     * Constructor for standalone execution without FeatureRuntime.
     */
    public ScenarioRuntime(KarateJs karate, Scenario scenario) {
        this.featureRuntime = null;
        this.scenario = scenario;
        this.reportDisabled = isReportDisabledTag(scenario);
        this.karate = karate;
        this.executor = new StepExecutor(this);
        this.result = new ScenarioResult(scenario);
        this.result.setReportDisabled(reportDisabled);
        this.config = new KarateConfig();

        // Initialize HTTP builder with default charset from config (V1 compatibility)
        if (config.getCharset() != null) {
            karate.http.charset(config.getCharset().name());
        }

        // Wire up KarateJsContext - all karate.* API methods use this
        karate.setContext(this);
    }

    private void initEngine() {
        // Wire up KarateJsContext - all karate.* API methods use this
        karate.setContext(this);

        // Set karate.env before config evaluation
        if (featureRuntime != null && featureRuntime.getSuite() != null) {
            karate.setEnv(featureRuntime.getSuite().env);
        }

        // Seed ext globals (registered by exts in onBoot) into JS scope as hidden
        // root bindings — same mechanism as the karate/read/match globals — so they
        // survive across steps and are visible to karate-config.js. Done before
        // evalConfig() below; applies to called features too (suite-level singletons).
        if (featureRuntime != null && featureRuntime.getSuite() != null) {
            for (var entry : featureRuntime.getSuite().getGlobals().entrySet()) {
                Object value = entry.getValue();
                // A per-scenario ext global registers an ExtGlobalFactory (vs a shared
                // singleton instance) — mint a fresh instance here with this runtime as
                // its KarateJsContext, so its state is scenario-scoped and it can resolve
                // this:/classpath:/file: paths + reach the runtime. See ExtGlobalFactory.
                Object binding = value instanceof ExtGlobalFactory factory ? factory.create(this) : value;
                karate.engine.putRootBinding(entry.getKey(), binding);
            }
        }

        // Evaluate config (only for top-level scenarios, not called features).
        // In dry-run mode, skip config JS for non-@setup scenarios - @setup scenarios
        // still run fully so scenario outlines can resolve their example data (V1 parity).
        if (featureRuntime != null && featureRuntime.getSuite() != null && featureRuntime.isTopLevel()) {
            if (!isDryRunSkip()) {
                evalConfig();
            }
        }

        // Inherit parent variables if called from another feature
        if (featureRuntime != null && featureRuntime.isCalled()) {
            inheritVariables();
        }

        // Expose the call magic variables (V1 parity).
        // __arg is bound whenever an argument map is supplied — which includes the
        // top-level runFeature(path, arg) / karate entry points that have no caller —
        // and ALSO for every called scenario, where it is null when called without an
        // argument so reading `__arg` yields null instead of a "__arg is not defined"
        // ReferenceError. __loop is the iteration index for called scenarios (-1 when
        // not a loop call).
        Map<String, Object> callArg = featureRuntime != null ? featureRuntime.getCallArg() : null;
        boolean isCalled = featureRuntime != null && featureRuntime.isCalled();
        if (callArg != null || isCalled) {
            karate.engine.putRootBinding("__arg", callArg);
        }
        if (isCalled) {
            karate.engine.putRootBinding("__loop", featureRuntime.getLoopIndex());
        }
        // Spread the individual argument keys as variables.
        if (callArg != null) {
            for (var entry : callArg.entrySet()) {
                karate.engine.put(entry.getKey(), entry.getValue());
            }
        }

        // Set example data for outline scenarios
        if (scenario.getExampleData() != null) {
            Map<String, Object> exampleData = scenario.getExampleData();
            for (var entry : exampleData.entrySet()) {
                karate.engine.put(entry.getKey(), entry.getValue());
            }
            // Set __row to the full example data map
            karate.engine.putRootBinding("__row", exampleData);
            // Set __num to the example index (0-based)
            karate.engine.putRootBinding("__num", scenario.getExampleIndex());
        }
    }

    /**
     * Detect the {@code @report=false} tag at scenario construction time.
     * The tag scopes to the scenario AND any features it calls (suppression
     * propagates downward). Failures still surface a redacted message.
     */
    private static boolean isReportDisabledTag(Scenario scenario) {
        List<Tag> tags = scenario.getTagsEffective();
        if (tags == null || tags.isEmpty()) return false;
        for (Tag tag : tags) {
            if ("report".equals(tag.getName())) {
                List<String> values = tag.getValues();
                if (values != null && values.contains("false")) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isReportDisabled() {
        return reportDisabled;
    }

    /**
     * True when this scenario's steps, hooks and config JS should be bypassed for dry-run.
     * {@code @setup} scenarios are exempt so dynamic scenario-outline example data
     * (e.g. {@code Examples: | karate.setup().data |}) still resolves in the report.
     * Feature authors can observe dry-run state from inside {@code @setup} via {@code karate.suite.dryRun}.
     */
    boolean isDryRunSkip() {
        Suite suite = featureRuntime != null ? featureRuntime.getSuite() : null;
        return suite != null && suite.dryRun && !scenario.isSetup();
    }

    /**
     * Evaluate karate-base.js, karate-config.js (and env-specific config) in this scenario's context.
     * This allows callSingle and other karate.* functions to work during config.
     */
    @SuppressWarnings("unchecked")
    private void evalConfig() {
        Suite suite = featureRuntime.getSuite();

        // Evaluate karate-base.js first (shared functions available to configs)
        if (suite.baseResource != null) {
            evalConfigJs(suite.baseResource);
        }

        // Evaluate main config
        if (suite.configResource != null) {
            evalConfigJs(suite.configResource);
        }

        // Evaluate env-specific config
        if (suite.configEnvResource != null) {
            evalConfigJs(suite.configEnvResource);
        }
    }

    /**
     * Evaluate a config JS and apply its result to the engine.
     * Supports multiple patterns:
     * 1. Function definition only: function fn() { return {...}; } - will call it
     * 2. Self-executing: function fn() { return {...}; } fn(); - returns result directly
     * 3. Object literal: ({ key: value }) - already an object
     */
    @SuppressWarnings("unchecked")
    private void evalConfigJs(io.karatelabs.common.Resource resource) {
        String displayName = resource.getRelativePath();
        if (displayName == null) {
            displayName = resource.getPrefixedPath();
        }
        String js = resource.getText();
        try {
            Object result;

            // Try wrapping in parentheses first (handles function definitions)
            // We need to wrap the content but preserve the resource path for debugging
            Object fn = null;
            boolean parseFailed = false;
            try {
                // Create a wrapped resource that adds parentheses but preserves the path
                io.karatelabs.common.Resource wrappedResource = io.karatelabs.common.Resource.embedded("(" + js + ")", resource, 0);
                fn = karate.engine.eval(wrappedResource);
            } catch (Exception e) {
                // Parentheses wrapping failed to parse - fall back to direct eval
                parseFailed = true;
            }

            if (parseFailed) {
                // Self-invoking pattern: function fn() { ... } fn();
                result = karate.engine.eval(resource);
            } else if (fn instanceof JavaCallable callable) {
                // It's a function definition - invoke it
                result = callable.call(null);
            } else {
                // Already evaluated to a value (e.g., object literal)
                result = fn;
            }

            // Apply config variables to engine
            if (result instanceof Map) {
                Map<String, Object> configVars = (Map<String, Object>) result;
                for (var entry : configVars.entrySet()) {
                    karate.engine.put(entry.getKey(), entry.getValue());
                }
                logger.debug("Evaluated {}: {} variables", displayName, configVars.size());
            } else if (result != null) {
                logger.warn("{} did not return an object, got: {}", displayName, result.getClass().getSimpleName());
            }
        } catch (Exception e) {
            logger.warn("Failed to evaluate {}: {}", displayName, e.getMessage());
            throw new RuntimeException("Config evaluation failed: " + displayName + " - " + e.getMessage(), e);
        }
    }

    /**
     * Execute the @setup scenario and return all its variables.
     */
    public Map<String, Object> executeSetup(String name) {
        if (featureRuntime == null) {
            throw new RuntimeException("karate.setup() requires a feature context");
        }
        Scenario setupScenario = scenario.getFeature().getSetup(name);
        if (setupScenario == null) {
            String message = "no scenario found with @setup tag";
            if (name != null) {
                message = message + " and name '" + name + "'";
            }
            throw new RuntimeException(message);
        }
        // Run the setup scenario without background
        ScenarioRuntime sr = new ScenarioRuntime(featureRuntime, setupScenario);
        sr.setSkipBackground(true);
        sr.call();
        return sr.getAllVariables();
    }

    /**
     * Execute the @setup scenario with caching (only runs once per feature).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeSetupOnce(String name) {
        if (featureRuntime == null) {
            throw new RuntimeException("karate.setupOnce() requires a feature context");
        }
        String cacheKey = name == null ? "__default__" : name;
        Map<String, Object> setupCache = featureRuntime.getSetupOnceCache();
        Map<String, Object> cached = (Map<String, Object>) setupCache.get(cacheKey);
        if (cached != null) {
            // Return a shallow copy to prevent modifications affecting other scenarios
            return new HashMap<>(cached);
        }
        synchronized (setupCache) {
            // Double-check after acquiring lock
            cached = (Map<String, Object>) setupCache.get(cacheKey);
            if (cached != null) {
                return new HashMap<>(cached);
            }
            Map<String, Object> result = executeSetup(name);
            setupCache.put(cacheKey, result);
            return new HashMap<>(result);
        }
    }

    /**
     * Execute a feature via karate.call() and return its result variables.
     * This is used for JavaScript calls like: karate.call('other.feature', { arg: 'value' })
     *
     * Supports call-by-tag syntax:
     * - call('file.feature@name=tagvalue') - call feature, run only scenario with matching tag
     * - call('@tagname') - call scenario in same file by tag
     *
     * When arg is a List, loops over elements (same as `call read('path') array`) and
     * returns a List of result maps.
     */
    @SuppressWarnings("unchecked")
    public Object executeJsCall(String path, Object arg) {
        if (featureRuntime == null) {
            throw new RuntimeException("karate.call() requires a feature context");
        }

        // Parse path, tag selector and line filter — shared parser keeps JS
        // (karate.call) and keyword (`call read(...)`) syntax in lockstep.
        StepUtils.ParsedFeaturePath parsed = StepUtils.parseFeaturePath(path);
        String tagSelector = parsed.tagSelector();
        Set<Integer> lineFilters = parsed.lineFilters();
        Feature calledFeature;
        if (parsed.sameFile()) {
            // call('@tagname') — scenario in the same file
            calledFeature = featureRuntime.getFeature();
        } else {
            Resource calledResource = featureRuntime.resolve(parsed.path());
            // A .js target is a callable helper, not a feature — evaluate and invoke it,
            // mirroring read()/callSingle and the `call` keyword (and v1, which dispatched
            // on the resolved type). Blindly parsing it as a feature silently returned an
            // empty result, dropping the helper's functions.
            if ("js".equals(calledResource.getExtension())) {
                Object jsTarget = karate.engine.eval(calledResource);
                if (jsTarget instanceof JavaCallable fn) {
                    return arg == null ? fn.call(null) : fn.call(null, arg);
                }
                // Non-callable JS (e.g. an object/JSON literal file) — return as-is.
                return jsTarget;
            }
            calledFeature = Feature.read(calledResource);
        }

        // Array-loop call - delegate to shared helper used by the `call` keyword
        if (arg instanceof List) {
            return executor.callFeatureLoop(calledFeature, (List<?>) arg, tagSelector, lineFilters);
        }

        // Single call - arg must be a map (or null)
        Map<String, Object> callArg = null;
        if (arg != null) {
            if (arg instanceof Map) {
                callArg = (Map<String, Object>) arg;
            } else {
                throw new RuntimeException("karate.call() arg must be a map or list, got: " + arg.getClass());
            }
        }

        Map<String, Object> resultVars = executor.callFeatureSingle(calledFeature, callArg, tagSelector, lineFilters);
        return resultVars != null ? resultVars : new HashMap<>();
    }

    /**
     * Execute karate.callonce() - runs a feature once per FeatureRuntime and caches the result.
     * Uses the same cache as the callonce keyword.
     * Uses double-check locking to ensure thread-safe execution in parallel scenarios.
     */
    public Object executeJsCallOnce(String path, Object arg) {
        if (featureRuntime == null) {
            throw new RuntimeException("karate.callonce() requires a feature context");
        }

        // Use the same cache key format as the keyword: "callonce:call read('path')"
        String cacheKey = "callonce:call read('" + path + "')";

        // Use feature-level cache (not suite-level) - callOnce is scoped per feature
        Map<String, Object> cache = featureRuntime.getCallOnceCache();
        java.util.concurrent.locks.ReentrantLock lock = featureRuntime.getCallOnceLock();

        // Fast path - check cache without lock
        Object cached = cache.get(cacheKey);
        if (cached != null) {
            // Deep copy to prevent cross-scenario mutation
            return deepCopy(cached);
        }

        // Slow path - acquire lock for execution
        lock.lock();
        try {
            // Double-check after acquiring lock
            Object rechecked = cache.get(cacheKey);
            if (rechecked != null) {
                return deepCopy(rechecked);
            }

            // Not cached - execute the call
            Object result = executeJsCall(path, arg);

            // Cache a deep copy to prevent the caller from mutating the cache
            cache.put(cacheKey, deepCopy(result));

            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Execute karate.callSingle() - runs a file once per Suite and caches the result.
     * Uses Suite-level locking to ensure thread-safe execution in parallel scenarios.
     *
     * Flow:
     * 1. Check in-memory cache (lock-free for fast path)
     * 2. If callSingleCache is configured, check disk cache
     * 3. If not cached, acquire Suite lock
     * 4. Double-check cache (another thread may have cached while waiting)
     * 5. Execute and cache result (in-memory and optionally to disk)
     * 6. Return deep copy to prevent cross-thread mutation
     *
     * Disk caching (configure callSingleCache):
     * - { minutes: 15 } - cache to disk for 15 minutes (default dir: karate-temp/cache)
     * - { minutes: 15, dir: 'some/folder' } - custom cache directory
     * - Only JSON-like results (Map/List) are persisted to disk
     *
     * Exceptions are cached and re-thrown on subsequent calls.
     */
    public Object executeCallSingle(String path, Object arg) {
        if (featureRuntime == null || featureRuntime.getSuite() == null) {
            throw new RuntimeException("karate.callSingle() requires a Suite context");
        }

        Suite suite = featureRuntime.getSuite();
        Map<String, Object> cache = suite.getCallSingleCache();
        ReentrantLock lock = suite.getCallSingleLock();

        // Get disk cache settings from config
        int cacheMinutes = config.getCallSingleCacheMinutes();
        String cacheDir = config.getCallSingleCacheDir();
        if (cacheDir == null || cacheDir.isEmpty()) {
            // Default to <buildDir>/karate-temp/cache (cleaned by 'karate clean')
            cacheDir = io.karatelabs.common.FileUtils.getBuildDir() + "/karate-temp/cache";
        }

        // Fast path: check if already in memory cache (no locking needed)
        if (cache.containsKey(path)) {
            logger.trace("[callSingle] memory cache hit: {}", path);
            return unwrapCachedResult(cache.get(path));
        }

        // Slow path: acquire lock and execute
        long startWait = System.currentTimeMillis();
        logger.debug("[callSingle] waiting for lock: {}", path);
        lock.lock();
        try {
            // Double-check: another thread may have cached while we waited
            if (cache.containsKey(path)) {
                long waitTime = System.currentTimeMillis() - startWait;
                logger.info("[callSingle] lock acquired after {}ms, memory cache hit: {}", waitTime, path);
                return unwrapCachedResult(cache.get(path));
            }

            Object result = null;
            File cacheFile = null;

            // Check disk cache if configured
            if (cacheMinutes > 0) {
                String cleanedName = StringUtils.toIdString(path);
                cacheFile = new File(cacheDir, cleanedName + ".txt");
                long staleThreshold = System.currentTimeMillis() - (cacheMinutes * 60L * 1000L);

                if (cacheFile.exists()) {
                    long lastModified = cacheFile.lastModified();
                    if (lastModified > staleThreshold) {
                        try {
                            String json = Files.readString(cacheFile.toPath());
                            result = Json.parseLenient(json);
                            logger.info("[callSingle] disk cache hit: {}", cacheFile);
                        } catch (IOException e) {
                            logger.warn("[callSingle] disk cache read failed: {} - {}", cacheFile, e.getMessage());
                        }
                    } else {
                        logger.info("[callSingle] disk cache stale: {} (modified {}ms ago, threshold {}min)",
                                cacheFile, System.currentTimeMillis() - lastModified, cacheMinutes);
                    }
                } else {
                    logger.debug("[callSingle] disk cache miss, will create: {}", cacheFile);
                }
            }

            // Execute if not found in disk cache
            if (result == null) {
                logger.info("[callSingle] >> executing: {}", path);
                long startExec = System.currentTimeMillis();

                try {
                    result = executeCallSingleInternal(path, arg);
                } catch (Exception e) {
                    // Cache the exception so subsequent calls also fail fast
                    logger.warn("[callSingle] caching exception for: {} - {}", path, e.getMessage());
                    cache.put(path, new CallSingleException(e));
                    throw e;
                }

                long execTime = System.currentTimeMillis() - startExec;
                logger.info("[callSingle] << executed in {}ms: {}", execTime, path);

                // Write to disk cache if configured and result is JSON-like
                if (cacheMinutes > 0 && cacheFile != null) {
                    if (Json.isMapOrList(result)) {
                        try {
                            cacheFile.getParentFile().mkdirs();
                            String json = StringUtils.formatJson(result, false, false, false);
                            Files.writeString(cacheFile.toPath(), json);
                            logger.info("[callSingle] disk cache write: {}", cacheFile);
                        } catch (IOException e) {
                            logger.warn("[callSingle] disk cache write failed: {} - {}", cacheFile, e.getMessage());
                        }
                    } else {
                        logger.warn("[callSingle] disk cache skipped (not JSON-like): {}", path);
                    }
                }
            }

            // Cache in memory
            cache.put(path, result);
            logger.debug("[callSingle] memory cached: {}", path);

            return deepCopy(result);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Internal execution of callSingle - reads and evaluates the file.
     * Supports:
     * - ?suffix syntax for cache key differentiation (suffix is stripped for file read)
     * - @tag syntax for scenario selection (e.g., file.feature@tagname)
     */
    private Object executeCallSingleInternal(String path, Object arg) {
        // Strip ?suffix from path for file reading (suffix is only for cache key differentiation)
        // e.g., "get-token.feature?admin" -> read "get-token.feature", cache as "get-token.feature?admin"
        String filePath = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;

        // Parse @tag selector from path using shared utility
        StepUtils.ParsedFeaturePath parsed = StepUtils.parseFeaturePath(filePath);
        filePath = parsed.path() != null ? parsed.path() : filePath;
        String tagSelector = parsed.tagSelector();

        // Read the file using the engine (which has access to the read function)
        Object content = karate.engine.eval("read('" + filePath.replace("'", "\\'") + "')");

        if (content instanceof Feature) {
            // Feature file - execute it
            Feature calledFeature = (Feature) content;
            @SuppressWarnings("unchecked")
            Map<String, Object> callArg = arg != null ? (Map<String, Object>) arg : null;

            FeatureRuntime nestedFr = new FeatureRuntime(
                    featureRuntime.getSuite(),
                    calledFeature,
                    featureRuntime,
                    this,
                    false,  // Isolated scope
                    callArg,
                    tagSelector
            );
            FeatureResult fr = nestedFr.call();

            // Attach the called feature's result so its steps (and HTTP traffic) surface
            // in the calling scenario's HTML / Cucumber JSON / JUnit report. callSingle
            // executes exactly once per Suite under a lock — only the "winning" scenario
            // (cache miss) reaches this code, so the call results appear under whichever
            // scenario's thread actually ran the feature. Done before the failure throw
            // so a failed callSingle still surfaces its steps in the report.
            executor.addCallResult(fr);

            // Check if the feature failed
            if (fr.isFailed()) {
                String failureMsg = fr.getScenarioResults().stream()
                        .filter(ScenarioResult::isFailed)
                        .findFirst()
                        .map(ScenarioResult::getFailureMessage)
                        .orElse("callSingle feature failed");
                throw new RuntimeException("callSingle failed: " + path + " - " + failureMsg);
            }

            if (nestedFr.getLastExecuted() != null) {
                return nestedFr.getLastExecuted().getAllVariables();
            }
            return new HashMap<>();
        } else if (content instanceof JavaCallable) {
            // JavaScript function - invoke it with the arg
            JavaCallable fn = (JavaCallable) content;
            return fn.call(null, arg == null ? new Object[0] : new Object[]{arg});
        } else {
            // Return as-is (JSON, text, etc.)
            return content;
        }
    }

    /**
     * Unwrap cached result - throws if it's a cached exception.
     */
    private Object unwrapCachedResult(Object cached) {
        if (cached instanceof CallSingleException) {
            throw new RuntimeException(((CallSingleException) cached).cause.getMessage(),
                    ((CallSingleException) cached).cause);
        }
        return deepCopy(cached);
    }

    /**
     * Deep copy to prevent cross-thread mutation of cached data.
     */
    @SuppressWarnings("unchecked")
    private Object deepCopy(Object value) {
        if (value == null) {
            return null;
        }
        // JsCallable functions shouldn't be deep-copied - they should be shared across threads
        if (value instanceof JavaCallable) {
            return value;
        }
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                copy.put(entry.getKey(), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        // Primitives, strings, etc. are immutable - return as-is
        return value;
    }

    /**
     * Wrapper for cached exceptions to distinguish from null results.
     */
    private static class CallSingleException {
        final Exception cause;
        CallSingleException(Exception cause) {
            this.cause = cause;
        }
    }

    private void inheritVariables() {
        boolean sharedScope = featureRuntime.isSharedScope();
        // First check for callerScenario (the currently executing scenario that made the call)
        ScenarioRuntime callerScenario = featureRuntime.getCallerScenario();
        if (callerScenario != null) {
            // V1 behavior: inherit config from caller (copy or share based on scope)
            inheritConfigFromCaller(callerScenario, sharedScope);

            Map<String, Object> parentVars = callerScenario.getAllVariables();
            for (var entry : parentVars.entrySet()) {
                Object value = entry.getValue();
                if (!sharedScope) {
                    // Isolated scope - shallow copy maps and lists so mutations don't affect parent
                    value = shallowCopy(value);
                }
                karate.engine.put(entry.getKey(), value);
            }
            // Inherit cookie jar from caller so called feature shares session cookies
            inheritCookieJarFromCaller(callerScenario);
            // Inherit driver and driver helper root bindings from caller
            inheritDriverFromCaller(callerScenario);
            return;
        }
        // Fallback to lastExecuted for other cases (e.g., sequential scenarios in same feature)
        FeatureRuntime caller = featureRuntime.getCaller();
        if (caller != null && caller.getLastExecuted() != null) {
            ScenarioRuntime lastExecuted = caller.getLastExecuted();
            // V1 behavior: inherit config from caller
            inheritConfigFromCaller(lastExecuted, sharedScope);

            Map<String, Object> parentVars = lastExecuted.getAllVariables();
            for (var entry : parentVars.entrySet()) {
                Object value = entry.getValue();
                if (!sharedScope) {
                    value = shallowCopy(value);
                }
                karate.engine.put(entry.getKey(), value);
            }
            inheritCookieJarFromCaller(lastExecuted);
            // Inherit driver from lastExecuted
            inheritDriverFromCaller(lastExecuted);
        }
    }

    /**
     * Inherit config from caller scenario (V1 behavior).
     * <p>
     * Both shared and isolated scope copy the typed {@link KarateConfig} fields
     * from the caller — variables differ in scope semantics, but configuration
     * (proxy, ssl, timeouts, etc.) always propagates downward into a called
     * feature.
     * <p>
     * Re-projects the inherited config onto this scenario's HTTP client. Without
     * this push, the fresh {@code HttpClient} created in the constructor would
     * never see settings like {@code proxy} set in {@code karate-config.js} or
     * via {@code * configure proxy = ...} in the caller.
     */
    private void inheritConfigFromCaller(ScenarioRuntime callerScenario, boolean sharedScope) {
        this.config.copyFrom(callerScenario.config);
        karate.client.apply(this.config);
        logger.debug("Inherited config from caller");
    }

    private void inheritCookieJarFromCaller(ScenarioRuntime callerScenario) {
        Map<String, Map<String, Object>> parentCookies = callerScenario.getCookieJar();
        if (!parentCookies.isEmpty()) {
            cookieJar.putAll(parentCookies);
        }
    }

    /**
     * Inherit driver and driver helper root bindings from caller scenario.
     * This enables browser reuse across called features (V1 behavior).
     * Note: driverConfig is inherited via inheritConfigFromCaller().
     */
    private void inheritDriverFromCaller(ScenarioRuntime callerScenario) {
        // Inherit existing driver instance if available
        if (callerScenario.driver != null && !callerScenario.driver.isTerminated()) {
            this.driver = callerScenario.driver;
            this.driverInherited = true;
            logger.debug("Inherited driver from caller: {}", driver);
            // Copy driver-related root bindings from parent
            Map<String, Object> parentRootBindings = callerScenario.karate.engine.getRootBindings();
            for (var entry : parentRootBindings.entrySet()) {
                String key = entry.getKey();
                // Skip karate/read/match - they were re-initialized for this context
                if (!"karate".equals(key) && !"read".equals(key) && !"match".equals(key)) {
                    karate.engine.putRootBinding(key, entry.getValue());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object shallowCopy(Object value) {
        // JsCallable functions implement Map but must be shared, not wrapped.
        // Without this guard, functions from karate-config.js get
        // turned into plain LinkedHashMaps when inherited into an isolated-scope
        // called feature, losing their callable behavior.
        if (value instanceof JavaCallable) {
            return value;
        }
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) value);
        } else if (value instanceof List) {
            return new ArrayList<>((List<Object>) value);
        }
        return value;
    }

    @Override
    public ScenarioResult call() {
        // karate-base.js / karate-config.js / env-config eval (in the constructor's
        // initEngine()) may have produced log lines and embeds — capture them so the
        // upcoming LogContext.set(...) doesn't drop them on the floor. They'll be
        // replayed into the fresh context below so the first step's StepResult.log
        // (or the beforeScenario hook step, if one fires) surfaces config-time output
        // alongside config-time karate.call / karate.callSingle results.
        // LogContext.get() lazy-creates an empty one if no thread-local is set, so this
        // is safe even on a cold thread — collect() / collectEmbeds() return ""/null.
        // Hold onto the outer context: a nested call() (call/callonce in a Background or
        // step) runs on the caller's thread, so on exit we must hand the caller back its
        // own LogContext — mask, pretty and all — rather than clearing the thread-local
        // and leaving the caller's later HTTP steps to lazy-create a mask-less one.
        LogContext outerLogContext = LogContext.get();
        String configTimeLog = outerLogContext.collect();
        java.util.List<StepResult.Embed> configTimeEmbeds = outerLogContext.collectEmbeds();
        boolean nestedCall = featureRuntime != null && featureRuntime.isCalled();
        // Mark this scenario as the live one on this thread so report collection (call
        // results, logs, embeds) attaches here. A nested call/callonce runs on the
        // caller's thread, so save the caller's runtime and hand it back on exit.
        ScenarioRuntime outerRuntime = CURRENT.get();
        CURRENT.set(this);
        LogContext.set(new LogContext());
        // Repopulate the fresh LogContext from this scenario's KarateConfig — which is
        // the source of truth for mask + pretty. Without this, anything that
        // karate-config.js (evaluated during the constructor's initEngine()) configured
        // via `karate.configure('logging', {...})` would be silently dropped here, since
        // the new LogContext starts with mask=null.
        config.applyLoggingToContext(LogContext.get());
        // Replay the captured config-time output into the fresh, properly-configured
        // context. Order matters: this happens AFTER applyLoggingToContext so the new
        // context already has the right threshold / mask / pretty before content lands.
        if (configTimeLog != null && !configTimeLog.isEmpty()) {
            LogContext.get().appendCaptured(configTimeLog);
        }
        if (configTimeEmbeds != null) {
            for (StepResult.Embed e : configTimeEmbeds) {
                LogContext.get().embed(e.getData(), e.getMimeType(), e.getName());
            }
        }
        if (config.getCompiledMask() != null && logger.isDebugEnabled()) {
            // One-line confirmation per scenario so users can verify the mask compiled
            // and is actually active. Visible only when karate.runtime is at DEBUG, so
            // it stays out of the way for normal runs.
            logger.debug("http log mask active: {}", config.getCompiledMask().describe());
        }
        // Snapshot logging state BEFORE any mid-test `configure logging` mutations so we
        // can restore in finally. The static report-buffer threshold and the Logback
        // "karate" level are global, so without this the next scenario on this thread
        // would inherit whatever this scenario's last `configure logging` left behind.
        LogContext.Snapshot loggingSnapshot = LogContext.snapshot();
        result.setStartTime(System.currentTimeMillis());
        // Use lane name for timeline if available (parallel mode), otherwise use thread info
        Suite suite = featureRuntime != null ? featureRuntime.getSuite() : null;
        String laneName = suite != null ? suite.getCurrentLaneName() : null;
        String threadName;
        if (laneName != null) {
            // Parallel mode: use lane number for consistent timeline
            threadName = laneName;
        } else {
            // Sequential mode or no lane: use thread name/ID
            Thread currentThread = Thread.currentThread();
            threadName = currentThread.getName();
            if (threadName == null || threadName.isEmpty() || "main".equals(threadName) || threadName.isBlank()) {
                threadName = "thread-" + currentThread.threadId();
            }
        }
        result.setThreadName(threadName);

        // If suite has been aborted (abortSuiteOnFailure), skip this scenario
        if (suite != null && suite.isAborted() && featureRuntime.isTopLevel()) {
            stopped = true;
            result.setAborted(true);
            result.setEndTime(System.currentTimeMillis());
            restoreLogContext(loggingSnapshot, outerLogContext, nestedCall, outerRuntime);
            return result;
        }

        boolean scenarioStarted = false;
        // Scenario-lifecycle hooks (beforeScenario/afterScenario) only fire for top-level
        // scenarios so a hook that uses karate.call() does not recurse into the called feature.
        boolean topLevel = featureRuntime == null || featureRuntime.isTopLevel();
        try {
            // Fire SCENARIO_ENTER event
            if (suite != null) {
                boolean proceed = suite.fireEvent(ScenarioRunEvent.enter(this));
                if (!proceed) {
                    // Listener returned false - skip this scenario
                    stopped = true;
                }
            }

            // Invoke beforeScenario hook (if set and listener didn't veto).
            // Note: Since this fires BEFORE Background, a beforeScenario defined inside
            // a Background has no effect on the current scenario - define it in karate-config.js.
            // A hook exception fails the scenario (same convention as a step failure); wrap
            // the hook body in try/catch to suppress. Honors continueOnStepFailure so users in
            // soft-assertion mode can still run scenario steps after a hook failure.
            if (!stopped) {
                scenarioStarted = true;
                // Skip hook in dry-run mode for non-@setup scenarios (V1 parity)
                if (topLevel && !isDryRunSkip()) {
                    Throwable hookError = invokeAndRecordHook(config.getBeforeScenario(), "beforeScenario");
                    if (hookError != null && !config.isContinueOnStepFailure()) {
                        stopped = true;
                    }
                }
            }

            // Use index-based iteration for debug step navigation support
            steps = skipBackground ? scenario.getSteps() : scenario.getStepsIncludingBackground();
            int count = steps.size();
            stepIndex = 0;
            while (stepIndex < count) {
                if (stopped || aborted
                        || (suite != null && suite.isAborted() && featureRuntime.isTopLevel())) {
                    // Mark remaining steps as skipped
                    while (stepIndex < count) {
                        StepResult sr = StepResult.skipped(steps.get(stepIndex), System.currentTimeMillis());
                        result.addStepResult(sr);
                        stepIndex++;
                    }
                    break;
                }

                currentStep = steps.get(stepIndex);
                stepIndex++;
                // In dry-run mode (parse-only), mark steps SKIPPED — not passed — without executing
                // them, so a dry-run scenario reports as not-executed (ScenarioResult.isSkipped()) rather
                // than a misleading green. A dry-run that shows green is wrong: downstream consumers (the
                // karate-ext trace graph) then derive a mapped-but-unrun requirement as NOTRUN, never a
                // false COVERED. @setup scenarios still execute normally via isDryRunSkip() returning
                // false, so dynamic outlines resolve.
                StepResult sr = isDryRunSkip()
                        ? StepResult.skipped(currentStep, System.currentTimeMillis())
                        : executor.execute(currentStep);
                result.addStepResult(sr);

                // Flush any synthetic steps an out-of-band producer appended while this step ran
                // (LogContext.step) so they land as sibling step rows right after their producing
                // step, in the report and JSONL. A failed appended step makes isFailed() true below.
                List<StepResult> appended = LogContext.get().collectPendingSteps();
                if (appended != null) {
                    for (StepResult ap : appended) {
                        result.addStepResult(ap);
                    }
                }

                if (sr.isFailed()) {
                    boolean softAssert = runStepFailurePipeline(sr);
                    if (softAssert) {
                        // Log the failure but continue execution
                        if (!hasIgnoredFailure) {
                            hasIgnoredFailure = true;
                            firstIgnoredError = sr.getError();
                        }
                    } else {
                        stopped = true;
                        error = sr.getError();
                    }
                }
            }

            // If we have ignored failures and reached the end, fail the scenario
            if (hasIgnoredFailure && error == null) {
                error = firstIgnoredError;
            }

            // Evaluate backtick scenario name for reports (e.g., `result is ${1+1}`)
            // Must happen BEFORE SCENARIO_EXIT so the event contains the evaluated name
            evaluateScenarioName();

            // Propagate scenario-level abort to the result so reporting can tag it @skipped
            if (aborted) {
                result.setAborted(true);
            }

            // Set end time BEFORE firing SCENARIO_EXIT so the event contains correct duration
            result.setEndTime(System.currentTimeMillis());

            // Capture the author-set __id (a sibling of __row/__num) as the scenario's
            // stable identity while the engine is still live — it overrides the derived
            // slug verbatim on both the SCENARIO_EXIT event and the FEATURE_EXIT payload.
            result.setStableId(resolveStableId());

            // Fire SCENARIO_EXIT event
            if (suite != null) {
                suite.fireEvent(ScenarioRunEvent.exit(this, result));
            }

        } catch (Throwable t) {
            error = t;
            stopped = true;
        } finally {
            // Note: SCENARIO_EXIT event was already fired above

            // Report (and drain) the last held perf event before teardown so Gatling receives
            // all HTTP metrics. The deferred model attaches a scenario failure to the last HTTP
            // request; logLastPerfEvent reports whether it actually did.
            String perfFailureMessage = null;
            if (result.isFailed()) {
                String display = result.getFailureMessageForDisplay();
                perfFailureMessage = display != null ? display : "scenario failed";
            }
            boolean perfFailureReported = logLastPerfEvent(perfFailureMessage);
            // If the scenario failed but no HTTP perf event carried the failure — it failed
            // before its first request, or via a non-HTTP step — synthesize one so the failure
            // still surfaces as a Gatling KO instead of vanishing. Top-level only: a called
            // feature's failure propagates to its caller, which reports it (avoids double KOs).
            if (perfFailureMessage != null && !perfFailureReported
                    && isPerfMode() && featureRuntime != null && featureRuntime.isTopLevel()) {
                reportSyntheticFailurePerfEvent(perfFailureMessage);
            }

            // Invoke afterScenario hook - runs on both pass and fail paths so teardown always executes.
            // Must run BEFORE closeChannels/closeDriver: the hook still owns the scenario's
            // resources, so a teardown body can take an error screenshot or drain a channel.
            // Running it after closeDriver() released the driver to the pool meant the hook
            // raced whichever scenario acquired that driver next under parallel execution.
            // Skipped when the scenario never actually started (SCENARIO_ENTER vetoed or suite aborted).
            // A hook exception fails the scenario (same convention as a step failure); wrap the hook
            // body in try/catch to suppress. The primary step error, if any, is preserved as the root
            // cause because the hook's fakeFailure step is appended after existing step results.
            // Skipped for called features (karate.call) so a hook that itself calls a feature
            // does not recurse - matches afterFeature / afterScenarioOutline which also gate on isTopLevel().
            if (scenarioStarted && topLevel && !isDryRunSkip()) {
                Throwable hookError = invokeAndRecordHook(config.getAfterScenario(), "afterScenario");
                if (hookError != null) {
                    // Bump end time since we added a post-scenario step
                    result.setEndTime(System.currentTimeMillis());
                }
            }

            // Close channels (kafka, grpc, etc.)
            closeChannels();

            // Close driver if it was initialized
            closeDriver();

            // Handle @fail tag - invert pass/fail result
            if (scenario.isFail()) {
                result.applyFailTag();
            }
            // endTime is set before SCENARIO_EXIT event above; set here only if not yet set (exception path)
            if (result.getEndTime() == 0) {
                result.setEndTime(System.currentTimeMillis());
            }
            // If scenario failed and abortSuiteOnFailure is set, signal suite to abort
            if (result.isFailed() && config.isAbortSuiteOnFailure()
                    && suite != null && featureRuntime.isTopLevel()) {
                suite.abort();
            }
            // Restore the global logging state we snapshotted at scenario entry so any
            // mid-test `configure logging` change does not leak into the next scenario.
            restoreLogContext(loggingSnapshot, outerLogContext, nestedCall, outerRuntime);
        }

        return result;
    }

    /**
     * Tear down this scenario's LogContext on exit. Always restores the global logging
     * snapshot (report threshold, Logback level) taken at entry. Then, for a nested
     * call/callonce, hands the caller back its original thread-local LogContext — keeping
     * the caller's mask and pretty settings alive for its remaining HTTP steps — instead
     * of clearing the thread-local and forcing a mask-less lazy-created one. Top-level
     * scenarios clear, so a pooled thread does not leak this scenario's context.
     */
    private void restoreLogContext(LogContext.Snapshot loggingSnapshot, LogContext outerLogContext,
                                   boolean nestedCall, ScenarioRuntime outerRuntime) {
        loggingSnapshot.restore();
        if (nestedCall) {
            LogContext.set(outerLogContext);
        } else {
            LogContext.clear();
        }
        // Hand the thread back to the caller's scenario (a nested call) or clear it
        // (top-level) so a pooled thread does not leak this scenario as "current".
        if (outerRuntime != null) {
            CURRENT.set(outerRuntime);
        } else {
            CURRENT.remove();
        }
    }

    /**
     * Evaluate a dynamic scenario name as a JS template literal. v1 parity — fires when
     * the name is either:
     * <ul>
     *   <li>wrapped in backticks (already a template literal), e.g. {@code `result is ${1+1}`}, or</li>
     *   <li>contains a {@code ${...}} placeholder anywhere in the name (the typical case —
     *       Gherkin authors don't naturally write backticks). The name is wrapped in
     *       backticks before eval so the placeholder resolves against the JS bindings.</li>
     * </ul>
     * If evaluation fails the original name is kept and a warning is logged to console
     * and appended to the last step's log for report visibility.
     */
    private void evaluateScenarioName() {
        String name = scenario.getName();
        if (name == null) {
            return;
        }
        String trimmed = name.trim();
        boolean wrappedByBackTick = trimmed.length() > 1
                && trimmed.charAt(0) == '`'
                && trimmed.charAt(trimmed.length() - 1) == '`';
        boolean hasPlaceholder = JS_NAME_PLACEHOLDER.matcher(trimmed).find();
        if (!wrappedByBackTick && !hasPlaceholder) {
            return;
        }
        String eval = wrappedByBackTick ? trimmed : "`" + trimmed + "`";
        try {
            Object evaluated = karate.engine.eval(eval);
            if (evaluated != null) {
                scenario.setName(evaluated.toString());
            }
        } catch (Exception e) {
            String warning = "[WARN] Failed to evaluate scenario name '" + trimmed + "': " + e.getMessage();
            logger.warn(warning);
            // Append to last step's log for report visibility
            List<StepResult> steps = result.getStepResults();
            if (!steps.isEmpty()) {
                StepResult lastStep = steps.get(steps.size() - 1);
                lastStep.appendLog(warning);
            }
        }
    }

    private static final java.util.regex.Pattern JS_NAME_PLACEHOLDER =
            java.util.regex.Pattern.compile("\\$\\{.*?}");

    // ========== Execution Context ==========

    public Object eval(String expression) {
        return karate.engine.eval(expression);
    }

    /**
     * Evaluate a JS expression with debug source information from a Gherkin step.
     * Creates an embedded Resource so the JS engine's debug points carry the
     * feature file path and correct line numbers.
     *
     * @param expression the JS expression text
     * @param step       the Gherkin step that contains this expression
     */
    public Object eval(String expression, io.karatelabs.gherkin.Step step) {
        io.karatelabs.common.Resource featureResource = step.getFeature().getResource();
        // step.getLine() is 1-indexed, convert to 0-indexed for the Resource lineOffset
        int lineOffset = step.getLine() - 1;
        io.karatelabs.common.Resource embedded = io.karatelabs.common.Resource.embedded(expression, featureResource, lineOffset);
        return karate.engine.eval(embedded);
    }

    /**
     * Evaluate text as a Gherkin step against this runtime's variable / HTTP state.
     * Accepts any Karate step prefix ({@code *}, {@code Given}, {@code When}, {@code Then},
     * {@code And}, {@code But}) and prepends {@code "* "} when no prefix is present, so a
     * plain expression like {@code "myFunc('input')"} works too. The synthesized step is
     * marked fake ({@link Step#isFake()}) so listener and interceptor callbacks are
     * skipped.
     */
    public StepResult evalAsStep(String text) {
        long startTime = System.currentTimeMillis();
        String trimmed = text == null ? "" : text.trim();
        boolean hasPrefix = false;
        for (String prefix : Step.PREFIXES) {
            if (trimmed.startsWith(prefix)) {
                hasPrefix = true;
                break;
            }
        }
        if (!hasPrefix) {
            trimmed = "* " + trimmed;
        }
        Step parsed;
        Scenario synthScenario;
        try {
            Resource resource = Resource.text("Feature:\nScenario:\n" + trimmed);
            Feature feature = Feature.read(resource);
            if (feature.getSections().isEmpty()) {
                throw new RuntimeException("no step parsed");
            }
            synthScenario = feature.getSection(0).getScenario();
            if (synthScenario == null || synthScenario.getSteps() == null || synthScenario.getSteps().isEmpty()) {
                throw new RuntimeException("no step parsed");
            }
            parsed = synthScenario.getSteps().get(0);
        } catch (Exception e) {
            Step fakeStep = new Step(scenario, -1);
            return StepResult.failed(fakeStep, startTime, 0, e);
        }
        Step fakeStep = new Step(synthScenario, -1);
        fakeStep.setLine(parsed.getLine());
        fakeStep.setEndLine(parsed.getEndLine());
        fakeStep.setPrefix(parsed.getPrefix());
        fakeStep.setKeyword(parsed.getKeyword());
        fakeStep.setText(parsed.getText());
        fakeStep.setDocString(parsed.getDocString());
        fakeStep.setDocStringLine(parsed.getDocStringLine());
        fakeStep.setTable(parsed.getTable());
        fakeStep.setComments(parsed.getComments());
        return executor.execute(fakeStep);
    }

    /**
     * Evaluate a JS docstring expression with debug source information.
     * Uses the docstring's actual content start line for precise line mapping.
     */
    public Object evalDocString(String expression, io.karatelabs.gherkin.Step step) {
        io.karatelabs.common.Resource featureResource = step.getFeature().getResource();
        int lineOffset = step.getDocStringLine();
        if (lineOffset < 0) {
            // fallback: estimate as 2 lines after the step keyword (step line + """)
            lineOffset = step.getLine(); // 1-indexed, but +1 for """ line makes it 0-indexed equivalent
        }
        io.karatelabs.common.Resource embedded = io.karatelabs.common.Resource.embedded(expression, featureResource, lineOffset);
        return karate.engine.eval(embedded);
    }

    public void setVariable(String name, Object value) {
        karate.engine.put(name, value);
    }

    /**
     * Set a "hidden" variable that is accessible in JS but excluded from getAllVariables().
     * Used for internal implementation details like responseBytes, responseType, etc.
     */
    public void setHiddenVariable(String name, Object value) {
        karate.engine.putRootBinding(name, value);
    }

    public Object getVariable(String name) {
        return karate.engine.get(name);
    }

    /**
     * The author-set {@code __id} variable as this scenario's stable identity, or
     * null when unset/blank (the slug then derives from feature path + name). Read
     * once at scenario end while the engine is still live; an opt-in, rename-proof
     * identity an author binds via a {@code * def __id} step or an {@code Examples:}
     * column (so each row pins its own id). Fail-safe: any lookup error falls back
     * to the derived slug rather than breaking event emission. Package-private so
     * {@code karate.scenario.slug} (KarateJsBase) reports the same identity.
     */
    String resolveStableId() {
        Object value;
        try {
            value = karate.engine.get("__id");
        } catch (RuntimeException e) {
            return null;
        }
        if (value == null) {
            return null;
        }
        String id = value.toString();
        return id.isBlank() ? null : id;
    }

    public Map<String, Object> getAllVariables() {
        return new LinkedHashMap<>(karate.engine.getBindings());
    }

    public io.karatelabs.js.Engine getEngine() {
        return karate.engine;
    }

    public HttpRequestBuilder getHttp() {
        return karate.http;
    }

    public KarateJs getKarate() {
        return karate;
    }

    /**
     * Whether a mock should evaluate embedded expressions found in request-derived data.
     * Off by default (request data is treated as inert data); a mock can opt in via the
     * {@code MockServer.Builder} flag or {@code configure requestExpressionsEnabled = true}.
     */
    public boolean isRequestExpressionsEnabled() {
        return requestExpressionsEnabled;
    }

    public void setRequestExpressionsEnabled(boolean requestExpressionsEnabled) {
        this.requestExpressionsEnabled = requestExpressionsEnabled;
    }

    /** Forget all request-derived values; called by the Mock Server at the start of each request. */
    public void clearRequestDerived() {
        if (!requestDerived.isEmpty()) {
            requestDerived.clear();
        }
    }

    /**
     * Deep-mark a value (and every nested Map/List) as having originated from an untrusted HTTP
     * request, so {@code processEmbeddedExpressions} will leave its {@code #(...)} strings inert.
     */
    public void markRequestDerived(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (requestDerived.add(value)) { // add returns false if already present - stops cycles
                for (Object v : map.values()) {
                    markRequestDerived(v);
                }
            }
        } else if (value instanceof List<?> list) {
            if (requestDerived.add(value)) {
                for (Object v : list) {
                    markRequestDerived(v);
                }
            }
        }
    }

    /** True if the value was marked by {@link #markRequestDerived}. Free when nothing is marked. */
    public boolean isRequestDerived(Object value) {
        return !requestDerived.isEmpty() && requestDerived.contains(value);
    }

    /**
     * Get the cookie jar for auto-sending cookies on subsequent requests.
     * Returns a map of cookie name to cookie details (value, path, domain, etc.).
     */
    public Map<String, Map<String, Object>> getCookieJar() {
        return cookieJar;
    }

    /**
     * Add cookies from responseCookies to the cookie jar for auto-send.
     * Called after each HTTP response to persist cookies.
     */
    @SuppressWarnings("unchecked")
    public void updateCookieJar(Object responseCookies) {
        if (responseCookies instanceof Map<?, ?> cookies) {
            for (Map.Entry<?, ?> entry : cookies.entrySet()) {
                String name = entry.getKey().toString();
                Object cookieData = entry.getValue();
                if (cookieData instanceof Map) {
                    cookieJar.put(name, (Map<String, Object>) cookieData);
                }
            }
        }
    }

    /**
     * Clear the cookie jar (for clearCookies functionality).
     */
    public void clearCookieJar() {
        cookieJar.clear();
    }

    public void configure(String key, Object value) {
        // Delegate to KarateConfig for type-safe storage
        boolean requiresHttpClientRebuild = config.configure(key, value);

        // Re-project the typed config onto the HTTP client when a client-relevant
        // key changed. KarateConfig is the single source of truth; the client just
        // reads typed getters off it (see HttpClient.apply).
        if (requiresHttpClientRebuild) {
            karate.client.apply(config);
        }

        // Additional side effects for specific keys
        if ("cookies".equals(key) && value == null) {
            // V1 compatibility: configure cookies = null should also clear the cookie jar
            clearCookieJar();
        }
        if ("charset".equals(key)) {
            // Handle both String charset and null (to disable auto-charset)
            karate.http.charset(value == null ? null : value.toString());
        }

        // When continueOnStepFailure is set to false, check for deferred failures
        if ("continueOnStepFailure".equals(key) && !toBoolean(value) && hasIgnoredFailure) {
            stopped = true;
            error = firstIgnoredError;
        }
    }

    private static boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    // ========== KarateJsContext Interface ==========

    @Override
    public ScenarioRuntime getRuntime() {
        return this;
    }

    @Override
    public KarateConfig getConfig() {
        return config;
    }

    public StepExecutor getExecutor() {
        return executor;
    }

    @Override
    public Resource getWorkingDir() {
        return scenario.getFeature().getResource();
    }

    // ========== State Access ==========

    public Scenario getScenario() {
        return scenario;
    }

    public FeatureRuntime getFeatureRuntime() {
        return featureRuntime;
    }

    public ScenarioResult getResult() {
        return result;
    }

    public Step getCurrentStep() {
        return currentStep;
    }

    public boolean isStopped() {
        return stopped;
    }

    public boolean isAborted() {
        return aborted;
    }

    public Throwable getError() {
        return error;
    }

    public void abort() {
        this.aborted = true;
    }

    public void stop() {
        this.stopped = true;
    }

    /**
     * Re-reads the feature from disk and updates step text in place
     * for any steps whose text changed. Returns true if at least one
     * step was updated.
     */
    public boolean hotReload() {
        if (steps == null) {
            return false;
        }
        boolean success = false;
        Feature feature = scenario.getFeature();
        Resource resource = feature.getResource();
        if (resource.getPath() == null) {
            return false; // in-memory resource: nothing to reload from disk
        }
        Feature reloaded = Feature.read(Resource.from(resource.getPath()));
        for (Step oldStep : steps) {
            Step newStep = reloaded.findStepByLine(oldStep.getLine());
            if (newStep == null) {
                continue;
            }
            String oldText = canonicalStepText(oldStep);
            String newText = canonicalStepText(newStep);
            if (!oldText.equals(newText)) {
                try {
                    oldStep.parseAndUpdateFrom(newText);
                    logger.info("hot reloaded line: {} - {}", newStep.getLine(), newText);
                    success = true;
                } catch (Exception e) {
                    logger.warn("failed to hot reload step: {}", e.getMessage());
                }
            }
        }
        return success;
    }

    private static String canonicalStepText(Step step) {
        String keyword = step.getKeyword();
        String text = step.getText() == null ? "" : step.getText();
        return keyword == null ? text : keyword + " " + text;
    }

    // ========== Debug Step Navigation ==========

    /**
     * Move execution back one step (for debugging).
     * After calling this, the previous step will be re-executed.
     */
    public void stepBack() {
        stopped = false;
        stepIndex -= 2;
        if (stepIndex < 0) {
            stepIndex = 0;
        }
    }

    /**
     * Reset to re-execute the current step (for debugging after hot reload).
     */
    public void stepReset() {
        stopped = false;
        stepIndex--;
        if (stepIndex < 0) {
            stepIndex = 0;
        }
    }

    /**
     * Continue execution from current position (after a pause).
     */
    public void stepProceed() {
        stopped = false;
    }

    /**
     * Get current step index for debugging.
     */
    public int getStepIndex() {
        return stepIndex;
    }

    /**
     * Get total number of steps.
     */
    public int getStepCount() {
        return steps != null ? steps.size() : 0;
    }

    public <T> void setDebugSupport(io.karatelabs.js.RunInterceptor<T> interceptor, io.karatelabs.js.DebugPointFactory<T> factory) {
        this.interceptor = interceptor;
        this.pointFactory = factory;
        karate.engine.setDebugSupport(interceptor, factory);
    }

    public io.karatelabs.js.RunInterceptor<?> getInterceptor() {
        return interceptor;
    }

    public io.karatelabs.js.DebugPointFactory<?> getPointFactory() {
        return pointFactory;
    }

    public void setSkipBackground(boolean skipBackground) {
        this.skipBackground = skipBackground;
    }

    public boolean isSkipBackground() {
        return skipBackground;
    }

    // ========== Failure Pipeline ==========

    /**
     * Centralised failure handler. Runs in order:
     * <ol>
     *   <li>Built-in defaults (driver {@code screenshotOnFailure}).</li>
     *   <li>User {@code configure onStepFailure} hook with an info map exposing
     *       {@code embed} / {@code proceed} / {@code stop} callbacks.</li>
     *   <li>{@link ErrorRunEvent} on the {@link Suite} bus.</li>
     * </ol>
     * Returns the resolved soft-assert decision: {@code true} → soft-assert this
     * failure, {@code false} → hard-stop. If the hook called neither
     * {@code info.proceed()} nor {@code info.stop()}, falls back to the static
     * {@code configure continueOnStepFailure} flag.
     *
     * <p>Only fires at the innermost failure: when the failed step is a
     * {@code call} whose callee already ran this pipeline (signalled by
     * {@link StepResult#hasCallResults()}), the built-in screenshot and user
     * hook are skipped to avoid duplicate work. {@link ErrorRunEvent} still
     * fires so observers always see the failure.
     *
     * <p>Built-in defaults and the user hook each catch their own exceptions
     * and warn-log; a buggy hook or a dead browser must never escalate into a
     * second scenario failure.
     */
    private boolean runStepFailurePipeline(StepResult sr) {
        boolean innermost = !sr.hasCallResults();
        if (innermost) {
            captureScreenshotOnFailure(sr);
        }
        FailureDecision decision = new FailureDecision();
        if (innermost) {
            invokeOnStepFailureHook(sr, decision);
        }
        fireErrorEvent(sr);
        return decision.resolve(config.isContinueOnStepFailure());
    }

    /**
     * Built-in {@code screenshotOnFailure}: when a driver is active and the
     * option is enabled (default true), capture a PNG and attach it directly
     * to the failed step. Any failure during capture is swallowed.
     *
     * <p>The enabled flag is resolved from the live {@code configure driver}
     * map first, falling back to the driver instance's frozen options. This
     * lets per-scenario overrides take effect under pooled-driver reuse, where
     * the driver instance's options reflect creation-time config not the
     * current scenario's.
     */
    private void captureScreenshotOnFailure(StepResult sr) {
        if (driver == null || driver.isTerminated()) {
            return;
        }
        try {
            if (!isScreenshotOnFailureEnabled()) {
                return;
            }
            byte[] bytes = driver.failureScreenshot();
            if (bytes != null && bytes.length > 0) {
                sr.addEmbed(new StepResult.Embed(bytes, "image/png", "screenshot.png"));
            }
        } catch (Throwable t) {
            logger.warn("screenshotOnFailure: capture failed, continuing: {}", t.toString());
        }
    }

    @SuppressWarnings("rawtypes")
    private boolean isScreenshotOnFailureEnabled() {
        Object cfg = config.getDriverConfig();
        if (cfg instanceof Map map && map.containsKey("screenshotOnFailure")) {
            Object v = map.get("screenshotOnFailure");
            return v == null || Boolean.parseBoolean(String.valueOf(v));
        }
        try {
            return driver.getOptions().isScreenshotOnFailure();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Invoke the {@code configure onStepFailure} hook with a single info-map
     * argument. The map exposes failure metadata plus three JS-callable
     * methods (embed / proceed / stop) bound to {@code sr} and {@code decision}.
     */
    private void invokeOnStepFailureHook(StepResult sr, FailureDecision decision) {
        Object hookRef = config.getOnStepFailure();
        if (!(hookRef instanceof JavaCallable callable)) {
            return;
        }
        try {
            Map<String, Object> info = buildStepFailureInfo(sr, decision);
            callable.call(null, new Object[]{info});
        } catch (Throwable t) {
            logger.warn("onStepFailure hook threw, continuing: {}", t.toString());
        }
    }

    private Map<String, Object> buildStepFailureInfo(StepResult sr, FailureDecision decision) {
        Map<String, Object> info = new LinkedHashMap<>();
        Throwable err = sr.getError();
        info.put("error", err == null ? null : err.getMessage());
        Step step = sr.getStep();
        if (step != null) {
            Map<String, Object> stepInfo = new LinkedHashMap<>();
            stepInfo.put("line", step.getLine());
            stepInfo.put("text", step.getText());
            stepInfo.put("prefix", step.getPrefix());
            info.put("step", stepInfo);
        }
        info.put("scenarioName", scenario == null ? null : scenario.getName());
        if (scenario != null && scenario.getFeature() != null) {
            info.put("featureName", scenario.getFeature().getName());
        }
        info.put("embed", (io.karatelabs.js.JavaInvokable) args -> {
            if (args.length < 1 || args[0] == null) {
                return null;
            }
            byte[] bytes = io.karatelabs.core.KarateJsUtils.convertToBytes(args[0]);
            String mime = args.length > 1 && args[1] != null
                    ? args[1].toString()
                    : io.karatelabs.core.KarateJsUtils.detectMimeType(args[0]);
            String name = args.length > 2 && args[2] != null ? args[2].toString() : null;
            sr.addEmbed(new StepResult.Embed(bytes, mime, name));
            return null;
        });
        info.put("proceed", (io.karatelabs.js.JavaInvokable) args -> {
            decision.proceed();
            return null;
        });
        info.put("stop", (io.karatelabs.js.JavaInvokable) args -> {
            decision.stop();
            return null;
        });
        return info;
    }

    private void fireErrorEvent(StepResult sr) {
        Suite suite = featureRuntime == null ? null : featureRuntime.getSuite();
        if (suite == null) {
            return;
        }
        // @report=false scenarios are designed to keep sensitive content out of
        // every reporting surface (HTML, JSONL). Skipping the bus fire here is
        // the cheapest way to honor that contract — the failure still counts,
        // it just doesn't surface raw error text to listeners.
        if (reportDisabled) {
            return;
        }
        try {
            suite.fireEvent(ErrorRunEvent.of(sr.getError(), this));
        } catch (Throwable t) {
            logger.warn("ErrorRunEvent dispatch failed, continuing: {}", t.toString());
        }
    }

    /**
     * Tracks whether the {@code onStepFailure} hook explicitly chose a
     * soft-assert / hard-stop outcome. Last call wins; if neither is called,
     * {@link #resolve(boolean)} returns the supplied static-config default.
     */
    private static final class FailureDecision {

        private Boolean override;

        void proceed() {
            override = Boolean.TRUE;
        }

        void stop() {
            override = Boolean.FALSE;
        }

        boolean resolve(boolean configDefault) {
            return override == null ? configDefault : override;
        }
    }

    // ========== Driver Support ==========

    /**
     * Get the browser driver, initializing it lazily if needed.
     * Requires driver to be configured via `configure driver = { ... }`.
     *
     * @return the Driver instance
     * @throws RuntimeException if driver is not configured
     */
    public Driver getDriver() {
        // user-triggered driver.quit() leaves the instance terminated but still bound to this
        // runtime; release it back to the pool before re-init or the slot leaks and subsequent
        // grid-style runs (multiple browsers per scenario) deadlock once the pool fills
        if (driver != null && driver.isTerminated()) {
            closeDriver();
        }
        if (driver == null) {
            driver = initDriver();
        }
        return driver;
    }

    /**
     * Initialize the browser driver from configuration.
     */
    @SuppressWarnings("unchecked")
    private Driver initDriver() {
        Object driverConfig = config.getDriverConfig();
        if (driverConfig == null) {
            throw new RuntimeException("driver not configured - use: * configure driver = { type: 'chrome' }");
        }

        Map<String, Object> configMap = null;
        if (driverConfig instanceof Map) {
            configMap = new java.util.HashMap<>((Map<String, Object>) driverConfig);
        } else {
            configMap = new java.util.HashMap<>();
        }

        // Check if a driver provider is configured at Suite level
        io.karatelabs.driver.DriverProvider provider = null;
        if (featureRuntime != null && featureRuntime.getSuite() != null) {
            provider = featureRuntime.getSuite().getDriverProvider();
            // Default userDataDir to <buildDir>/karate-temp/chrome-<uuid> if not explicitly set
            // Uses buildDir (target or build) as sibling to karate-reports, not inside it
            if (!configMap.containsKey("userDataDir")) {
                java.nio.file.Path tempDir = java.nio.file.Path.of(
                        io.karatelabs.common.FileUtils.getBuildDir(),
                        "karate-temp",
                        "chrome-" + java.util.UUID.randomUUID().toString().substring(0, 8));
                configMap.put("userDataDir", tempDir.toAbsolutePath().toString());
            }
        }

        // stop:false is a debug-only flag — bypass the driver pool so the instance
        // we hand out isn't recycled or auto-closed by the pool's lifecycle.
        boolean stopFlag = !Boolean.FALSE.equals(configMap.get("stop"));
        if (!stopFlag && provider != null) {
            logger.warn("configure driver = {{ stop: false }} — bypassing driver pool;"
                    + " browser will be left running on scenario exit (debug use only)");
            provider = null;
        }

        // Determine driver type and parse scope
        String driverType = (String) configMap.getOrDefault("type", "chrome");
        boolean isW3c = io.karatelabs.driver.w3c.W3cBrowserType.isW3cType(driverType);

        if (isW3c) {
            io.karatelabs.driver.w3c.W3cDriverOptions w3cOptions = io.karatelabs.driver.w3c.W3cDriverOptions.fromMap(configMap);
            this.driverScope = w3cOptions.getScope();
        } else {
            CdpDriverOptions cdpOptions = CdpDriverOptions.fromMap(configMap);
            this.driverScope = cdpOptions.getScope();
        }

        Driver driver;
        if (provider != null) {
            // Use provider to acquire driver (provider is now backend-generic)
            driver = provider.acquire(this, configMap);
            driverFromProvider = true;
            logger.info("Acquired driver from provider for scenario: {} (scope: {})", scenario.getName(), driverScope);
        } else {
            // Create driver directly (fallback, shouldn't happen with default pool)
            if (isW3c) {
                logger.info("Starting W3C WebDriver: type={}", driverType);
                driver = io.karatelabs.driver.w3c.W3cDriver.start(configMap);
            } else {
                CdpDriverOptions cdpOptions = CdpDriverOptions.fromMap(configMap);
                String wsUrl = cdpOptions.getWebSocketUrl();
                if (wsUrl != null && !wsUrl.isEmpty()) {
                    logger.info("Connecting to existing browser: {}", wsUrl);
                    driver = CdpDriver.connect(wsUrl, cdpOptions);
                } else {
                    logger.info("Starting browser with options: headless={}", cdpOptions.isHeadless());
                    driver = CdpDriver.start(cdpOptions);
                }
            }
            driverFromProvider = false;
        }

        karate.engine.putRootBinding("driver", driver);

        DriverApi.bindJsHelpers(karate.engine, driver);

        return driver;
    }

    /**
     * Register a channel for lifecycle management. Called by karate.channel().
     * The channel's afterScenario() will be called when the scenario ends.
     */
    public void registerChannel(Channel channel) {
        if (channels == null) {
            channels = new ArrayList<>();
        }
        channels.add(channel);
    }

    /**
     * Invoke a lifecycle hook (beforeScenario / afterScenario) if it is a callable.
     * Returns null on success or no-op; returns the Throwable on failure.
     * Exceptions are always logged WARN (callers decide whether to surface as a scenario failure).
     */
    private Throwable invokeHook(Object hookRef, String hookName) {
        if (!(hookRef instanceof JavaCallable callable)) {
            return null;
        }
        try {
            callable.call(null);
            return null;
        } catch (Exception e) {
            logger.warn("{} hook failed: {}", hookName, e.getMessage());
            return e;
        }
    }

    /**
     * Run a lifecycle hook and append a synthetic StepResult describing the invocation
     * to the scenario's step list so it renders in the HTML report alongside regular
     * Gherkin steps. The synthetic step carries any karate.call() results produced inside
     * the hook (via the same buffer real steps use) plus the hook's log / embed output.
     * Returns null when there is no callable hook configured or the hook passed; returns
     * the Throwable so callers can apply the existing stop/continue semantics.
     */
    private Throwable invokeAndRecordHook(Object hookRef, String hookName) {
        if (!(hookRef instanceof JavaCallable)) {
            return null;
        }
        long startTime = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        // No explicit reset of call-results / log / embed buffers here. Each step's
        // collectLogsAndEmbeds already drains those buffers as part of its normal lifecycle,
        // so by afterScenario the buffers are naturally empty. For beforeScenario specifically,
        // anything karate-config.js produced (karate.log, karate.embed, karate.call /
        // karate.callSingle) sits in those buffers — preserving them means the hook's
        // synthetic step absorbs that config-time output instead of dropping it.
        LogContext ctx = LogContext.get();

        Throwable err = invokeHook(hookRef, hookName);

        long durationNanos = System.nanoTime() - startNanos;
        StepResult.Status status = err == null ? StepResult.Status.PASSED : StepResult.Status.FAILED;
        StepResult sr = StepResult.hook(hookName, status, startTime, durationNanos, err);
        List<FeatureResult> calls = executor.drainCallResults();
        if (calls != null && !calls.isEmpty()) {
            sr.setCallResults(calls);
        }
        String log = ctx.collect();
        if (log != null && !log.isEmpty()) {
            sr.setLog(log);
        }
        List<StepResult.Embed> embeds = ctx.collectEmbeds();
        if (embeds != null) {
            for (StepResult.Embed e : embeds) {
                sr.addEmbed(e);
            }
        }
        result.addStepResult(sr);
        return err;
    }

    private void closeChannels() {
        if (channels == null) {
            return;
        }
        for (Channel channel : channels) {
            try {
                channel.afterScenario();
            } catch (Exception e) {
                logger.warn("error closing channel: {}", e.getMessage());
            }
        }
        channels = null;
    }

    /**
     * Close or release the driver if it was initialized.
     * If driver came from a provider, release it back to the provider.
     * Otherwise, close it directly.
     */
    private void closeDriver() {
        if (driver == null) {
            return;
        }

        // Don't close inherited driver - the owner (caller scenario) will close it
        if (driverInherited) {
            driver = null;
            return;
        }

        // configure driver = { stop: false } — leave the browser running for DOM
        // inspection. Skips both direct quit() and the pool's release(). Note that
        // a leaked driver here loses one slot from the pool's accounting; that's
        // accepted because stop:false is a single-scenario debug flag.
        try {
            if (!driver.getOptions().isStop()) {
                logger.warn("driver.stop=false — leaving browser running, scenario will not close it: {}",
                        scenario.getName());
                driver = null;
                return;
            }
        } catch (Exception e) {
            logger.debug("could not read driver options for stop check: {}", e.getMessage());
        }

        // Shared-scope called feature: propagate the driver up to the caller scenario
        // immediately so sibling scenarios in this same called feature can inherit it
        // via the normal inheritDriverFromCaller path. Without this, scenario N+1 in a
        // multi-scenario called feature can't see scenario N's driver — it falls
        // through to PooledDriverProvider.acquire() and deadlocks if the pool is full.
        if (featureRuntime != null && featureRuntime.isSharedScope()
                && featureRuntime.getCallerScenario() != null) {
            featureRuntime.getCallerScenario().setDriverFromCallee(this);
            driver = null;
            return;
        }

        if (driverFromProvider) {
            // Release back to provider
            io.karatelabs.driver.DriverProvider provider = null;
            if (featureRuntime != null && featureRuntime.getSuite() != null) {
                provider = featureRuntime.getSuite().getDriverProvider();
            }
            if (provider != null) {
                try {
                    provider.release(this, driver);
                    logger.debug("Released driver to provider for scenario: {}", scenario.getName());
                } catch (Exception e) {
                    logger.warn("Error releasing driver to provider: {}", e.getMessage());
                }
            }
        } else {
            // Close directly
            try {
                driver.quit();
            } catch (Exception e) {
                logger.warn("Error closing driver: {}", e.getMessage());
            }
        }
        driver = null;
    }

    /**
     * Get the driver without initializing it.
     * Returns null if driver has not been initialized.
     * Used for checking if driver exists without side effects.
     */
    public Driver getDriverIfPresent() {
        return driver;
    }

    /**
     * Get the driver scope ("scenario" or "caller").
     */
    public String getDriverScope() {
        return driverScope;
    }

    /**
     * Check if driver scope is "caller" (V1-style propagation).
     */
    public boolean isCallerScope() {
        return "caller".equals(driverScope);
    }

    /**
     * Set driver from a callee feature (upward propagation).
     * When callee has scope: "caller", driver propagates to the caller
     * so it can be used after the call returns.
     */
    public void setDriverFromCallee(ScenarioRuntime callee) {
        if (callee.driver != null && !callee.driver.isTerminated()) {
            this.driver = callee.driver;
            this.driverFromProvider = callee.driverFromProvider;
            this.driverInherited = false; // This scenario now owns the driver
            // Mark callee's driver as inherited so it doesn't close
            callee.driverInherited = true;
            logger.debug("Driver propagated from callee to caller");
            // Copy driver-related root bindings from callee
            Map<String, Object> calleeRootBindings = callee.karate.engine.getRootBindings();
            for (var entry : calleeRootBindings.entrySet()) {
                String key = entry.getKey();
                // Skip karate/read/match - they belong to this context
                if (!"karate".equals(key) && !"read".equals(key) && !"match".equals(key)) {
                    karate.engine.putRootBinding(key, entry.getValue());
                }
            }
        }
    }

    // ========== Signal/Listen Mechanism ==========

    /**
     * Set the listen result (called by karate.signal()).
     * Triggers any waiting listen() call to complete.
     */
    public void setListenResult(Object result) {
        this.listenResult = result;
        this.listenLatch.countDown();
    }

    /**
     * Wait for a signal with timeout.
     * Returns the signaled result or throws on timeout.
     */
    public Object waitForListenResult(long timeoutMs) {
        try {
            if (listenLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                Object result = listenResult;
                // Reset for potential reuse
                listenResult = null;
                listenLatch = new CountDownLatch(1);
                return result;
            }
            throw new RuntimeException("listen timed out after " + timeoutMs + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("listen interrupted", e);
        }
    }

    /**
     * Get the current listen result without waiting.
     */
    public Object getListenResult() {
        return listenResult;
    }

    // ========== Performance Testing (Gatling Integration) ==========

    /**
     * Check if performance mode is enabled.
     * When true, HTTP request timing is reported via PerfHook.
     */
    public boolean isPerfMode() {
        return featureRuntime != null
                && featureRuntime.getSuite() != null
                && featureRuntime.getSuite().isPerfMode();
    }

    /**
     * Get the PerfHook for reporting performance events.
     *
     * @return the PerfHook or null if not in perf mode
     */
    public PerfHook getPerfHook() {
        if (featureRuntime != null && featureRuntime.getSuite() != null) {
            return featureRuntime.getSuite().getPerfHook();
        }
        return null;
    }

    /**
     * Capture a performance event for the HTTP request just completed.
     * <p>
     * This implements the "deferred reporting" pattern from v1:
     * - The previous event is reported first (if any)
     * - The new event is held until the next HTTP request or scenario end
     * - This allows assertion failures to be attributed to the preceding HTTP request
     *
     * @param event the performance event to capture
     */
    public void capturePerfEvent(PerfEvent event) {
        // Report the previous event (if any) without failure
        logLastPerfEvent(null);
        // Hold this event for later
        prevPerfEvent = event;
    }

    /**
     * Log (report) the last captured performance event.
     * <p>
     * Called:
     * 1. Before each new HTTP request (to report the previous one)
     * 2. At scenario end (to report the final request, with any failure message)
     * <p>
     * If failureMessage is non-null, the event is marked as failed.
     *
     * @param failureMessage the failure message (null if successful)
     * @return true if a <em>failed</em> event was reported — i.e. the failure was carried by a
     *         real HTTP perf event. False if there was no held event or it was reported as OK.
     *         The caller uses this to decide whether a scenario failure still needs a synthetic
     *         perf event (a failure before the first HTTP request would otherwise vanish).
     */
    public boolean logLastPerfEvent(String failureMessage) {
        if (prevPerfEvent == null) {
            return false;
        }
        if (!isPerfMode()) {
            prevPerfEvent = null;
            return false;
        }
        // Mark as failed if there's a failure message
        boolean failed = failureMessage != null;
        if (failed) {
            prevPerfEvent.setFailed(true);
            prevPerfEvent.setMessage(failureMessage);
        }
        // Report to PerfHook
        PerfHook hook = getPerfHook();
        if (hook != null) {
            hook.reportPerfEvent(prevPerfEvent);
        }
        prevPerfEvent = null;
        return failed;
    }

    /**
     * Report a synthetic failed perf event for a scenario that failed without an HTTP request to
     * attach the failure to — e.g. a bad {@code path}/{@code def}/{@code match} before the first
     * call. Without this, such failures never reach Gatling's StatsEngine (no HTTP request → no
     * deferred event to flag), so they vanish from the load report's KO count even though the
     * scenario clearly failed. The event is named after the feature so these aggregate per
     * feature, distinct from the URI-pattern names used for real requests.
     */
    private void reportSyntheticFailurePerfEvent(String failureMessage) {
        PerfHook hook = getPerfHook();
        if (hook == null) {
            return;
        }
        long start = result.getStartTime();
        long end = result.getEndTime();
        if (end < start) {
            end = start;
        }
        String name = scenario.getFeature().getResource().getRelativePath();
        PerfEvent event = new PerfEvent(start, end, name, 0);
        event.setFailed(true);
        event.setMessage(failureMessage);
        hook.reportPerfEvent(event);
    }

    /**
     * Capture a custom performance event (for non-HTTP operations like DB, gRPC).
     * <p>
     * Unlike HTTP events, custom events are reported immediately.
     *
     * @param name      the event name
     * @param startTime start time in epoch milliseconds
     * @param endTime   end time in epoch milliseconds
     */
    public void captureCustomPerfEvent(String name, long startTime, long endTime) {
        if (!isPerfMode()) {
            return;
        }
        PerfEvent event = new PerfEvent(startTime, endTime, name, 200);
        PerfHook hook = getPerfHook();
        if (hook != null) {
            hook.reportPerfEvent(event);
        }
    }

}
