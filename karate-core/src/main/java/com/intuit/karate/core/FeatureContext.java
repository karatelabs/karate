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
package com.intuit.karate.core;

import com.intuit.karate.CallResult;
import com.intuit.karate.Logger;
import com.intuit.karate.ScriptBindings;
import com.intuit.karate.StringUtils;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class FeatureContext {

    public final String env;
    public final String tagSelector;    
    public final Feature feature;
    public final Logger logger;
    public final Path parentPath;
    public final Map<String, CallResult> callCache;
    public final String packageQualifiedName;

    private static String getEnv(String envString) {
        String temp = StringUtils.trimToNull(envString);
        if (temp == null) {
            temp = StringUtils.trimToNull(System.getProperty(ScriptBindings.KARATE_ENV));
        }
        return temp;
    }

    public FeatureContext(String envString, Feature feature, File workingDir, String tagSelector, Logger logger) {
        this.env = getEnv(envString);        
        this.tagSelector = tagSelector;
        this.feature = feature;
        this.callCache = new HashMap(1);
        this.logger = logger;        
        this.parentPath = workingDir == null ? feature.getPath().getParent() : workingDir.toPath();
        this.packageQualifiedName = workingDir == null ? feature.getResource().getPackageQualifiedName() : "";
    }
    
    public static FeatureContext forEnv(String env) {
        return FeatureContext.forWorkingDir(env, null);
    }    
    
    public static FeatureContext forLogger(Logger logger) {
        return FeatureContext.forWorkingDir(null, null, logger);
    }    
    
    public static FeatureContext forEnv() {
        return FeatureContext.forWorkingDir(null, null);
    }    
    
    public static FeatureContext forWorkingDir(File file) {
        return FeatureContext.forWorkingDir(null, file);
    }
    
    public static FeatureContext forWorkingDir(String env, File file) {
        return forWorkingDir(env, file, null);
    }
    
    public static FeatureContext forWorkingDir(String env, File file, Logger logger) {
        if (file == null) {
            file = new File("");
        }
        if (logger == null) {
            logger = new Logger();
        }
        return new FeatureContext(env, null, file, logger);
    }    

    public FeatureContext(String env, Feature feature, String tagSelector) {
        this(env, feature, null, tagSelector, new Logger());
    }
    
    public FeatureContext(String env, Feature feature, File workingDir, Logger logger) {
        this(env, feature, workingDir, null, logger);
    }    

    @Override
    public String toString() {
        return feature.getRelativePath() + ", env: " + env;
    }

}
