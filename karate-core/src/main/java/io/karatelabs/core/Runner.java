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
import io.karatelabs.gherkin.Feature;
import io.karatelabs.output.LogLevel;
import io.karatelabs.output.ResultListener;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Main entry point for running Karate tests programmatically.
 * <p>
 * Example usage:
 * <pre>
 * SuiteResult result = Runner.path("src/test/resources")
 *     .tags("@smoke")
 *     .karateEnv("dev")
 *     .parallel(5);
 * </pre>
 */
public final class Runner {

    private Runner() {
    }

    /**
     * Start building a test run with one or more paths.
     * Paths can be directories or individual .feature files.
     */
    public static Builder path(String... paths) {
        return new Builder().path(paths);
    }

    /**
     * Start building a test run with a list of paths.
     */
    public static Builder path(List<String> paths) {
        return new Builder().path(paths);
    }

    /**
     * Start building a test run with Feature objects.
     */
    public static Builder features(Feature... features) {
        return new Builder().features(features);
    }

    /**
     * Get a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Execute a single feature with pre-defined variables.
     * This is called by karate-gatling for running features with Gatling session variables.
     *
     * @param path the feature path (can be classpath: prefixed)
     * @param arg  variables to inject into the feature (available as top-level variables)
     * @return the feature result
     */
    public static FeatureResult runFeature(String path, Map<String, Object> arg) {
        return runFeature(path, arg, null);
    }

    /**
     * Run a single feature file with arguments and optional PerfHook.
     * <p>
     * This method is primarily used by Gatling integration for running features
     * with performance metric collection.
     *
     * @param path     the feature path (can be classpath: prefixed)
     * @param arg      variables to inject into the feature (available as top-level variables)
     * @param perfHook optional PerfHook for performance metric collection (Gatling)
     * @return the feature result
     */
    public static FeatureResult runFeature(String path, Map<String, Object> arg, PerfHook perfHook) {
        Resource resource = Resource.path(path);
        Feature feature = Feature.read(resource);

        // Create the suite using Builder
        Suite suite = Runner.builder()
                .features(feature)
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .buildSuite();

        // Set PerfHook if provided (for Gatling integration)
        if (perfHook != null) {
            suite.setPerfHook(perfHook);
        }

        // Run the feature with the arg map
        FeatureRuntime fr = new FeatureRuntime(suite, feature, null, null, false, arg, null);
        FeatureResult result = fr.call();

        // Capture result variables from last executed scenario (for Gatling variable chaining)
        ScenarioRuntime lastExecuted = fr.getLastExecuted();
        if (lastExecuted != null) {
            result.setResultVariables(lastExecuted.getAllVariables());
        }

        // Notify PerfHook of feature completion
        if (perfHook != null) {
            perfHook.afterFeature(result);
        }

        return result;
    }

    // ========== Builder ==========

    public static class Builder {

        private final List<String> paths = new ArrayList<>();
        private final List<Feature> features = new ArrayList<>();
        private final List<RunListener> listeners = new ArrayList<>();
        private final List<RunListenerFactory> listenerFactories = new ArrayList<>();
        private final List<ResultListener> resultListeners = new ArrayList<>();

        private String env;
        private String tags;
        private String scenarioName;
        private String configDir;
        private Path outputDir; // Default set in getOutputDir() using FileUtils.getBuildDir()
        private Path workingDir = FileUtils.WORKING_DIR.toPath();
        private boolean dryRun;
        private boolean outputHtmlReport = true;
        private boolean outputJsonLines;
        private boolean outputJunitXml;
        private boolean outputCucumberJson;
        private boolean backupOutputDir = true;
        private boolean outputConsoleSummary = true;
        private Map<String, String> systemProperties;
        private LogLevel logLevel = LogLevel.INFO;
        private io.karatelabs.driver.DriverProvider driverProvider;
        private io.karatelabs.http.HttpClientFactory httpClientFactory;
        private boolean skipTagFiltering;
        private int poolSize = -1; // -1 means auto-detect from parallel count
        private io.karatelabs.js.RunInterceptor<?> debugInterceptor;
        private io.karatelabs.js.DebugPointFactory<?> debugPointFactory;

        // Line filters: maps feature resource key to set of line numbers to run
        // Resource key is the normalized path (e.g., "src/test/features/test.feature")
        private final Map<String, Set<Integer>> lineFilters = new HashMap<>();

        // Track resolved features separately to associate them with their original paths
        private List<Feature> resolvedFeatures;

        Builder() {
        }

        /**
         * Add paths to search for .feature files.
         * Can be directories or individual files.
         */
        public Builder path(String... values) {
            paths.addAll(Arrays.asList(values));
            return this;
        }

        /**
         * Add paths to search for .feature files.
         */
        public Builder path(List<String> values) {
            if (values != null) {
                paths.addAll(values);
            }
            return this;
        }

        /**
         * Add Feature objects directly.
         */
        public Builder features(Feature... values) {
            features.addAll(Arrays.asList(values));
            return this;
        }

        /**
         * Add Feature objects directly.
         */
        public Builder features(Collection<Feature> values) {
            if (values != null) {
                features.addAll(values);
            }
            return this;
        }

        /**
         * Set the karate environment (karate.env).
         */
        public Builder karateEnv(String env) {
            this.env = env;
            return this;
        }

        /**
         * Set tag filter expression.
         * Examples: "@smoke", "~@slow", "@smoke,@fast"
         */
        public Builder tags(String... tagExpressions) {
            if (tagExpressions.length == 1) {
                this.tags = tagExpressions[0];
            } else if (tagExpressions.length > 1) {
                this.tags = String.join(",", tagExpressions);
            }
            return this;
        }

        /**
         * Filter by scenario name (regex supported).
         */
        public Builder scenarioName(String name) {
            this.scenarioName = name;
            return this;
        }

        /**
         * Set the directory containing karate-config.js.
         */
        public Builder configDir(String dir) {
            this.configDir = dir;
            return this;
        }

        /**
         * Set a system property that will be available via karate.properties in scripts.
         * Multiple properties can be set by calling this method multiple times.
         */
        public Builder systemProperty(String key, String value) {
            if (systemProperties == null) {
                systemProperties = new HashMap<>();
            }
            systemProperties.put(key, value);
            return this;
        }

        /**
         * Set the minimum log level for report capture.
         * Logs below this level will be filtered from reports.
         */
        public Builder logLevel(LogLevel level) {
            if (level != null) {
                this.logLevel = level;
            }
            return this;
        }

        /**
         * Set the minimum log level by name (case-insensitive).
         * Valid values: trace, debug, info, warn, error
         */
        public Builder logLevel(String level) {
            if (level != null) {
                this.logLevel = LogLevel.valueOf(level.toUpperCase());
            }
            return this;
        }

        /**
         * Set the output directory for reports.
         */
        public Builder outputDir(String dir) {
            if (dir != null) {
                this.outputDir = Path.of(dir);
            }
            return this;
        }

        /**
         * Set the output directory for reports.
         */
        public Builder outputDir(Path dir) {
            if (dir != null) {
                this.outputDir = dir;
            }
            return this;
        }

        /**
         * Set the working directory for relative path resolution.
         * This affects how feature file paths are displayed in reports.
         */
        public Builder workingDir(String dir) {
            if (dir != null) {
                this.workingDir = Path.of(dir).toAbsolutePath().normalize();
            }
            return this;
        }

        /**
         * Set the working directory for relative path resolution.
         * This affects how feature file paths are displayed in reports.
         */
        public Builder workingDir(Path dir) {
            if (dir != null) {
                this.workingDir = dir.toAbsolutePath().normalize();
            }
            return this;
        }

        /**
         * Enable/disable HTML report generation.
         */
        public Builder outputHtmlReport(boolean enabled) {
            this.outputHtmlReport = enabled;
            return this;
        }

        /**
         * Enable/disable JSON Lines streaming output.
         * Writes feature results to karate-results.jsonl as they complete.
         */
        public Builder outputJsonLines(boolean enabled) {
            this.outputJsonLines = enabled;
            return this;
        }

        /**
         * Enable/disable JUnit XML report generation.
         */
        public Builder outputJunitXml(boolean enabled) {
            this.outputJunitXml = enabled;
            return this;
        }

        /**
         * Enable/disable Cucumber JSON report generation.
         */
        public Builder outputCucumberJson(boolean enabled) {
            this.outputCucumberJson = enabled;
            return this;
        }

        /**
         * Enable/disable backup of existing output directory.
         * When enabled, the existing output directory is renamed with a timestamp
         * suffix (e.g., karate-reports_2025-01-15_143022) before new reports are written.
         */
        public Builder backupOutputDir(boolean enabled) {
            this.backupOutputDir = enabled;
            return this;
        }

        /**
         * Enable/disable console summary output after test execution.
         * When disabled, no summary is printed to console, but results
         * are still available in the returned SuiteResult.
         */
        public Builder outputConsoleSummary(boolean enabled) {
            this.outputConsoleSummary = enabled;
            return this;
        }

        /**
         * Enable dry-run mode (parse but don't execute).
         */
        public Builder dryRun(boolean enabled) {
            this.dryRun = enabled;
            return this;
        }

        /**
         * Add a run event listener.
         */
        public Builder listener(RunListener listener) {
            if (listener != null) {
                listeners.add(listener);
            }
            return this;
        }

        /**
         * Add multiple run event listeners.
         */
        public Builder listeners(Collection<RunListener> values) {
            if (values != null) {
                listeners.addAll(values);
            }
            return this;
        }

        /**
         * Add a run listener factory for per-thread listeners.
         * The factory's create() method is called once per execution thread.
         */
        public Builder listenerFactory(RunListenerFactory factory) {
            if (factory != null) {
                listenerFactories.add(factory);
            }
            return this;
        }

        /**
         * Add a run listener factory by class name (supports no-arg constructor).
         * Used for CLI --listener-factory option.
         * @param className fully qualified class name
         */
        public Builder listenerFactory(String className) {
            if (className != null && !className.isEmpty()) {
                try {
                    Class<?> clazz = Class.forName(className);
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    if (instance instanceof RunListenerFactory factory) {
                        listenerFactories.add(factory);
                    } else if (instance instanceof RunListener listener) {
                        // If it's a RunListener, wrap it in a factory that returns the same instance
                        listeners.add(listener);
                    } else {
                        throw new IllegalArgumentException(
                                "Class " + className + " must implement RunListenerFactory or RunListener");
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("Class not found: " + className, e);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to instantiate " + className, e);
                }
            }
            return this;
        }

        /**
         * Add a result listener for streaming test results.
         */
        public Builder resultListener(ResultListener listener) {
            if (listener != null) {
                resultListeners.add(listener);
            }
            return this;
        }

        /**
         * Add multiple result listeners.
         */
        public Builder resultListeners(Collection<ResultListener> values) {
            if (values != null) {
                resultListeners.addAll(values);
            }
            return this;
        }

        /**
         * Set a driver provider for managing browser driver lifecycle.
         * <p>
         * When set, the provider is used to acquire/release drivers for scenarios
         * instead of creating a new driver per scenario.
         * <p>
         * Example:
         * <pre>
         * Runner.path("features/")
         *     .driverProvider(new PooledDriverProvider())
         *     .parallel(4);  // Pool size auto-detected
         * </pre>
         */
        public Builder driverProvider(io.karatelabs.driver.DriverProvider provider) {
            this.driverProvider = provider;
            return this;
        }

        /**
         * Enable browser driver pooling for parallel UI tests.
         * Creates a PooledDriverProvider that reuses browser instances across scenarios.
         * <p>
         * Pool size is auto-detected from the parallel thread count.
         * <p>
         * Example:
         * <pre>
         * Runner.path("features/")
         *     .pool()       // Enable pooling
         *     .parallel(4); // Pool size = 4
         * </pre>
         */
        public Builder pool() {
            this.poolSize = -1; // Auto-detect from parallel count
            return this;
        }

        /**
         * Enable browser driver pooling with explicit pool size.
         * Creates a PooledDriverProvider that reuses browser instances across scenarios.
         * <p>
         * Example:
         * <pre>
         * Runner.path("features/")
         *     .pool(2)      // Pool of 2 browsers
         *     .parallel(4); // 4 threads share 2 browsers
         * </pre>
         *
         * @param size number of browser instances to pool
         */
        public Builder pool(int size) {
            this.poolSize = size;
            return this;
        }

        /**
         * Set the HTTP client factory for custom/mock HTTP clients.
         * When set, this factory is used instead of the default HTTP client.
         */
        public Builder httpClientFactory(io.karatelabs.http.HttpClientFactory factory) {
            this.httpClientFactory = factory;
            return this;
        }

        /**
         * Skip tag filtering (@env, @ignore) so all scenarios run regardless of tags.
         * Use this for unit tests that need to run scenarios with any tags.
         */
        public Builder skipTagFiltering(boolean skip) {
            this.skipTagFiltering = skip;
            return this;
        }

        /**
         * Set debug support with interceptor and factory.
         * Used for IDE debugging integration.
         */
        public <T> Builder debugSupport(io.karatelabs.js.RunInterceptor<T> interceptor, io.karatelabs.js.DebugPointFactory<T> factory) {
            this.debugInterceptor = interceptor;
            this.debugPointFactory = factory;
            return this;
        }

        // ========== Package-private accessors for Suite constructor ==========

        List<Feature> getResolvedFeatures() {
            if (resolvedFeatures == null) {
                resolvedFeatures = new ArrayList<>(features);
                for (String path : paths) {
                    resolveFeatures(path, resolvedFeatures, workingDir);
                }
            }
            return resolvedFeatures;
        }

        String getEnv() { return env; }
        String getTags() { return tags; }
        String getConfigDir() { return configDir; }
        Path getOutputDir() { return outputDir; }
        Path getWorkingDir() { return workingDir; }
        boolean isDryRun() { return dryRun; }
        boolean isOutputHtmlReport() { return outputHtmlReport; }
        boolean isOutputJsonLines() { return outputJsonLines; }
        boolean isOutputJunitXml() { return outputJunitXml; }
        boolean isOutputCucumberJson() { return outputCucumberJson; }
        boolean isBackupOutputDir() { return backupOutputDir; }
        boolean isOutputConsoleSummary() { return outputConsoleSummary; }
        Map<String, String> getSystemProperties() { return systemProperties; }
        List<RunListener> getListeners() { return listeners; }
        List<RunListenerFactory> getListenerFactories() { return listenerFactories; }
        List<ResultListener> getResultListeners() { return resultListeners; }
        io.karatelabs.driver.DriverProvider getDriverProvider() { return driverProvider; }
        io.karatelabs.http.HttpClientFactory getHttpClientFactory() { return httpClientFactory; }
        boolean isSkipTagFiltering() { return skipTagFiltering; }
        Map<String, Set<Integer>> getLineFilters() { return lineFilters; }
        io.karatelabs.js.RunInterceptor<?> getDebugInterceptor() { return debugInterceptor; }
        io.karatelabs.js.DebugPointFactory<?> getDebugPointFactory() { return debugPointFactory; }

        /**
         * Execute the tests with the specified thread count.
         * This is the terminal operation that runs the tests.
         *
         * @param threadCount number of threads (1 for sequential)
         * @return the test results
         */
        public SuiteResult parallel(int threadCount) {
            // Apply log level (this is a global setting)
            io.karatelabs.output.LogContext.setLogLevel(logLevel);

            Suite suite = new Suite(this, Math.max(1, threadCount));
            SuiteResult result = suite.run();

            // Print summary if enabled
            if (outputConsoleSummary) {
                result.printSummary(env, threadCount);
            }

            return result;
        }

        /**
         * Build the Suite without running it.
         * Useful for advanced scenarios where you need to configure the Suite
         * further or run it manually.
         */
        public Suite buildSuite() {
            return buildSuite(1);
        }

        /**
         * Build the Suite with specified thread count without running it.
         */
        Suite buildSuite(int threadCount) {
            // Apply log level (this is a global setting)
            io.karatelabs.output.LogContext.setLogLevel(logLevel);

            return new Suite(this, Math.max(1, threadCount));
        }

        private void resolveFeatures(String path, List<Feature> target, Path root) {
            // Parse line number from path (e.g., "test.feature:10" or "test.feature:10:15")
            String actualPath = path;
            Set<Integer> lines = new HashSet<>();

            // Check for line number suffix (path:line or path:line1:line2:...)
            // Only parse line numbers for .feature files, not directories
            // Look for ".feature:" to find where line numbers start (handles Windows drive letters like C:\)
            if (path.contains(".feature:") && !path.startsWith("classpath:")) {
                int featureExtIndex = path.indexOf(".feature:");
                int colonIndex = featureExtIndex + ".feature".length();
                String potentialPath = path.substring(0, colonIndex);
                String remainder = path.substring(colonIndex + 1);

                // Check if what follows the colon looks like line numbers
                if (remainder.matches("\\d+(:\\d+)*")) {
                    actualPath = potentialPath;
                    for (String lineStr : remainder.split(":")) {
                        try {
                            lines.add(Integer.parseInt(lineStr));
                        } catch (NumberFormatException e) {
                            // Not a valid line number, treat as original path
                            actualPath = path;
                            lines.clear();
                            break;
                        }
                    }
                }
            } else if (path.startsWith("classpath:") && path.contains(".feature:")) {
                // Handle classpath:path/to/file.feature:10 format
                int featureExtIndex = path.indexOf(".feature:");
                int colonIndex = featureExtIndex + ".feature".length();
                String potentialPath = path.substring(0, colonIndex);
                String remainder = path.substring(colonIndex + 1);

                // Check if what follows the colon looks like line numbers
                if (remainder.matches("\\d+(:\\d+)*")) {
                    actualPath = potentialPath;
                    for (String lineStr : remainder.split(":")) {
                        try {
                            lines.add(Integer.parseInt(lineStr));
                        } catch (NumberFormatException e) {
                            actualPath = path;
                            lines.clear();
                            break;
                        }
                    }
                }
            }

            File file = new File(actualPath);

            if (file.isDirectory()) {
                resolveDirectory(file, target, root);
            } else if (file.exists() && file.getName().endsWith(".feature")) {
                Feature feature = Feature.read(Resource.from(file.toPath(), root));
                target.add(feature);
                // Store line filter for this feature
                if (!lines.isEmpty()) {
                    lineFilters.put(feature.getResource().getUri().toString(), lines);
                }
            } else if (actualPath.startsWith("classpath:")) {
                // Handle classpath resources
                String classpathPath = actualPath.substring("classpath:".length());
                if (classpathPath.startsWith("/")) {
                    classpathPath = classpathPath.substring(1);
                }

                if (classpathPath.endsWith(".feature")) {
                    // Single feature file
                    Resource resource = Resource.path(actualPath);
                    Feature feature = Feature.read(resource);
                    target.add(feature);
                    // Store line filter for this feature
                    if (!lines.isEmpty()) {
                        lineFilters.put(feature.getResource().getUri().toString(), lines);
                    }
                } else {
                    // Directory - scan for .feature files
                    List<Resource> resources = Resource.scanClasspath(classpathPath, "feature", null, root);
                    for (Resource resource : resources) {
                        Feature feature = Feature.read(resource);
                        target.add(feature);
                    }
                }
            }
        }

        private void resolveDirectory(File dir, List<Feature> target, Path root) {
            File[] files = dir.listFiles();
            if (files == null) return;

            for (File file : files) {
                if (file.isDirectory()) {
                    resolveDirectory(file, target, root);
                } else if (file.getName().endsWith(".feature")) {
                    Feature feature = Feature.read(Resource.from(file.toPath(), root));
                    target.add(feature);
                }
            }
        }

        @Override
        public String toString() {
            return "Runner.Builder{paths=" + paths + ", features=" + features.size() + "}";
        }
    }

}
