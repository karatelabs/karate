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
package com.intuit.karate;

import com.intuit.karate.core.Feature;
import com.intuit.karate.runtime.RuntimeHook;
import com.intuit.karate.runtime.RuntimeHookFactory;
import com.intuit.karate.runtime.Tags;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class SuiteRuntime {

    private String env; // lazy inited

    public final String tagSelector;
    public final Logger logger;
    public final File workingDir;
    public final String buildDir;
    public final String reportDir;
    public final ClassLoader classLoader;
    public final int threadCount;
    public final List<Feature> features;
    public final Results results;
    public final Collection<RuntimeHook> hooks;
    public final RuntimeHookFactory hookFactory;

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

    public static SuiteRuntime forMock() {
        Runner.Builder builder = new Runner.Builder();
        builder.forMock = true;
        return new SuiteRuntime(builder);
    }

    public SuiteRuntime() {
        this(new Runner.Builder());
    }

    public SuiteRuntime(Runner.Builder rb) {
        env = rb.env;
        tagSelector = Tags.fromKarateOptionsTags(rb.tags);
        logger = rb.logger;
        workingDir = rb.workingDir;
        buildDir = rb.buildDir;
        reportDir = rb.reportDir;
        classLoader = rb.classLoader;
        threadCount = rb.threadCount;
        hooks = rb.hooks;
        hookFactory = rb.hookFactory;
        features = rb.resolveFeatures();
        results = Results.startTimer(threadCount);
        //======================================================================
        if (rb.forMock) { // don't show logs and confuse people
            karateBase = null;
            karateConfig = null;
            karateConfigEnv = null;
        } else {
            karateBase = read("classpath:karate-base.js");
            if (karateBase != null) {
                logger.info("karate-base.js found on classpath");
            }
            String configPrefix = rb.configDir;
            if (configPrefix == null) {
                configPrefix = StringUtils.trimToNull(System.getProperty("karate.config.dir"));
            }
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

    private boolean resolved;

    public Collection<RuntimeHook> resolveHooks() {
        if (hookFactory == null || resolved) {
            return hooks;
        }
        resolved = true;
        hooks.add(hookFactory.create());
        return hooks;
    }

    public String getEnv() {
        if (env == null) {
            env = StringUtils.trimToNull(System.getProperty("karate.env"));
        }
        return env;
    }

}
