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
package com.intuit.karate;

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.http.HttpClientFactory;

import java.util.List;
import java.util.Map;

/**
 * V1 compatibility shim for {@link io.karatelabs.core.Runner}.
 * <p>
 * This class provides backward compatibility for code written against Karate v1.
 * Migrate to {@link io.karatelabs.core.Runner} for new code.
 *
 * @deprecated Use {@link io.karatelabs.core.Runner} instead.
 *             This class will be removed in a future release.
 */
@Deprecated(since = "2.0", forRemoval = true)
public final class Runner {

    private Runner() {
    }

    /**
     * Start building a test run with one or more paths.
     *
     * @deprecated Use {@link io.karatelabs.core.Runner#path(String...)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static Builder path(String... paths) {
        return new Builder().path(paths);
    }

    /**
     * Start building a test run with a list of paths.
     *
     * @deprecated Use {@link io.karatelabs.core.Runner#path(List)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static Builder path(List<String> paths) {
        return new Builder().path(paths);
    }

    /**
     * Get a new builder instance.
     *
     * @deprecated Use {@link io.karatelabs.core.Runner#builder()} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Execute a single feature with pre-defined variables.
     * <p>
     * V1 signature that converts the Class-relative path to a classpath resource path.
     *
     * @param relativeTo       the class to resolve the path relative to
     * @param path             the feature file path relative to the class
     * @param arg              variables to inject into the feature
     * @param evalKarateConfig ignored in v2 (config is always evaluated)
     * @return the result variables from the last executed scenario
     * @deprecated Use {@link io.karatelabs.core.Runner#runFeature(String, Map)} with a
     *             classpath: prefixed path instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static Map<String, Object> runFeature(Class<?> relativeTo, String path,
                                                  Map<String, Object> arg, boolean evalKarateConfig) {
        String classpathPath = toClasspathPath(relativeTo, path);
        FeatureResult result = io.karatelabs.core.Runner.runFeature(classpathPath, arg);
        return result.getResultVariables();
    }

    /**
     * Execute a single feature with pre-defined variables using a classpath path.
     *
     * @deprecated Use {@link io.karatelabs.core.Runner#runFeature(String, Map)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static Map<String, Object> runFeature(String path, Map<String, Object> arg) {
        FeatureResult result = io.karatelabs.core.Runner.runFeature(path, arg);
        return result.getResultVariables();
    }

    /**
     * Execute a single feature with pre-defined variables using a classpath path.
     *
     * @param path             the feature file path
     * @param arg              variables to inject into the feature
     * @param evalKarateConfig ignored in v2 (config is always evaluated)
     * @return the result variables from the last executed scenario
     * @deprecated Use {@link io.karatelabs.core.Runner#runFeature(String, Map)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static Map<String, Object> runFeature(String path, Map<String, Object> arg, boolean evalKarateConfig) {
        FeatureResult result = io.karatelabs.core.Runner.runFeature(path, arg);
        return result.getResultVariables();
    }

    private static String toClasspathPath(Class<?> relativeTo, String path) {
        if (path.startsWith("classpath:")) {
            return path;
        }
        String packagePath = relativeTo.getPackage().getName().replace('.', '/');
        return "classpath:" + packagePath + "/" + path;
    }

    /**
     * V1 compatibility Builder that delegates to {@link io.karatelabs.core.Runner.Builder}.
     *
     * @deprecated Use {@link io.karatelabs.core.Runner.Builder} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @SuppressWarnings("removal")
    public static class Builder {

        private final io.karatelabs.core.Runner.Builder delegate;

        Builder() {
            this.delegate = io.karatelabs.core.Runner.builder();
        }

        /**
         * Add paths to search for .feature files.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder path(String... paths) {
            delegate.path(paths);
            return this;
        }

        /**
         * Add paths to search for .feature files.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder path(List<String> paths) {
            delegate.path(paths);
            return this;
        }

        /**
         * Set tag filter expressions.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder tags(String... tags) {
            delegate.tags(tags);
            return this;
        }

        /**
         * Set tag filter expressions from a list.
         * <p>
         * V1 had this variant; v2 only has varargs.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder tags(List<String> tags) {
            if (tags != null && !tags.isEmpty()) {
                delegate.tags(tags.toArray(new String[0]));
            }
            return this;
        }

        /**
         * Set the karate environment (karate.env).
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder karateEnv(String env) {
            delegate.karateEnv(env);
            return this;
        }

        /**
         * Set the output directory for reports.
         * <p>
         * V1 name was reportDir; v2 uses outputDir.
         *
         * @deprecated Use outputDir() instead.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder reportDir(String dir) {
            delegate.outputDir(dir);
            return this;
        }

        /**
         * Set the output directory for reports.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder outputDir(String dir) {
            delegate.outputDir(dir);
            return this;
        }

        /**
         * Enable/disable backup of existing report directory.
         * <p>
         * V1 name was backupReportDir; v2 uses backupOutputDir.
         *
         * @deprecated Use backupOutputDir() instead.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder backupReportDir(boolean enabled) {
            delegate.backupOutputDir(enabled);
            return this;
        }

        /**
         * Enable/disable backup of existing output directory.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder backupOutputDir(boolean enabled) {
            delegate.backupOutputDir(enabled);
            return this;
        }

        /**
         * Set the HTTP client factory.
         * <p>
         * V1 name was clientFactory; v2 uses httpClientFactory.
         *
         * @deprecated Use httpClientFactory() instead.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder clientFactory(HttpClientFactory factory) {
            delegate.httpClientFactory(factory);
            return this;
        }

        /**
         * Set the HTTP client factory.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder httpClientFactory(HttpClientFactory factory) {
            delegate.httpClientFactory(factory);
            return this;
        }

        /**
         * Filter by scenario name (regex supported).
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder scenarioName(String name) {
            delegate.scenarioName(name);
            return this;
        }

        /**
         * Set the directory containing karate-config.js.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder configDir(String dir) {
            delegate.configDir(dir);
            return this;
        }

        /**
         * Set the working directory for relative path resolution.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder workingDir(String dir) {
            delegate.workingDir(dir);
            return this;
        }

        /**
         * Enable/disable HTML report generation.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder outputHtmlReport(boolean enabled) {
            delegate.outputHtmlReport(enabled);
            return this;
        }

        /**
         * Enable/disable Cucumber JSON report generation.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder outputCucumberJson(boolean enabled) {
            delegate.outputCucumberJson(enabled);
            return this;
        }

        /**
         * Set a system property.
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Builder systemProperty(String key, String value) {
            delegate.systemProperty(key, value);
            return this;
        }

        /**
         * Execute the tests with the specified thread count.
         *
         * @param threadCount number of threads (1 for sequential)
         * @return the test results wrapped in v1 Results class
         */
        @Deprecated(since = "2.0", forRemoval = true)
        public Results parallel(int threadCount) {
            SuiteResult suiteResult = delegate.parallel(threadCount);
            return new Results(suiteResult);
        }

        /**
         * Get the underlying v2 Builder for gradual migration.
         */
        public io.karatelabs.core.Runner.Builder toV2Builder() {
            return delegate;
        }
    }

}
