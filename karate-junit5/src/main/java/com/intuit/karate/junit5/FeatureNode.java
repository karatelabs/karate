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

import com.intuit.karate.CallContext;
import com.intuit.karate.core.ExecutionContext;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.FeatureExecutionUnit;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.HtmlFeatureReport;
import com.intuit.karate.core.HtmlSummaryReport;
import com.intuit.karate.core.ScenarioExecutionUnit;

import java.io.File;
import java.net.URI;
import java.util.Iterator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;

/**
 *
 * @author pthomas3
 */
public class FeatureNode implements Iterator<DynamicTest>, Iterable<DynamicTest> {

    public final Feature feature;
    public final ExecutionContext exec;
    public final FeatureExecutionUnit featureUnit;
    public final HtmlSummaryReport summary;
    public final String reportDir;
    public final Iterator<ScenarioExecutionUnit> iterator;

    public FeatureNode(String reportDir, HtmlSummaryReport summary, Feature feature, String tagSelector) {
        this.reportDir = reportDir;
        this.summary = summary;
        this.feature = feature;
        FeatureContext featureContext = new FeatureContext(null, feature, tagSelector);
        CallContext callContext = new CallContext(null, true);
        exec = new ExecutionContext(null, System.currentTimeMillis(), featureContext, callContext, null, null, null);
        featureUnit = new FeatureExecutionUnit(exec);
        featureUnit.init();
        iterator = featureUnit.getScenarioExecutionUnits();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public DynamicTest next() {
        ScenarioExecutionUnit unit = iterator.next();
        return DynamicTest.dynamicTest(unit.scenario.getNameForReport(), getFeatureSrcURI(unit) ,() -> {
            if (featureUnit.isSelected(unit)) {
                unit.run();
            }
            boolean failed = unit.result.isFailed();
            if (!iterator.hasNext()) {
                featureUnit.stop();
                FeatureResult result = exec.result;
                if (!result.isEmpty()) {
                    result.printStats(null);
                    HtmlFeatureReport.saveFeatureResult(reportDir, result);
                    summary.addFeatureResult(result);
                }
            }
            if (failed) {
                Assertions.fail(unit.result.getError().getMessage());
            }
        });
    }

    @Override
    public Iterator<DynamicTest> iterator() {
        return this;
    }

    // fetch src uri to point to scenario in feature file.
    public URI getFeatureSrcURI(ScenarioExecutionUnit sunit) {
        // this could be made conditional based on config - if navigating to feature file needed, then use below else return null.
        String workingDir = System.getProperty("user.dir");
        // we can use getPath as well - though that will point to feature file from compiled location i.e. target
        String featurePath = sunit.scenario.getFeature().getRelativePath().replace("classpath:", "");
        return URI.create(new File(workingDir + "/src/test/java/" + featurePath).toURI().toString() + "?line="
                + sunit.scenario.getLine());
    }

}
