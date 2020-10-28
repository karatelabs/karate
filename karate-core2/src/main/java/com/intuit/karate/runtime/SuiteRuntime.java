/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.runtime;

import com.intuit.karate.Logger;
import com.intuit.karate.Resource;
import com.intuit.karate.Results;
import com.intuit.karate.StringUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class SuiteRuntime {

    public final String env;
    public final Logger logger = new Logger();
    public final File workingDir = new File("");
    public final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    public final Results results = Results.startTimer(1);
    public final Collection<RuntimeHook> runtimeHooks = new ArrayList();

    public final Map<String, Object> SUITE_CACHE = new HashMap();

    public final String karateBase;
    public final String karateConfig;
    public final String karateConfigEnv;

    private String read(String name) {
        try {
            return Resource.relativePathToString(name);
        } catch (Exception e) {
            logger.trace("file not found: {} - {}", name, e.getMessage());
            return null;
        }
    }

    public SuiteRuntime() {
        karateBase = read("classpath:karate-base.js");
        if (karateBase != null) {
            logger.info("karate-base.js found on classpath");
        }
        String configPrefix = StringUtils.trimToNull(System.getProperty("karate.config.dir"));
        if (configPrefix == null) {
            configPrefix = "classpath:";
        } else {
            if (configPrefix.startsWith("file:") || configPrefix.startsWith("classpath:")) {
                // all good
            } else {
                configPrefix = "file:" + configPrefix;
            }
            if (configPrefix.endsWith("/") || configPrefix.endsWith("\\")) {
                // all good
            } else {
                configPrefix = configPrefix + File.separator;
            }
        }
        karateConfig = read(configPrefix + "karate-config.js");
        if (karateConfig != null) {
            logger.info("karate-config.js found in {}", configPrefix);
        } else {
            logger.warn("karate-config.js not found in {}", configPrefix);
        }
        env = StringUtils.trimToNull(System.getProperty("karate.env"));
        if (env != null) {
            karateConfigEnv = read(configPrefix + "karate-config-" + env + ".js");
            if (karateConfigEnv != null) {
                logger.info("karate-config-" + env + ".js found in {}", configPrefix);
            }
        } else {
            karateConfigEnv = null;
        }
    }

}
