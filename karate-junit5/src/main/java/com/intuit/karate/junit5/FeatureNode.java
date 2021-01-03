/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.junit5;

import com.intuit.karate.Suite;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.core.ScenarioIterator;
import com.intuit.karate.core.ScenarioRuntime;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;

/**
 *
 * @author pthomas3
 */
public class FeatureNode implements Iterator<DynamicTest>, Iterable<DynamicTest> {

    public final List<CompletableFuture> futures;
    public final Suite suite;
    public final FeatureRuntime featureRuntime;
    private final Iterator<ScenarioRuntime> scenarios;

    public FeatureNode(Suite suite, List<CompletableFuture> futures, Feature feature, String tagSelector) {
        this.suite = suite;
        this.futures = futures;
        featureRuntime = FeatureRuntime.of(suite, feature);
        CompletableFuture future = new CompletableFuture();
        futures.add(future);
        featureRuntime.setNext(() -> future.complete(Boolean.TRUE));
        scenarios = new ScenarioIterator(featureRuntime).filterSelected().iterator();
    }

    @Override
    public boolean hasNext() {
        return scenarios.hasNext();
    }

    @Override
    public DynamicTest next() {
        ScenarioRuntime runtime = scenarios.next();
        return DynamicTest.dynamicTest(runtime.scenario.getRefIdAndName(), runtime.scenario.getUriToLineNumber(), () -> {
            if (featureRuntime.beforeHook()) { // minimal code duplication from feature-runtime
                runtime.run();
                featureRuntime.result.addResult(runtime.result);
            } else {
                runtime.logger.info("before-feature hook returned [false], aborting: ", featureRuntime);
            }
            boolean failed = runtime.result.isFailed();
            if (!scenarios.hasNext()) {
                featureRuntime.afterFeature();
                FeatureResult result = featureRuntime.result;
                if (!result.isEmpty()) {
                    suite.saveFeatureResults(result);
                }
                saveSummaryIfAllComplete();
            }
            if (failed) {
                Assertions.fail(runtime.result.getError().getMessage());
            }
        });
    }

    @Override
    public Iterator<DynamicTest> iterator() {
        return this;
    }

    private void saveSummaryIfAllComplete() {
        for (CompletableFuture cf : futures) {
            if (!cf.isDone()) {
                return;
            }
        }
        suite.buildResults();
    }

}
