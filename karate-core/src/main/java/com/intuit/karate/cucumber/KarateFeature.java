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

import com.intuit.karate.FileUtils;
import cucumber.runtime.model.CucumberFeature;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class KarateFeature {
    
    private static final Logger logger = LoggerFactory.getLogger(KarateFeature.class);
    
    private final KarateRuntimeOptions runtimeOptions;
    private final File file;    
    private final CucumberFeature feature;       

    public CucumberFeature getFeature() {
        return feature;
    }        
    
    public KarateFeature(CucumberFeature feature, KarateRuntimeOptions karateOptions) {
        file = FileUtils.resolveIfClassPath(feature.getPath());
        this.feature = feature;
        this.runtimeOptions = karateOptions;
    }
    
    public KarateFeature(File file) {
        this.file = file;
        runtimeOptions = new KarateRuntimeOptions(file);
        feature = runtimeOptions.loadFeatures().get(0);
    }
    
    public static List<KarateFeature> loadFeatures(KarateRuntimeOptions runtimeOptions) {
        List<CucumberFeature> features = runtimeOptions.loadFeatures();
        List<KarateFeature> karateFeatures = new ArrayList(features.size());
        for (CucumberFeature feature : features) {
            KarateFeature kf = new KarateFeature(feature, runtimeOptions);
            karateFeatures.add(kf);
        }
        return karateFeatures;
    }
    
    public KarateRuntime getRuntime(KarateReporter reporter) {
        return runtimeOptions.getRuntime(file, reporter);
    }
    
    public KarateReporter getReporter(String reportDirPath) {
        File reportDir = new File(reportDirPath);
        String featurePackagePath = FileUtils.toPackageQualifiedName(feature.getPath());
        try {
            reportDir.mkdirs();
            reportDirPath = reportDir.getPath() + File.separator;
            String reportPath = reportDirPath + "TEST-" + featurePackagePath + ".xml";
            return new KarateReporter(featurePackagePath, reportPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }        
    }
    
}
