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

import io.karatelabs.common.FileUtils;
import io.karatelabs.common.Resource;
import io.karatelabs.common.ResourceNotFoundException;
import io.karatelabs.driver.DriverProvider;
import io.karatelabs.driver.PooledDriverProvider;
import io.karatelabs.gherkin.ExamplesTable;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.FeatureSection;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.Tag;
import io.karatelabs.output.*;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Immutable suite configuration and execution engine.
 * <p>
 * Suite instances can only be created via {@link Runner.Builder}.
 * All configuration fields are public final for inspection.
 * <p>
 * Example usage:
 * <pre>
 * SuiteResult result = Runner.path("features/")
 *     .tags("@smoke")
 *     .parallel(4);
 * </pre>
 */
public class Suite {

    private static final Logger logger = LogContext.RUNTIME_LOGGER;

    /** Subfolder for JSON output (JSONL events, etc.) */
    public static final String KARATE_JSON_SUBFOLDER = "karate-json";

    // ========== Configuration (immutable, public final) ==========

    public final List<Feature> features;
    public final String env;
    public final String tagSelector;
    public final int threadCount;
    public final boolean parallel;
    public final boolean dryRun;
    public final String configPath;
    public final Path outputDir;
    public final Path workingDir;
    public final boolean outputHtmlReport;
    public final boolean outputJsonLines;
    public final boolean outputJunitXml;
    public final boolean outputCucumberJson;
    public final boolean backupReportDir;
    public final boolean outputConsoleSummary;
    public final Map<String, String> systemProperties;
    public final List<RunListener> listeners;
    public final List<RunListenerFactory> listenerFactories;
    public final DriverProvider driverProvider;
    public final io.karatelabs.http.HttpClientFactory httpClientFactory;
    public final boolean skipTagFiltering;
    public final Map<String, Set<Integer>> lineFilters;
    public final String scenarioName;

    // Debug support (for IDE debugging)
    public final io.karatelabs.js.RunInterceptor<?> debugInterceptor;
    public final io.karatelabs.js.DebugPointFactory<?> debugPointFactory;

    // Config resources (loaded in constructor, immutable)
    public final Resource baseResource;
    public final Resource configResource;
    public final Resource configEnvResource;

    // ========== Runtime State (mutable, private) ==========

    // Result listeners are mutable because auto-registered listeners are added in run()
    private final List<ResultListener> resultListeners;


    // Caches (shared across features within a Suite — or longer-lived when injected
    // via Runner.Builder, e.g. by karate-gatling so callSingle/callOnce survive across
    // virtual users of the same simulation).
    private final Map<String, Object> CALLSINGLE_CACHE;
    private final ReentrantLock callSingleLock;
    private final Map<String, Map<String, Object>> callOnceCacheStore;
    private final Map<String, ReentrantLock> callOnceLockStore;
    private final Map<String, Map<String, Object>> setupOnceCacheStore;

    // Lock manager for @lock tag support (mutual exclusion across parallel scenarios)
    private final ScenarioLockManager lockManager = new ScenarioLockManager();

    // Abort flag for abortSuiteOnFailure support
    private final AtomicBoolean aborted = new AtomicBoolean();

    // Per-thread listeners
    private final ThreadLocal<List<RunListener>> threadListeners = new ThreadLocal<>();

    // Channel factories registered by exts at boot (e.g. io.karatelabs.ext.grpc.GrpcExt,
    // io.karatelabs.ext.kafka.KafkaExt) via registerChannelFactory(). These take precedence
    // over the name-convention fallback in KarateJs.channel() (io.karatelabs.ext.<type>.
    // <Type>ChannelFactory), letting an ext own its channel wiring + suite/JVM-wide init from
    // onBoot() instead of relying on classpath presence alone. See docs/EXT.md § Channel factories.
    private final Map<String, ChannelFactory> channelFactories = new ConcurrentHashMap<>();

    // Shared executor and semaphore for scenario-level parallelism
    private volatile ExecutorService scenarioExecutor;
    private volatile Semaphore scenarioSemaphore;

    // Lane pool for timeline reporting
    private volatile Queue<Integer> availableLanes;
    private final ThreadLocal<Integer> currentLane = new ThreadLocal<>();

    // Results
    private SuiteResult result;

    // Performance testing hook (for Gatling integration)
    private PerfHook perfHook;

    // karate-boot.js exts, evaluated by BootLoader.loadIfPresent() at run() start.
    // Exposed via getBootBinding() so SuiteRunEvent can surface ext manifests on
    // SUITE_ENTER. Null when no karate-boot.js file is present in the workdir.
    private BootBinding bootBinding;

    // Ext globals registered by exts in their onBoot(Suite) — name → instance
    // (typically a SimpleObject). Seeded into every scenario's JS scope before
    // karate-config.js evaluates (see ScenarioRuntime.initEngine). Populated only
    // during single-threaded boot, then read-only for the parallel run.
    private final Map<String, Object> globals = new LinkedHashMap<>();

    // Ext report-asset contributions registered by exts in onBoot — name → spec.
    // Read by HtmlReportWriter at report-write time to copy ext assets and splice
    // <script>/<link> tags. Like globals: populated single-threaded at boot,
    // read-only thereafter.
    private final Map<String, ReportAssets> reportAssets = new LinkedHashMap<>();

    // KPI summary cards an ext contributes for the summary-page hero (e.g. coverage %,
    // requirements covered). Unlike reportAssets (static, boot-time), these carry post-run
    // VALUES, so an ext adds them in onShutdown() — which runs before the report listener's
    // onSuiteEnd() write (Suite.run finally-block ordering), so the values are ready in time.
    // Read by HtmlReportListener at onSuiteEnd and inlined into the summary page data.
    private final List<Map<String, Object>> summaryCards = new ArrayList<>();

    // ========== Constructor (package-private) ==========

    /**
     * Creates an immutable Suite from a Runner.Builder.
     * This constructor is package-private - only Runner.Builder can create Suite instances.
     */
    Suite(Runner.Builder builder, int threadCount) {
        // Caches — use injected stores when present (e.g. from karate-gatling protocol),
        // otherwise allocate fresh per-Suite stores.
        this.CALLSINGLE_CACHE = builder.getCallSingleCache() != null
                ? builder.getCallSingleCache() : new ConcurrentHashMap<>();
        this.callSingleLock = builder.getCallSingleLock() != null
                ? builder.getCallSingleLock() : new ReentrantLock();
        this.callOnceCacheStore = builder.getCallOnceCacheStore() != null
                ? builder.getCallOnceCacheStore() : new ConcurrentHashMap<>();
        this.callOnceLockStore = builder.getCallOnceLockStore() != null
                ? builder.getCallOnceLockStore() : new ConcurrentHashMap<>();
        this.setupOnceCacheStore = builder.getSetupOnceCacheStore() != null
                ? builder.getSetupOnceCacheStore() : new ConcurrentHashMap<>();

        // Core configuration
        this.features = List.copyOf(builder.getResolvedFeatures());
        this.env = builder.getEnv();
        this.tagSelector = builder.getTags() != null && !builder.getTags().isEmpty()
                ? TagSelector.fromKarateOptionsTags(builder.getTags())
                : null;
        this.threadCount = Math.max(1, threadCount);
        this.parallel = this.threadCount > 1;
        this.dryRun = builder.isDryRun();
        this.configPath = resolveConfigPath(builder.getConfigDir());
        this.outputDir = builder.getOutputDir() != null
                ? builder.getOutputDir()
                : Path.of(FileUtils.getBuildDir(), "karate-reports");
        this.workingDir = builder.getWorkingDir() != null
                ? builder.getWorkingDir()
                : FileUtils.WORKING_DIR.toPath();
        this.outputHtmlReport = builder.isOutputHtmlReport();
        this.outputJsonLines = builder.isOutputJsonLines();
        this.outputJunitXml = builder.isOutputJunitXml();
        this.outputCucumberJson = builder.isOutputCucumberJson();
        this.backupReportDir = builder.isBackupOutputDir();
        this.outputConsoleSummary = builder.isOutputConsoleSummary();
        this.systemProperties = builder.getSystemProperties() != null
                ? Collections.unmodifiableMap(new HashMap<>(builder.getSystemProperties()))
                : null;
        this.listeners = List.copyOf(builder.getListeners());
        this.listenerFactories = List.copyOf(builder.getListenerFactories());
        // Programmatic ext globals injected via Runner.Builder.global(...) — the run-free binding
        // seam (an embedder binds a JS global for a no-feature-file run, e.g. the loop-report
        // synthesizer's replay object). Registered like an ext's onBoot globals: visible to every
        // scenario before karate-config.js; a reserved-name collision fails the suite loud here.
        builder.getProgrammaticGlobals().forEach(this::registerGlobal);
        this.resultListeners = new ArrayList<>(builder.getResultListeners());
        // Auto-create PooledDriverProvider if none is set (default pooling behavior)
        DriverProvider configuredProvider = builder.getDriverProvider();
        if (configuredProvider != null) {
            this.driverProvider = configuredProvider;
        } else {
            // Default to pooled driver provider with pool size = thread count
            this.driverProvider = new PooledDriverProvider(threadCount);
            logger.debug("Auto-created PooledDriverProvider with pool size: {}", threadCount);
        }
        this.httpClientFactory = builder.getHttpClientFactory();
        this.skipTagFiltering = builder.isSkipTagFiltering();
        this.lineFilters = builder.getLineFilters() != null
                ? Collections.unmodifiableMap(new HashMap<>(builder.getLineFilters()))
                : Collections.emptyMap();
        // Trim to null so downstream shouldSelect() checks are simple (null = no filter)
        String rawScenarioName = builder.getScenarioName();
        this.scenarioName = rawScenarioName == null || rawScenarioName.trim().isEmpty()
                ? null
                : rawScenarioName.trim();
        this.debugInterceptor = builder.getDebugInterceptor();
        this.debugPointFactory = builder.getDebugPointFactory();

        // Load config resources (all inputs are now available)
        this.baseResource = tryLoadConfigResource(getBasePath(this.configPath), false);
        if (this.baseResource != null) {
            logger.info("{} processed", getBasePath(this.configPath));
        }
        this.configResource = tryLoadConfigResource(this.configPath, true);
        if (this.configResource != null) {
            logger.info("{} processed", this.configPath);
        }
        if (this.env != null && !this.env.isEmpty()) {
            String envConfigPath = this.configPath.replace(".js", "-" + this.env + ".js");
            this.configEnvResource = tryLoadConfigResource(envConfigPath, false);
            if (this.configEnvResource != null) {
                logger.info("{} processed", envConfigPath);
            }
        } else {
            this.configEnvResource = null;
        }
    }

    private String resolveConfigPath(String configDir) {
        if (configDir == null) {
            return "classpath:karate-config.js";
        }
        return configDir.endsWith(".js") ? configDir : configDir + "/karate-config.js";
    }

    private String getBasePath(String configPath) {
        return configPath.endsWith("karate-config.js")
                ? configPath.replace("karate-config.js", "karate-base.js")
                : "classpath:karate-base.js";
    }

    /**
     * Try to load a config file and return the Resource (for debugging support).
     */
    private Resource tryLoadConfigResource(String path, boolean warnIfMissing) {
        // Try the explicit path first
        try {
            Resource resource = Resource.path(path);
            if (resource.exists()) {
                return resource;
            }
        } catch (ResourceNotFoundException e) {
            // Not found at explicit path - continue to fallbacks
        } catch (Exception e) {
            logger.warn("Error loading config {}: {}", path, e.getMessage());
            return null;
        }

        // V2 enhancement: Try working directory as fallback
        String fileName = path;
        if (path.startsWith("classpath:")) {
            fileName = path.substring("classpath:".length());
        }
        if (!fileName.startsWith("/")) {
            try {
                Path workingDirConfig = workingDir.resolve(fileName);
                if (Files.exists(workingDirConfig)) {
                    return Resource.from(workingDirConfig);
                }
            } catch (Exception e) {
                logger.debug("Could not load config from working dir: {}", e.getMessage());
            }
        }

        if (warnIfMissing) {
            logger.trace("Config not found: {}", path);
        }
        return null;
    }

    // ========== Execution ==========

    public SuiteResult run() {
        result = new SuiteResult();
        result.setStartTime(System.currentTimeMillis());
        result.setReportDir(outputDir);
        result.setHtmlReportEnabled(outputHtmlReport);

        // Clear any per-Scenario selection cache from a prior run against the same
        // parsed Feature objects. The pre-filter below will repopulate for this run.
        clearScenarioSelectionCache();

        // Backup existing report directory if enabled
        if (backupReportDir) {
            backupReportDirIfExists();
        }

        // Auto-register HTML report listener
        if (outputHtmlReport) {
            resultListeners.add(new HtmlReportListener(outputDir, env));
        }

        // Auto-register Cucumber JSON report listener
        if (outputCucumberJson) {
            resultListeners.add(new CucumberJsonReportListener(outputDir));
        }

        // Auto-register JUnit XML report listener
        if (outputJunitXml) {
            resultListeners.add(new JunitXmlReportListener(outputDir));
        }

        // Optionally register JSON Lines event stream writer
        JsonLinesEventWriter jsonlWriter = null;
        if (outputJsonLines) {
            jsonlWriter = new JsonLinesEventWriter(outputDir, env, threadCount);
            try {
                jsonlWriter.init();
                // Add to a mutable copy for event firing
                addJsonlListener(jsonlWriter);
            } catch (Exception e) {
                logger.warn("Failed to initialize JSONL event stream: {}", e.getMessage());
                jsonlWriter = null;
            }
        }

        // Load karate-boot.js if present (workdir root, then classpath). Ext SPI
        // per K43: boot file evaluates ext-scripting only; the side effect is
        // ext registration through BootBinding.ext(...). Returns null when
        // no boot file exists — preserves the zero-cost path for projects that
        // don't use exts.
        this.bootBinding = BootLoader.loadIfPresent(this, env);

        try {
            // Notify listeners
            for (ResultListener listener : resultListeners) {
                listener.onSuiteStart(this);
            }

            // Fire SUITE_ENTER event
            fireEvent(SuiteRunEvent.enter(this));

            if (parallel && threadCount > 1) {
                runParallel();
            } else {
                runSequential();
            }

            // set end time before firing SUITE_EXIT so the event carries a valid durationMillis
            result.setEndTime(System.currentTimeMillis());

            // Fire SUITE_EXIT event
            fireEvent(SuiteRunEvent.exit(this, result));
        } finally {
            if (result.getEndTime() == 0) {
                result.setEndTime(System.currentTimeMillis());
            }

            // Shutdown driver provider if one exists
            if (driverProvider != null) {
                driverProvider.shutdown();
            }

            // Close JSONL event writer
            if (jsonlWriter != null) {
                try {
                    removeJsonlListener(jsonlWriter);
                    jsonlWriter.close();
                } catch (Exception e) {
                    logger.warn("Failed to close JSONL event stream: {}", e.getMessage());
                }
            }

            // Ext onShutdown — best-effort; exceptions are logged + dropped per K43.
            if (bootBinding != null) {
                for (Ext ext : bootBinding.getExts()) {
                    try {
                        ext.onShutdown();
                    } catch (Exception e) {
                        logger.warn("ext onShutdown failed ({}): {}",
                                ext.getClass().getName(), e.getMessage());
                    }
                    removeJsonlListener(ext);
                }
            }

            // Notify listeners
            for (ResultListener listener : resultListeners) {
                listener.onSuiteEnd(result);
            }
        }

        return result;
    }

    // Thread-safe listener management for JSONL writer
    private final List<RunListener> mutableListeners = new ArrayList<>();

    private void addJsonlListener(RunListener listener) {
        synchronized (mutableListeners) {
            mutableListeners.add(listener);
        }
    }

    /**
     * Register an {@link Ext} as a {@link RunListener} on this Suite. Called from
     * {@link BootBinding#ext(String)} during karate-boot.js evaluation, before
     * SUITE_ENTER fires. Ext will see every event from SUITE_ENTER onward.
     */
    public void registerExtListener(Ext ext) {
        addJsonlListener(ext);
    }

    /** Workdir for boot.js discovery + per-ext file resolution. */
    public Path getWorkingDir() {
        return workingDir;
    }

    /**
     * The directory of the resolved {@code karate-config.js} — for a Java project this is the
     * <b>classpath root</b> ({@code target/test-classes} / {@code src/test/resources}), since
     * {@link #configResource} is filesystem-backed even when found via {@code classpath:}. The
     * canonical anchor for resolving path <i>references</i> (an ext / boot-config path string), so a
     * project's resources resolve with no {@code classpath:} prefix.
     *
     * <p>Fallbacks for the cases where {@code karate-config.js} is absent: when an explicit
     * {@code configDir} was set on the CLI/Runner but holds no config file, the configured directory is
     * still honoured (derived from {@link #configPath}); when there is no config at all (the default
     * {@code classpath:} lookup found nothing), it falls back to {@link #workingDir}.
     */
    public Path getConfigDir() {
        if (configResource != null) {
            try {
                Path p = configResource.getPath();
                if (p != null && p.getParent() != null) {
                    return p.getParent();         // resolved config (filesystem-backed even via classpath:)
                }
            } catch (RuntimeException e) {
                // a non-filesystem resource (e.g. MemoryResource from a jar/jpackage classpath fallback)
                // has no real path — fall through to the configPath / workingDir fallbacks below
            }
        }
        // No config file found. Honour an explicit, filesystem configDir (configPath = <dir>/karate-config.js
        // or <dir>/<name>.js); a classpath: configPath we cannot map to a real dir without the file → workdir.
        if (configPath != null && !configPath.startsWith("classpath:")) {
            String fsPath = configPath.startsWith("file:") ? configPath.substring("file:".length()) : configPath;
            Path parent = Path.of(fsPath).getParent();
            if (parent != null) {
                return parent;
            }
        }
        return workingDir;
    }

    /**
     * Returns the karate-boot.js {@link BootBinding} populated during {@link #run()},
     * or {@code null} when no boot file is present in the workdir. Surfaced so
     * {@link SuiteRunEvent#toJson()} can attach {@code exts[]} manifests to
     * SUITE_ENTER — receivers read this to know which exts were active and which
     * embed names to expect.
     */
    public BootBinding getBootBinding() {
        return bootBinding;
    }

    /**
     * Register a {@link ChannelFactory} for an async channel type (e.g. {@code "grpc"},
     * {@code "kafka"}). Called by an ext from {@link Ext#onBoot(Suite)} — typically right
     * after its license gate passes — so {@code karate.channel('grpc')} resolves to the
     * ext-supplied factory instead of the {@code io.karatelabs.ext.<type>.<Type>ChannelFactory}
     * name-convention fallback. The factory instance is suite-scoped, giving the ext a home for
     * suite/JVM-wide init (shared channels, pools). Last registration for a type wins.
     */
    public void registerChannelFactory(String type, ChannelFactory factory) {
        channelFactories.put(type, factory);
    }

    /**
     * Ext-registered {@link ChannelFactory} for the given type, or {@code null} if none —
     * in which case {@code karate.channel()} falls back to the built-in name->FQCN map.
     */
    public ChannelFactory getChannelFactory(String type) {
        return channelFactories.get(type);
    }

    /**
     * Built-in JS-scope names an ext global may not shadow. Registration of a
     * colliding ext global fails the Suite loud at boot (see EXT.md § Ext globals).
     */
    // `match` is intentionally NOT reserved — core no longer ships a global `match` (assertions are the
    // Gherkin `match` keyword + `karate.match`); a consumer may register its own `match` global.
    private static final Set<String> RESERVED_GLOBAL_NAMES = Set.of("karate", "read", "driver");

    /**
     * Register an ext global under {@code name}, seeded into every scenario's JS
     * scope before {@code karate-config.js} evaluates. Called by an {@link Ext}
     * from {@link Ext#onBoot(Suite)}; the instance is typically a
     * {@link io.karatelabs.js.SimpleObject} so members cross into JS natively
     * (no reflection). Throws — and so fails the Suite at boot — when the name is
     * blank or collides with a built-in global ({@code karate}, {@code read},
     * {@code match}, {@code driver}).
     */
    public void registerGlobal(String name, Object instance) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("registerGlobal: name is null or empty");
        }
        if (RESERVED_GLOBAL_NAMES.contains(name)) {
            throw new RuntimeException("ext global '" + name + "' collides with built-in '" + name + "'");
        }
        globals.put(name, instance);
    }

    /**
     * Register a <em>per-scenario</em> ext global: the {@link ExtGlobalFactory} is
     * invoked once per scenario at seed time to mint a fresh instance bound with the
     * scenario's {@link KarateJsContext}. Prefer this over the singleton
     * {@link #registerGlobal(String, Object)} when the global carries per-scenario
     * mutable state or needs runtime / path access — see {@link ExtGlobalFactory}.
     */
    public void registerGlobal(String name, ExtGlobalFactory factory) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("registerGlobal: name is null or empty");
        }
        if (RESERVED_GLOBAL_NAMES.contains(name)) {
            throw new RuntimeException("ext global '" + name + "' collides with built-in '" + name + "'");
        }
        globals.put(name, factory);
    }

    /** The ext global registered under {@code name}, or {@code null} if none. */
    public Object getGlobal(String name) {
        return globals.get(name);
    }

    /** Immutable view of all ext globals, in registration order. */
    public Map<String, Object> getGlobals() {
        return Collections.unmodifiableMap(globals);
    }

    /**
     * Register an ext's report-asset contribution. Called by an {@link Ext} from
     * {@link Ext#onBoot(Suite)} with a fluent {@link ReportAssets} spec, e.g.
     * {@code suite.registerReportAssets(ReportAssets.named("image").js("static/ext.js"), getClass().getClassLoader())}.
     * Validates the spec against the classloader (referenced resources must exist);
     * any failure throws and so fails the Suite loud at boot (see EXT.md § Report assets).
     */
    public void registerReportAssets(ReportAssets assets, ClassLoader classLoader) {
        if (assets == null) {
            throw new IllegalArgumentException("registerReportAssets: assets is null");
        }
        assets.validateAndBind(classLoader);
        reportAssets.put(assets.name(), assets);
    }

    /** Immutable view of all registered ext report-asset specs, in registration order. */
    public Map<String, ReportAssets> getReportAssets() {
        return Collections.unmodifiableMap(reportAssets);
    }

    /**
     * Contribute one KPI summary card to the summary-page hero (a key/value tile, e.g.
     * {@code {label:'Coverage', value:'53%', sub:'8/15 endpoints'}}). Called by an {@link Ext}
     * from {@link Ext#onShutdown()} (the values are post-run), which the run loop invokes before
     * the report listener writes the summary, so cards are inlined into the page. Optional keys
     * the renderer understands: {@code sub} (a second line), {@code href} (click-through), and
     * {@code status} ({@code ok}/{@code warn}/{@code fail}, for accent colour).
     */
    public void addSummaryCard(Map<String, Object> card) {
        if (card != null) {
            summaryCards.add(card);
        }
    }

    /** The contributed KPI summary cards, in registration order (empty when no ext added any). */
    public List<Map<String, Object>> getSummaryCards() {
        return Collections.unmodifiableList(summaryCards);
    }

    private void removeJsonlListener(RunListener listener) {
        synchronized (mutableListeners) {
            mutableListeners.remove(listener);
        }
    }

    private void runSequential() {
        initThreadListeners();
        try {
            for (Feature feature : features) {
                if (isAborted() || isFeatureIgnored(feature)) {
                    continue;
                }
                FeatureResult featureResult = runFeatureSafely(feature);
                result.addFeatureResult(featureResult);
                if (outputConsoleSummary) {
                    featureResult.printSummary();
                }
            }
        } finally {
            cleanupThreadListeners();
        }
    }

    private FeatureResult runFeatureSafely(Feature feature) {
        long startTime = System.currentTimeMillis();
        try {
            FeatureRuntime fr = new FeatureRuntime(this, feature);
            return fr.call();
        } catch (Exception e) {
            logger.error("Unexpected error running feature '{}': {}", feature.getName(), e.getMessage(), e);
            return FeatureResult.fromException(feature, e, startTime);
        }
    }

    private void runParallel() {
        // Initialize ALL parallel infrastructure BEFORE creating executor
        availableLanes = new ConcurrentLinkedQueue<>();
        for (int i = 1; i <= threadCount; i++) {
            availableLanes.add(i);
        }
        scenarioSemaphore = new Semaphore(threadCount);
        logger.info("Parallel execution initialized: threadCount={}, semaphore permits={}",
                threadCount, scenarioSemaphore.availablePermits());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            scenarioExecutor = executor;
            List<Future<FeatureResult>> futures = new ArrayList<>();

            for (Feature feature : features) {
                if (isAborted() || isFeatureIgnored(feature)) {
                    continue;
                }
                Future<FeatureResult> future = executor.submit(() -> {
                    initThreadListeners();
                    try {
                        FeatureResult featureResult = runFeatureSafely(feature);
                        if (outputConsoleSummary) {
                            featureResult.printSummary();
                        }
                        return featureResult;
                    } finally {
                        cleanupThreadListeners();
                    }
                });
                futures.add(future);
            }

            // Collect results
            for (Future<FeatureResult> future : futures) {
                try {
                    FeatureResult featureResult = future.get();
                    result.addFeatureResult(featureResult);
                } catch (Exception e) {
                    logger.error("Unexpected error collecting feature result: {}", e.getMessage(), e);
                }
            }
        } finally {
            scenarioExecutor = null;
            scenarioSemaphore = null;
        }
    }

    private boolean isFeatureIgnored(Feature feature) {
        for (Tag tag : feature.getTags()) {
            if (Tag.IGNORE.equals(tag.getName())) {
                return true;
            }
        }
        return allSectionsExcluded(feature);
    }

    /**
     * Returns true when no section in the feature can match the suite's filters,
     * so running the feature would only produce an empty FeatureResult. Without this
     * pre-filter, features whose feature-level tags (e.g. {@code @external}) exclude
     * every scenario via a selector like {@code ~@external} still get added to the
     * suite result with zero scenarios, showing up as green "passed" rows in the HTML
     * summary and inflating the feature count. The check is sound: it iterates every
     * section and returns true only if none of them could pass the selector.
     * <p>
     * Short-circuits when no tag selector is set — {@code @ignore}/{@code @env} at the
     * scenario level still produce empty FeatureResults under the status quo and the
     * bug report explicitly asked to preserve that.
     * <p>
     * Never pre-excludes when a bypass filter is active — line filters, scenario name
     * filters, and {@code skipTagFiltering} all have their own selection semantics in
     * {@link FeatureRuntime} that bypass tag selection.
     * <p>
     * For persistent (non-outline) scenarios, the evaluation result is cached on the
     * {@link Scenario} via {@link Scenario#setSelected(Boolean)} so that
     * {@link FeatureRuntime#shouldSelect} can reuse it instead of re-evaluating.
     */
    private boolean allSectionsExcluded(Feature feature) {
        if (tagSelector == null) {
            return false;
        }
        if (skipTagFiltering) {
            return false;
        }
        if (scenarioName != null) {
            return false;
        }
        if (!lineFilters.isEmpty()) {
            String featureUri = feature.getResource().getUri().toString();
            Set<Integer> lines = lineFilters.get(featureUri);
            if (lines != null && !lines.isEmpty()) {
                return false;
            }
        }
        List<FeatureSection> sections = feature.getSections();
        if (sections == null || sections.isEmpty()) {
            return false;
        }
        List<Tag> featureTags = feature.getTags();
        // Full iteration (no early exit) so every non-outline Scenario's selection
        // gets cached — FeatureRuntime reads the cache in shouldSelect and avoids
        // a second TagSelector.evaluate. Net evaluations drop from ~1.5× per
        // scenario to exactly 1× for features that run.
        boolean anyPassed = false;
        for (FeatureSection section : sections) {
            if (sectionCanMatch(section, featureTags)) {
                anyPassed = true;
            }
        }
        return !anyPassed;
    }

    private boolean sectionCanMatch(FeatureSection section, List<Tag> featureTags) {
        if (section.isOutline()) {
            List<Tag> base = mergeTags(featureTags, section.getScenarioOutline().getTags());
            List<ExamplesTable> tables = section.getScenarioOutline().getExamplesTables();
            if (tables != null && !tables.isEmpty()) {
                // Examples tables can carry their own tags that only apply to rows in
                // that table — if any table could make the selector pass, we can't skip.
                // Row-level scenarios are generated fresh per run; no cache to populate.
                boolean anyTablePasses = false;
                for (ExamplesTable table : tables) {
                    if (tagSelectorPasses(mergeTags(base, table.getTags()))) {
                        anyTablePasses = true;
                    }
                }
                return anyTablePasses;
            }
            return tagSelectorPasses(base);
        }
        Scenario scenario = section.getScenario();
        boolean passes = tagSelectorPasses(mergeTags(featureTags, scenario.getTags()));
        scenario.setSelected(passes);
        return passes;
    }

    private boolean tagSelectorPasses(List<Tag> tags) {
        return new TagSelector(tags).evaluate(tagSelector, env);
    }

    private void clearScenarioSelectionCache() {
        for (Feature feature : features) {
            List<FeatureSection> sections = feature.getSections();
            if (sections == null) continue;
            for (FeatureSection section : sections) {
                if (!section.isOutline() && section.getScenario() != null) {
                    section.getScenario().setSelected(null);
                }
            }
        }
    }

    private static List<Tag> mergeTags(List<Tag> a, List<Tag> b) {
        if (a == null || a.isEmpty()) {
            return b == null ? Collections.emptyList() : b;
        }
        if (b == null || b.isEmpty()) {
            return a;
        }
        List<Tag> merged = new ArrayList<>(a.size() + b.size());
        merged.addAll(a);
        merged.addAll(b);
        return merged;
    }

    // ========== Event System ==========

    void initThreadListeners() {
        if (listenerFactories.isEmpty()) {
            return;
        }
        List<RunListener> perThread = new ArrayList<>();
        for (RunListenerFactory factory : listenerFactories) {
            perThread.add(factory.create());
        }
        threadListeners.set(perThread);
    }

    void cleanupThreadListeners() {
        threadListeners.remove();
    }

    public boolean fireEvent(RunEvent event) {
        boolean proceed = true;

        // Global listeners (immutable list)
        for (RunListener listener : listeners) {
            if (!listener.onEvent(event)) {
                proceed = false;
            }
        }

        // Mutable listeners (JSONL writer)
        synchronized (mutableListeners) {
            for (RunListener listener : mutableListeners) {
                if (!listener.onEvent(event)) {
                    proceed = false;
                }
            }
        }

        // Per-thread listeners
        List<RunListener> perThread = threadListeners.get();
        if (perThread != null) {
            for (RunListener listener : perThread) {
                if (!listener.onEvent(event)) {
                    proceed = false;
                }
            }
        }

        return proceed;
    }

    private static final DateTimeFormatter BACKUP_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private void backupReportDirIfExists() {
        if (!Files.exists(outputDir)) {
            return;
        }
        String timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT);
        String baseName = outputDir.getFileName() + "_" + timestamp;
        Path backupPath = outputDir.resolveSibling(baseName);
        int suffix = 1;
        while (Files.exists(backupPath)) {
            backupPath = outputDir.resolveSibling(baseName + "_" + suffix);
            suffix++;
        }
        try {
            Files.move(outputDir, backupPath);
            logger.info("backed up existing output to: {}", backupPath);
        } catch (Exception e) {
            logger.warn("Failed to backup existing dir '{}': {}", outputDir, e.getMessage());
        }
    }

    // ========== Accessors (for private fields) ==========

    public String getOutputDir() {
        return outputDir.toString();
    }

    public Map<String, Object> getCallSingleCache() {
        return CALLSINGLE_CACHE;
    }

    public ReentrantLock getCallSingleLock() {
        return callSingleLock;
    }

    /**
     * Get the callOnce cache for the given feature key (its resource URI). The cache
     * is created on first access and reused across all FeatureRuntimes for the same
     * feature within this Suite — and across Suites when the underlying store was
     * injected via Runner.Builder.
     */
    public Map<String, Object> getCallOnceCache(String featureKey) {
        return callOnceCacheStore.computeIfAbsent(featureKey, k -> new ConcurrentHashMap<>());
    }

    public ReentrantLock getCallOnceLock(String featureKey) {
        return callOnceLockStore.computeIfAbsent(featureKey, k -> new ReentrantLock());
    }

    public Map<String, Object> getSetupOnceCache(String featureKey) {
        return setupOnceCacheStore.computeIfAbsent(featureKey, k -> new ConcurrentHashMap<>());
    }

    public ScenarioLockManager getLockManager() {
        return lockManager;
    }

    public ExecutorService getScenarioExecutor() {
        return scenarioExecutor;
    }

    public Semaphore getScenarioSemaphore() {
        return scenarioSemaphore;
    }

    public void acquireLane() {
        if (availableLanes != null) {
            Integer lane = availableLanes.poll();
            if (lane != null) {
                currentLane.set(lane);
            }
        }
    }

    public void releaseLane() {
        if (availableLanes != null) {
            Integer lane = currentLane.get();
            if (lane != null) {
                currentLane.remove();
                availableLanes.add(lane);
            }
        }
    }

    public String getCurrentLaneName() {
        Integer lane = currentLane.get();
        return lane != null ? String.valueOf(lane) : null;
    }

    public void abort() {
        aborted.set(true);
    }

    public boolean isAborted() {
        return aborted.get();
    }

    public SuiteResult getResult() {
        return result;
    }

    public List<ResultListener> getResultListeners() {
        return resultListeners;
    }

    /**
     * Get system properties for karate.properties.
     */
    public Map<String, String> getSystemProperties() {
        if (systemProperties == null) {
            Map<String, String> props = new HashMap<>();
            System.getProperties().forEach((k, v) -> props.put(k.toString(), v.toString()));
            return props;
        }
        Map<String, String> merged = new HashMap<>();
        System.getProperties().forEach((k, v) -> merged.put(k.toString(), v.toString()));
        merged.putAll(systemProperties);
        return merged;
    }

    public DriverProvider getDriverProvider() {
        return driverProvider;
    }

    public PerfHook getPerfHook() {
        return perfHook;
    }

    /**
     * Set the performance hook for Gatling integration.
     * This is the only mutable setter, needed for Gatling's deferred perfHook setup.
     */
    void setPerfHook(PerfHook hook) {
        this.perfHook = hook;
    }

    public io.karatelabs.http.HttpClientFactory getHttpClientFactory() {
        return httpClientFactory;
    }

    public boolean isPerfMode() {
        return perfHook != null;
    }

    public boolean isSkipTagFiltering() {
        return skipTagFiltering;
    }

}
