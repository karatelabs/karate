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

import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.RuntimeOptionsFactory;
import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.model.CucumberFeature;
import java.io.File;

/**
 *
 * @author pthomas3
 */
public class KarateFeature {
    
    private final File file;
    private final ClassLoader classLoader;
    private final RuntimeOptions runtimeOptions;
    private final ResourceLoader resourceLoader;    
    private final CucumberFeature feature;
    
    public KarateFeature(File file) {
        this.file = file;
        classLoader = Thread.currentThread().getContextClassLoader();
        resourceLoader = new MultiLoader(classLoader);
        RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(getClass());
        runtimeOptions = runtimeOptionsFactory.create();
        FeatureWrapper wrapper = FeatureWrapper.fromFile(file, classLoader);
        feature = wrapper.getFeature();        
    }
    
    private KarateReporter getKarateReporter(String reportDirPath) {
        File reportDir = new File(reportDirPath);
        try {
            reportDir.mkdirs();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String featurePath = file.getPath();
        String featurePackagePath = featurePath.replace(File.separator, ".");
        if (featurePackagePath.endsWith(".feature")) {
            featurePackagePath = featurePackagePath.substring(0, featurePackagePath.length() - 8);
        }
        try {
            reportDirPath = reportDir.getPath() + File.separator;
            String reportPath = reportDirPath + "TEST-" + featurePackagePath + ".xml";
            return new KarateReporter(featurePackagePath, reportPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }        
    }
    
//    private KarateRuntime getKarateRuntime() {
//        
//    }
    
}
