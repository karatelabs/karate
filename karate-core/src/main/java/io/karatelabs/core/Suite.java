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
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.Tag;
import io.karatelabs.output.*;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
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


    // Caches (shared across features)
    private final Map<String, Object> CALLSINGLE_CACHE = new ConcurrentHashMap<>();
    private final ReentrantLock callSingleLock = new ReentrantLock();

    // Lock manager for @lock tag support (mutual exclusion across parallel scenarios)
    private final ScenarioLockManager lockManager = new ScenarioLockManager();

    // Per-thread listeners
    private final ThreadLocal<List<RunListener>> threadListeners = new ThreadLocal<>();

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

    // ========== Constructor (package-private) ==========

    /**
     * Creates an immutable Suite from a Runner.Builder.
     * This constructor is package-private - only Runner.Builder can create Suite instances.
     */
    Suite(Runner.Builder builder, int threadCount) {
        // Core configuration
        this.features = List.copyOf(builder.getResolvedFeatures());
        this.env = builder.getEnv();
        this.tagSelector = builder.getTags() != null
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

            // Fire SUITE_EXIT event
            fireEvent(SuiteRunEvent.exit(this, result));
        } finally {
            result.setEndTime(System.currentTimeMillis());

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

    private void removeJsonlListener(RunListener listener) {
        synchronized (mutableListeners) {
            mutableListeners.remove(listener);
        }
    }

    private void runSequential() {
        initThreadListeners();
        try {
            for (Feature feature : features) {
                if (isFeatureIgnored(feature)) {
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
                if (isFeatureIgnored(feature)) {
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
        return false;
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
