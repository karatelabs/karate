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
import com.intuit.karate.core.Tags;
import com.intuit.karate.http.HttpClientFactory;
import com.intuit.karate.resource.Resource;
import com.intuit.karate.resource.ResourceUtils;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Suite {
    
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Suite.class);

    public final String env;
    public final String tagSelector;
    public final long startTime;
    public final File workingDir;
    public final String buildDir;
    public final String reportDir;
    public final ClassLoader classLoader;
    public final int threadCount;
    public final Semaphore batchLimiter;
    public final List<Feature> features;
    public final Results results;
    public final Collection<RuntimeHook> hooks;
    public final HttpClientFactory clientFactory;
    public final Map<String, String> systemProperties;

    public final String karateBase;
    public final String karateConfig;
    public final String karateConfigEnv;

    public final Map<String, Object> SUITE_CACHE = new HashMap();

    private String read(String name) {
        try {
            Resource resource = ResourceUtils.getResource(workingDir, name);
            logger.debug("[config] {}", resource.getPrefixedPath());
            return FileUtils.toString(resource.getStream());
        } catch (Exception e) {
            logger.trace("file not found: {} - {}", name, e.getMessage());
            return null;
        }
    }

    public static Suite forTempUse() {
        return new Suite(Runner.builder().forTempUse());
    }

    public Suite() {
        this(Runner.builder());
    }

    public Suite(Runner.Builder rb) {
        classLoader = Thread.currentThread().getContextClassLoader();
        workingDir = rb.workingDir == null ? FileUtils.WORKING_DIR : rb.workingDir;
        buildDir = rb.buildDir == null ? FileUtils.getBuildDir() : rb.buildDir;
        reportDir = rb.reportDir == null ? buildDir + File.separator + Constants.KARATE_REPORTS : rb.reportDir;
        clientFactory = rb.clientFactory == null ? HttpClientFactory.DEFAULT : rb.clientFactory;
        if (rb.forTempUse) {
            startTime = 0;
            env = rb.env;
            systemProperties = null;
            tagSelector = null;    
            threadCount = 1;
            batchLimiter = null;
            hooks = null;
            features = null;
            results = null;
            karateBase = null;
            karateConfig = null;
            karateConfigEnv = null;
        } else {
            startTime = System.currentTimeMillis();
            rb.resolveAll();
            env = rb.env;
            systemProperties = rb.systemProperties;
            tagSelector = Tags.fromKarateOptionsTags(rb.tags);
            threadCount = rb.threadCount;
            batchLimiter = new Semaphore(threadCount);
            hooks = rb.hooks;
            features = rb.features;
            results = new Results(this);
            karateBase = read("classpath:karate-base.js");
            karateConfig = read(rb.configDir + "karate-config.js");
            if (env != null) {
                karateConfigEnv = read(rb.configDir + "karate-config-" + env + ".js");
            } else {
                karateConfigEnv = null;
            }
        }
    }

}
