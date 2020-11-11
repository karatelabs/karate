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
import com.intuit.karate.server.HttpClientFactory;
import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author pthomas3
 */
public class SuiteRuntime {

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
    public final HttpClientFactory clientFactory;

    public final Map<String, Object> SUITE_CACHE = new HashMap();

    public final String karateConfigDir;
    public final String karateBase;
    public final String karateConfig;

    public String env; // can be lazy-inited
    public String karateConfigEnv;

    private String read(String name) {
        try {
            Resource resource = new Resource(name, classLoader);
            return resource.getAsString();
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
        karateConfigEnv = rb.env;
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
        results.setReportDir(reportDir); // TODO unify
        clientFactory = rb.clientFactory == null ? resolveClientFactory() : rb.clientFactory;
        //======================================================================
        if (rb.forMock) { // don't show logs and confuse people
            karateBase = null;
            karateConfig = null;
            karateConfigDir = null;
        } else {
            karateBase = read("classpath:karate-base.js");
            if (karateBase != null) {
                logger.info("karate-base.js found on classpath");
            }
            String temp = rb.configDir;
            if (temp == null) {
                temp = StringUtils.trimToNull(System.getProperty("karate.config.dir"));
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
                logger.info("karate-config.js found in {}", karateConfigDir);
            } else {
                logger.warn("karate-config.js not found in {}", karateConfigDir);
            }
        }
    }

    private boolean envResolved;

    public String resolveEnv() {
        if (!envResolved) {
            envResolved = true;
            if (env == null) {
                env = StringUtils.trimToNull(System.getProperty("karate.env"));
            }
            if (env != null) {
                logger.info("karate.env is: '{}'", env);
                karateConfigEnv = read(karateConfigDir + "karate-config-" + env + ".js");
                if (karateConfigEnv != null) {
                    logger.info("karate-config-" + env + ".js found in {}", karateConfigDir);
                }
            }
        }
        return env;
    }

    private boolean hooksResolved;

    public Collection<RuntimeHook> resolveHooks() {
        if (hookFactory == null || hooksResolved) {
            return hooks;
        }
        hooksResolved = true;
        hooks.add(hookFactory.create());
        return hooks;
    }

    private static final String KARATE_HTTP_PROPERTIES = "classpath:karate-http.properties";
    private static final String CLIENT_FACTORY = "client.factory";

    private HttpClientFactory resolveClientFactory() {
        try {
            Resource resource = new Resource(KARATE_HTTP_PROPERTIES, classLoader);
            InputStream is = resource.getStream();
            if (is == null) {
                throw new RuntimeException(KARATE_HTTP_PROPERTIES + " not found");
            }
            Properties props = new Properties();
            props.load(is);
            String className = props.getProperty(CLIENT_FACTORY);
            if (className == null) {
                throw new RuntimeException("property " + CLIENT_FACTORY + " not found in " + KARATE_HTTP_PROPERTIES);
            }
            Class clazz = Class.forName(className);
            HttpClientFactory factory = (HttpClientFactory) clazz.newInstance();
            logger.info("using http client factory: {}", factory.getClass());
            return factory;
        } catch (Exception e) {
            logger.warn("using built-in http client, {}", e.getMessage());
            return HttpClientFactory.DEFAULT;
        }
    }

}
