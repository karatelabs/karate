/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.cucumber;

import com.intuit.karate.CallContext;
import com.intuit.karate.ScriptEnv;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.RuntimeOptionsFactory;
import cucumber.runtime.UndefinedStepsTracker;
import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.xstream.LocalizedXStreams;
import java.io.File;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class KarateRuntimeOptions {

    private final ClassLoader classLoader;
    private final RuntimeOptions runtimeOptions;
    private final ResourceLoader resourceLoader;

    public KarateRuntimeOptions(Class clazz) {
        classLoader = clazz.getClassLoader();
        RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(clazz);
        runtimeOptions = runtimeOptionsFactory.create();
        resourceLoader = new MultiLoader(classLoader);
    }

    public KarateRuntimeOptions(File file) {
        classLoader = Thread.currentThread().getContextClassLoader();
        runtimeOptions = new RuntimeOptions(file.getPath());
        resourceLoader = new MultiLoader(classLoader);
    }
    
    public KarateRuntime getRuntime(File file, KarateReporter reporter) {
        File featureDir = file.getParentFile();
        ScriptEnv env = new ScriptEnv(null, featureDir, file.getName(), classLoader, reporter);
        CallContext callContext = new CallContext(null, null, false, true);
        KarateBackend backend = new KarateBackend(env, callContext);
        RuntimeGlue glue = new RuntimeGlue(new UndefinedStepsTracker(), new LocalizedXStreams(classLoader));
        return new KarateRuntime(this, backend, glue);         
    }    

    public List<CucumberFeature> loadFeatures() {
        return runtimeOptions.cucumberFeatures(resourceLoader);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    public RuntimeOptions getRuntimeOptions() {
        return runtimeOptions;
    }

}
