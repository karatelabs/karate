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
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Suite {

    public final String env;
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
    public final HttpClientFactory clientFactory;

    public final String karateConfigDir;
    public final String karateBase;
    public final String karateConfig;
    public final String karateConfigEnv;

    public final Map<String, Object> SUITE_CACHE = new HashMap();

    private String read(String name) {
        try {
            Resource resource = new Resource(name, classLoader);
            logger.debug("[config] {}", resource.getPath());
            return resource.getAsString();
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
        env = rb.resolveEnv();
        tagSelector = Tags.fromKarateOptionsTags(rb.tags);
        logger = rb.logger;
        workingDir = rb.workingDir;
        buildDir = rb.buildDir;
        reportDir = rb.reportDir;
        classLoader = rb.classLoader;
        threadCount = rb.threadCount;
        hooks = rb.resolveHooks(); // ensure that hook factory is processed on the suite thread=
        features = rb.resolveFeatures();
        results = Results.startTimer(threadCount);
        results.setReportDir(reportDir); // TODO unify
        if (rb.clientFactory == null) {
            clientFactory = HttpClientFactory.DEFAULT;
        } else {
            clientFactory = rb.clientFactory;
        }
        //======================================================================
        if (rb.forTempUse) { // don't show logs and confuse people
            karateBase = null;
            karateConfig = null;
            karateConfigDir = null;
            karateConfigEnv = null;
        } else {
            karateBase = read("classpath:karate-base.js");
            String temp = rb.configDir;
            if (temp == null) {
                temp = StringUtils.trimToNull(System.getProperty(Constants.KARATE_CONFIG_DIR));
                if (temp == null) {
                    temp = "classpath:";
                }
            }
            if (temp.startsWith("file:") || temp.startsWith("classpath:")) {
                // all good
            } else {
                temp = "file:" + temp;
            }
            if (temp.endsWith(":") || temp.endsWith("/") || temp.endsWith("\\")) {
                // all good
            } else {
                temp = temp + File.separator;
            }
            karateConfigDir = temp;
            karateConfig = read(karateConfigDir + "karate-config.js");
            if (karateConfig != null) {
            } else {
                logger.warn("karate-config.js not found [{}]", karateConfigDir);
            }
            if (env != null) {
                karateConfigEnv = read(karateConfigDir + "karate-config-" + env + ".js");
            } else {
                karateConfigEnv = null;
            }
        }
    }

}
