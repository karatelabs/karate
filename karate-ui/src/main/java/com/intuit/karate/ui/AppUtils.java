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
package com.intuit.karate.ui;

import com.intuit.karate.ScriptEnv;
import com.intuit.karate.cucumber.CucumberUtils;
import com.intuit.karate.cucumber.FeatureWrapper;
import com.intuit.karate.cucumber.KarateBackend;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class AppUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(AppUtils.class);
    
    private AppUtils() {
        // only static methods
    }
    
    public static FeatureBackend getFeatureBackend(String rootPath, String featurePath) {
        File rootFile = new File(rootPath);
        rootPath = rootFile.getPath(); // fix for windows
        featurePath = rootFile.getPath() + File.separator + featurePath; // fix for windows
        logger.info("feature path: {}", featurePath);
        File featureFile = new File(featurePath);
        String[] searchPaths = new String[]{rootPath};
        ScriptEnv env = ScriptEnv.init(rootPath, featureFile, searchPaths, logger);
        FeatureWrapper feature = FeatureWrapper.fromFile(featureFile, env);
        KarateBackend backend = CucumberUtils.getBackendWithGlue(env, null, null, false);
        // force bootstrap
        backend.getObjectFactory().getInstance(null);
        return new FeatureBackend(feature, backend);
    }
    
}
