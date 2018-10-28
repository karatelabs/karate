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

import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.Resource;
import com.intuit.karate.RunnerOptions;
import com.intuit.karate.core.*;
import org.junit.jupiter.api.DynamicNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import org.junit.jupiter.api.DynamicTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class Karate {

    private static final Logger logger = LoggerFactory.getLogger(Karate.class);

    private final List<Feature> features;
    private final String tagSelector;

    public static class KarateBuilder {

        private List<String> tags;
        private List<String> features;
        private Class clazz;

        public KarateBuilder tags(String ... tags) {
            if (this.tags == null) {
                this.tags = new ArrayList(tags.length);
            }
            this.tags.addAll(Arrays.asList(tags));
            return this;
        }

        public KarateBuilder feature(String ... features) {
            if (this.features == null) {
                this.features = new ArrayList(features.length);
            }
            this.features.addAll(Arrays.asList(features));
            return this;
        }

        public KarateBuilder relativeTo(Class clazz) {
            this.clazz = clazz;
            return this;
        }

        public Collection<DynamicNode> run() {
            Karate karate = new Karate(features, tags, clazz);
            return karate.run();
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
        features = new ArrayList(resources.size());
        for (Resource resource : resources) {
            Feature feature = FeatureParser.parse(resource);
            features.add(feature);
        }
        tagSelector = Tags.fromCucumberOptionsTags(options.getTags());
    }

    private Karate(List<Feature> features, String tagSelector) {
        this.features = features;
        this.tagSelector = tagSelector;
    }

    public Collection<DynamicNode> run() {
        List<DynamicNode> list = new ArrayList(features.size());
        for (Feature feature : features) {
            FeatureContext featureContext = new FeatureContext(feature, tagSelector);
            CallContext callContext = new CallContext(null, true);
            ExecutionContext exec = new ExecutionContext(System.currentTimeMillis(), featureContext, callContext, null, null, null, null);
            FeatureExecutionUnit unit = new FeatureExecutionUnit(exec);
            unit.run();
            exec.result.printStats(null);
            Engine.saveResultHtml(Engine.getBuildDir() + File.separator + "surefire-reports", exec.result, null);
            String testName = feature.getResource().getFileNameWithoutExtension();
            List<ScenarioResult> results = exec.result.getScenarioResults();
            List<DynamicTest> scenarios = new ArrayList(results.size());
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

}
