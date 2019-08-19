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

import com.intuit.karate.Runner.Builder;
import com.intuit.karate.RunnerOptions;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author pthomas3
 */
public class Karate extends ParentRunner<JunitHook> {

    private static final Logger logger = LoggerFactory.getLogger(Karate.class);

    private final List<JunitHook> children;

    private Builder builder;

    private int threads;

    public Karate(Class<?> clazz) throws InitializationError {
        super(clazz);
        List<FrameworkMethod> testMethods = getTestClass().getAnnotatedMethods(Test.class);
        if (!testMethods.isEmpty()) {
            logger.warn("WARNING: there are methods annotated with '@Test', they will NOT be run when using '@RunWith(Karate.class)'");
        }

        JunitHook junitHook = new JunitHook(clazz);
        RunnerOptions options = RunnerOptions.fromAnnotationAndSystemProperties(clazz);
        builder = new Builder().hook(junitHook).path(options.getFeatures());
        if (options.getTags() != null)
            builder = builder.tags(options.getTags());
        threads = options.getThreads();
        children = new ArrayList<>();
        children.add(junitHook);
    }

    @Override
    public List<JunitHook> getChildren() {
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
    protected Description describeChild(JunitHook junitHook) {
        if (!beforeClassDone) {
            try {
                Statement statement = withBeforeClasses(NO_OP);
                statement.evaluate();
                beforeClassDone = true;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        return junitHook.getDescription();
    }

    @Override
    protected void runChild(JunitHook feature, RunNotifier notifier) {
        feature.setNotifier(notifier);
        builder.parallel(threads);
    }

    @Override
    public void run(RunNotifier notifier) {
        super.run(notifier);
    }

}
