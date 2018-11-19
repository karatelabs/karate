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
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.TestFactory;

public class Karate implements Iterable<DynamicNode> {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @TestFactory
    public @interface Test {

    }

    private final List<String> tags = new ArrayList();
    private final List<String> paths = new ArrayList();
    private Class clazz;

    public Karate relativeTo(Class clazz) {
        this.clazz = clazz;
        return this;
    }

    public Karate feature(String... paths) {
        this.paths.addAll(Arrays.asList(paths));
        return this;
    }

    public Karate tags(String... tags) {
        this.tags.addAll(Arrays.asList(tags));
        return this;
    }

    @Override
    public Iterator<DynamicNode> iterator() {
        RunnerOptions options = RunnerOptions.fromAnnotationAndSystemProperties(paths, tags, clazz);
        List<Resource> resources = FileUtils.scanForFeatureFiles(options.getFeatures(), clazz);
        List<Feature> features = new ArrayList(resources.size());
        for (Resource resource : resources) {
            Feature feature = FeatureParser.parse(resource);
            features.add(feature);
        }
        String tagSelector = Tags.fromCucumberOptionsTags(options.getTags());
        List<DynamicNode> list = new ArrayList<>(features.size());
        for (Feature feature : features) {
            FeatureContext featureContext = new FeatureContext(null, feature, tagSelector);
            CallContext callContext = new CallContext(null, true);
            ExecutionContext exec = new ExecutionContext(System.currentTimeMillis(), featureContext, callContext, null, null, null);
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
        return list.iterator();
    }

}
