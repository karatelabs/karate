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

import com.intuit.karate.Runner;
import com.intuit.karate.SuiteRuntime;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.HtmlFeatureReport;
import com.intuit.karate.core.HtmlSummaryReport;
import com.intuit.karate.runtime.FeatureRuntime;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Karate extends ParentRunner<Feature> {

    private static final Logger logger = LoggerFactory.getLogger(Karate.class);

    private final SuiteRuntime suite;
    private final HtmlSummaryReport summary;
    private final JunitHook hook;

    public Karate(Class<?> clazz) throws InitializationError, IOException {
        super(clazz);
        List<FrameworkMethod> testMethods = getTestClass().getAnnotatedMethods(Test.class);
        if (!testMethods.isEmpty()) {
            logger.warn("WARNING: there are methods annotated with '@Test', they will NOT be run when using '@RunWith(Karate.class)'");
        }
        hook = new JunitHook();
        Runner.Builder builder = new Runner.Builder(clazz);
        builder.hook(hook);
        suite = new SuiteRuntime(builder);
        summary = new HtmlSummaryReport();
    }

    @Override
    public List<Feature> getChildren() {
        return suite.features;
    }

    @Override
    protected Description describeChild(Feature feature) {
        return Description.createSuiteDescription(feature.getNameForReport(), feature.getResource().getPackageQualifiedName());
    }

    @Override
    protected void runChild(Feature feature, RunNotifier notifier) {
        hook.setNotifier(notifier);
        FeatureRuntime fr = FeatureRuntime.of(suite, feature);
        fr.run();
        FeatureResult result = fr.result;
        if (!result.isEmpty()) {
            result.printStats(null);
            HtmlFeatureReport.saveFeatureResult(suite.reportDir, result);
            summary.addFeatureResult(result);
        }
    }

    @Override
    public void run(RunNotifier notifier) {
        super.run(notifier);
        summary.save(suite.reportDir);
    }

}
