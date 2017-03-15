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

import java.io.File;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ScriptEnv {
    
    private static final Logger logger = LoggerFactory.getLogger(ScriptEnv.class);
    
    public final boolean test; // if test mode, skip some init for faster unit tests
    public final String env;
    public final File featureDir;
    public final String featureName;
    public final ClassLoader fileClassLoader;    
    
    public ScriptEnv(boolean test, String env, File featureDir, String featureName, ClassLoader fileClassLoader) {
        this.env = env;
        this.test = test;
        this.featureDir = featureDir;
        this.featureName = featureName;
        this.fileClassLoader = fileClassLoader;
    }
    
    public static ScriptEnv init(File featureDir, ClassLoader classLoader) {
        return new ScriptEnv(false, null, featureDir, null, classLoader);
    }
    
    public static ScriptEnv test(String env, File featureDir) {
        return new ScriptEnv(true, env, featureDir, null, Thread.currentThread().getContextClassLoader());
    }    
    
    public ScriptEnv refresh() { // immutable
        String karateEnv = StringUtils.trimToNull(env);
        if (karateEnv == null) {
            karateEnv = StringUtils.trimToNull(System.getProperty("karate.env"));
            logger.debug("obtained 'karate.env' from system properties: {}", karateEnv);
        }
        return new ScriptEnv(test, karateEnv, featureDir, featureName, fileClassLoader);
    }
    
}
