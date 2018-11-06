/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.Resource;
import com.intuit.karate.RunnerOptions;
import com.intuit.karate.core.*;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class Karate implements Iterable<DynamicNode> {

    private final List<Feature> features;
    private final String tagSelector;

    public static class KarateBuilder {

        private List<String> tags;
        private List<String> features;
        private Class clazz;

        public KarateBuilder tags(String ... tags) {
            if (this.tags == null) {
                this.tags = new ArrayList<>(tags.length);
            }
            this.tags.addAll(Arrays.asList(tags));
            return this;
        }

        public KarateBuilder feature(String ... features) {
            if (this.features == null) {
                this.features = new ArrayList<>(features.length);
            }
            this.features.addAll(Arrays.asList(features));
            return this;
        }

        public KarateBuilder relativeTo(Class clazz) {
            this.clazz = clazz;
            return this;
        }

        public Karate build() {
            return new Karate(features, tags, clazz);
        }

    }

    public static KarateBuilder relativeTo(Class clazz) {
        return new KarateBuilder().relativeTo(clazz);
    }

    public static KarateBuilder feature(String ... features) {
        return new KarateBuilder().feature(features);
    }

    public static KarateBuilder tags(String ... tags) {
        return new KarateBuilder().tags(tags);
    }

    private Karate(List<String> featureNames, List<String> tags, Class clazz) {
        RunnerOptions options = RunnerOptions.fromAnnotationAndSystemProperties(featureNames, tags, clazz);
        List<Resource> resources = FileUtils.scanForFeatureFiles(options.getFeatures(), clazz);
        features = new ArrayList<>(resources.size());
        for (Resource resource : resources) {
            Feature feature = FeatureParser.parse(resource);
            features.add(feature);
        }
        tagSelector = Tags.fromCucumberOptionsTags(options.getTags());
    }

    private Collection<DynamicNode> createDynamicTests() {
        List<DynamicNode> list = new ArrayList<>(features.size());
        for (Feature feature : features) {
            FeatureContext featureContext = new FeatureContext(null, feature, tagSelector);
            CallContext callContext = new CallContext(null, true);
            ExecutionContext exec = new ExecutionContext(System.currentTimeMillis(), featureContext, callContext, null, null, null, null);
            FeatureExecutionUnit unit = new FeatureExecutionUnit(exec);
            unit.run();
            exec.result.printStats(null);
            Engine.saveResultHtml(Engine.getBuildDir() + File.separator + "surefire-reports", exec.result, null);
            String testName = feature.getResource().getFileNameWithoutExtension();
            List<ScenarioResult> results = exec.result.getScenarioResults();
            List<DynamicTest> scenarios = new ArrayList<>(results.size());
            for (ScenarioResult sr : results) {
                Scenario scenario = sr.getScenario();
                String displayName = scenario.getDisplayMeta() + " " + scenario.getName();
                scenarios.add(dynamicTest(displayName, () -> {
                    if (sr.isFailed()) {
                        fail(sr.getError().getMessage());
                    }
                }));
            }
            DynamicNode node = dynamicContainer(testName, scenarios.stream());
            list.add(node);
        }
        return list;
    }

    @Override
    public Iterator<DynamicNode> iterator() {
        return createDynamicTests().iterator();
    }

}
