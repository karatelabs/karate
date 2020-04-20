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
package com.intuit.karate.junit4;

import com.intuit.karate.Resource;
import com.intuit.karate.FileUtils;
import com.intuit.karate.RunnerOptions;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.HtmlFeatureReport;
import com.intuit.karate.core.HtmlSummaryReport;
import com.intuit.karate.core.Tags;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Karate extends ParentRunner<Feature> {

    private static final Logger logger = LoggerFactory.getLogger(Karate.class);

    private final List<Feature> children;
    private final Map<String, FeatureInfo> featureMap;
    private final String tagSelector;
    private final HtmlSummaryReport summary;
    private final String targetDir;

    public Karate(Class<?> clazz) throws InitializationError, IOException {
        super(clazz);
        List<FrameworkMethod> testMethods = getTestClass().getAnnotatedMethods(Test.class);
        if (!testMethods.isEmpty()) {
            logger.warn("WARNING: there are methods annotated with '@Test', they will NOT be run when using '@RunWith(Karate.class)'");
        }
        RunnerOptions options = RunnerOptions.fromAnnotationAndSystemProperties(null, null, clazz);
        List<Resource> resources = FileUtils.scanForFeatureFiles(options.getFeatures(), clazz.getClassLoader());
        children = new ArrayList(resources.size());
        featureMap = new HashMap(resources.size());
        for (Resource resource : resources) {
            Feature feature = FeatureParser.parse(resource);
            feature.setCallName(options.getName());
            feature.setCallLine(resource.getLine());
            children.add(feature);
        }
        tagSelector = Tags.fromKarateOptionsTags(options.getTags());
        summary = new HtmlSummaryReport();
        targetDir = FileUtils.getBuildDir() + File.separator + "surefire-reports";
    }

    @Override
    public List<Feature> getChildren() {
        return children;
    }

    private static final Statement NO_OP = new Statement() {
        @Override
        public void evaluate() throws Throwable {
        }
    };

    private boolean beforeClassDone;

    @Override
    protected Statement withBeforeClasses(Statement statement) {
        if (!beforeClassDone) {
            return super.withBeforeClasses(statement);
        } else {
            return statement;
        }
    }

    @Override
    protected Description describeChild(Feature feature) {
        if (!beforeClassDone) {
            try {
                Statement statement = withBeforeClasses(NO_OP);
                statement.evaluate();
                beforeClassDone = true;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        String relativePath = feature.getRelativePath();
        // for whatever reason the junit 4 lifecycle can call describeChild() multiple times
        FeatureInfo info = featureMap.get(relativePath);
        if (info == null) {
            info = new FeatureInfo(feature, tagSelector);
            featureMap.put(relativePath, info);
        }
        return info.description;
    }

    @Override
    protected void runChild(Feature feature, RunNotifier notifier) {
        FeatureInfo info = featureMap.get(feature.getRelativePath());
        info.setNotifier(notifier);
        info.unit.run();
        FeatureResult result = info.exec.result;
        if (!result.isEmpty()) {
            result.printStats(null);
            HtmlFeatureReport.saveFeatureResult(targetDir, result);
            summary.addFeatureResult(result);
        }
    }

    @Override
    public void run(RunNotifier notifier) {
        super.run(notifier);
        summary.save(targetDir);
    }

}
