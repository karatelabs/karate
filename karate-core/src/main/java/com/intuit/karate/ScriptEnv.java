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
package com.intuit.karate;

import com.intuit.karate.cucumber.KarateReporter;
import java.io.File;

/**
 *
 * @author pthomas3
 */
public class ScriptEnv {    

    public final Logger logger;
    public final String env;
    public final String tagSelector;
    public final File featureDir;
    public final String featureName;
    public final ClassLoader fileClassLoader;
    public final CallCache callCache;
    public final KarateReporter reporter;
    
    public ScriptEnv(String env, String tagSelector, File featureDir, String featureName, ClassLoader fileClassLoader, 
            CallCache callCache, Logger logger, KarateReporter reporter) {
        this.env = env;
        this.tagSelector = tagSelector;
        this.featureDir = featureDir;
        this.featureName = featureName;
        this.fileClassLoader = fileClassLoader;
        this.callCache = callCache;
        this.logger = logger;
        this.reporter = reporter;
    }
    
    public ScriptEnv(String env, String tagSelector, File featureDir, String featureName, ClassLoader fileClassLoader, KarateReporter reporter) {
        this(env, tagSelector, featureDir, featureName, fileClassLoader, new CallCache(), 
                new Logger(), reporter);
    }    
    
    public static ScriptEnv forEnvAndCurrentWorkingDir(String env) {
        return ScriptEnv.forEnvAndWorkingDir(env, new File("."));
    }
    
    public static ScriptEnv forEnvAndClass(String env, Class clazz) {
        return ScriptEnv.forEnvAndWorkingDir(env, FileUtils.getDirContaining(clazz));
    }     
    
    private static ScriptEnv forEnvAndWorkingDir(String env, File workingDir) {
        return new ScriptEnv(env, null, workingDir, null, Thread.currentThread().getContextClassLoader(), null);
    }    
    
    public static ScriptEnv forEnvAndFeatureFile(String env, File featureFile) {
        return forFeatureFile(env, null, featureFile, Thread.currentThread().getContextClassLoader());
    }  
    
    public static ScriptEnv forEnvTagsAndFeatureFile(String env, String tagSelector, File featureFile) {
        return forFeatureFile(env, tagSelector, featureFile, Thread.currentThread().getContextClassLoader());
    }    

    public static ScriptEnv forEnvAndFeatureFile(String env, File featureFile, String[] searchPaths) {
        return forFeatureFile(env, null, featureFile, FileUtils.createClassLoader(searchPaths));
    }
    
    private static ScriptEnv forFeatureFile(String env, String tagSelector, File featureFile, ClassLoader classLoader) {
        return new ScriptEnv(env, tagSelector, featureFile.getParentFile(), featureFile.getName(), classLoader, null);
    }   
    
    public ScriptEnv refresh(String in) { // immutable
        String karateEnv = StringUtils.trimToNull(in);
        if (karateEnv == null) {
            karateEnv = StringUtils.trimToNull(env);
            if (karateEnv == null) {
                karateEnv = StringUtils.trimToNull(System.getProperty(ScriptBindings.KARATE_ENV));
            }
        }
        return new ScriptEnv(karateEnv, tagSelector, featureDir, featureName, fileClassLoader, callCache, logger, reporter);
    }
    
    @Override
    public String toString() {
        return featureName + ", env: " + env + ", dir: " + featureDir;
    }        
    
}
