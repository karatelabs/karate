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

import io.karatelabs.core.SuiteResult;

/**
 * V1 compatibility shim for {@link SuiteResult}.
 * <p>
 * This class provides backward compatibility for code written against Karate v1.
 * Migrate to {@link SuiteResult} for new code.
 *
 * @deprecated Use {@link io.karatelabs.core.SuiteResult} instead.
 *             This class will be removed in a future release.
 */
@Deprecated(since = "2.0", forRemoval = true)
public class Results {

    private final SuiteResult delegate;

    public Results(SuiteResult delegate) {
        this.delegate = delegate;
    }

    /**
     * Get the number of failed scenarios.
     *
     * @deprecated Use {@link SuiteResult#getScenarioFailedCount()} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public int getFailCount() {
        return delegate.getScenarioFailedCount();
    }

    /**
     * Get the number of passed scenarios.
     *
     * @deprecated Use {@link SuiteResult#getScenarioPassedCount()} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public int getPassCount() {
        return delegate.getScenarioPassedCount();
    }

    /**
     * Get the total number of scenarios.
     *
     * @deprecated Use {@link SuiteResult#getScenarioCount()} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public int getScenarioCount() {
        return delegate.getScenarioCount();
    }

    /**
     * Get the total number of features.
     *
     * @deprecated Use {@link SuiteResult#getFeatureCount()} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public int getFeatureCount() {
        return delegate.getFeatureCount();
    }

    /**
     * Get all error messages joined by newlines.
     *
     * @deprecated Use {@link SuiteResult#getErrors()} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public String getErrorMessages() {
        return String.join("\n", delegate.getErrors());
    }

    /**
     * Get the report directory path.
     * <p>
     * Note: In v2, the report directory is configured via {@link Runner.Builder#outputDir(String)}
     * and defaults to "target/karate-reports".
     *
     * @deprecated Configure output directory via Runner.Builder.outputDir() instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public String getReportDir() {
        return "target/karate-reports";
    }

    /**
     * Get the underlying v2 SuiteResult for gradual migration.
     */
    public SuiteResult toSuiteResult() {
        return delegate;
    }

}
