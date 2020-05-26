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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

    Iterator<ScenarioExecutionUnit> iterator;

    public FeatureNode(String reportDir, HtmlSummaryReport summary, Feature feature, String tagSelector) {
        this.reportDir = reportDir;
        this.summary = summary;
        this.feature = feature;
        FeatureContext featureContext = new FeatureContext(null, feature, tagSelector);
        CallContext callContext = new CallContext(null, true);
        exec = new ExecutionContext(null, System.currentTimeMillis(), featureContext, callContext, null, null, null);
        featureUnit = new FeatureExecutionUnit(exec);
        featureUnit.init();
        List<ScenarioExecutionUnit> selected = new ArrayList();
        for (ScenarioExecutionUnit unit : featureUnit.getScenarioExecutionUnits()) {
            if (featureUnit.isSelected(unit)) { // tag filtering
                selected.add(unit);
            }
        }
        if (!selected.isEmpty()) { // make sure we trigger junit html report on last unit (after tag filtering)
            selected.get(selected.size() - 1).setLast(true);
        }
        iterator = selected.iterator();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public DynamicTest next() {
        ScenarioExecutionUnit unit = iterator.next();
        return DynamicTest.dynamicTest(unit.scenario.getNameForReport(), () -> {
            featureUnit.run(unit);
            boolean failed = unit.result.isFailed();
            if (unit.isLast()) {
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

}
