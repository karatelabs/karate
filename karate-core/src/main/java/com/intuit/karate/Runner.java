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
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.core.RuntimeHookFactory;
import com.intuit.karate.core.ScenarioCall;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.driver.DriverRunner;
import com.intuit.karate.http.HttpClientFactory;
import com.intuit.karate.job.JobConfig;
import com.intuit.karate.report.SuiteReports;
import com.intuit.karate.resource.ResourceUtils;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Runner {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Runner.class);

    public static Map<String, Object> runFeature(Feature feature, Map<String, Object> vars, boolean evalKarateConfig) {
        Suite suite = new Suite();
        FeatureRuntime featureRuntime = FeatureRuntime.of(suite, feature, vars);
        featureRuntime.caller.setKarateConfigDisabled(!evalKarateConfig);
        featureRuntime.run();
        FeatureResult result = featureRuntime.result;
        if (result.isFailed()) {
            throw result.getErrorMessagesCombined();
        }
        return result.getVariables();
    }

    public static Map<String, Object> runFeature(File file, Map<String, Object> vars, boolean evalKarateConfig) {
        Feature feature = Feature.read(file);
        return runFeature(feature, vars, evalKarateConfig);
    }

    public static Map<String, Object> runFeature(Class relativeTo, String path, Map<String, Object> vars, boolean evalKarateConfig) {
        File file = ResourceUtils.getFileRelativeTo(relativeTo, path);
        return runFeature(file, vars, evalKarateConfig);
    }

    public static Map<String, Object> runFeature(String path, Map<String, Object> vars, boolean evalKarateConfig) {
        Feature feature = FileUtils.parseFeatureAndCallTag(path);
        return runFeature(feature, vars, evalKarateConfig);
    }

    // this is called by karate-gatling !
    public static void callAsync(Runner.Builder builder, String path, Map<String, Object> arg, PerfHook perfHook) {
        builder.features = Collections.emptyList(); // will skip expensive feature resolution in builder.resolveAll()
        Suite suite = new Suite(builder);
        Feature feature = FileUtils.parseFeatureAndCallTag(path);
        FeatureRuntime featureRuntime = FeatureRuntime.of(suite, feature, arg, perfHook);
        featureRuntime.setNext(() -> perfHook.afterFeature(featureRuntime.result));
        perfHook.submit(featureRuntime);
    }

    //==========================================================================
    //
    /**
     * @see com.intuit.karate.Runner#builder()
     * @deprecated
     */
    @Deprecated
    public static Results parallel(Class<?> clazz, int threadCount) {
        return parallel(clazz, threadCount, null);
    }

    /**
     * @see com.intuit.karate.Runner#builder()
     * @deprecated
     */
    @Deprecated
    public static Results parallel(Class<?> clazz, int threadCount, String reportDir) {
        return builder().fromKarateAnnotation(clazz).reportDir(reportDir).parallel(threadCount);
    }

    /**
     * @see com.intuit.karate.Runner#builder()
     * @deprecated
     */
    @Deprecated
    public static Results parallel(List<String> tags, List<String> paths, int threadCount, String reportDir) {
        return parallel(tags, paths, null, null, threadCount, reportDir);
    }

    /**
     * @see com.intuit.karate.Runner#builder()
     * @deprecated
     */
    @Deprecated
    public static Results parallel(int threadCount, String... tagsOrPaths) {
        return parallel(null, threadCount, tagsOrPaths);
    }

    /**
     * @see com.intuit.karate.Runner#builder()
     * @deprecated
     */
    @Deprecated
    public static Results parallel(String reportDir, int threadCount, String... tagsOrPaths) {
        List<String> tags = new ArrayList();
        List<String> paths = new ArrayList();
        for (String s : tagsOrPaths) {
            s = StringUtils.trimToEmpty(s);
            if (s.startsWith("~") || s.startsWith("@")) {
                tags.add(s);
            } else {
                paths.add(s);
            }
        }
        return parallel(tags, paths, threadCount, reportDir);
    }

    /**
     * @see com.intuit.karate.Runner#builder()
     * @deprecated
     */
    @Deprecated
    public static Results parallel(List<String> tags, List<String> paths, String scenarioName,
            List<RuntimeHook> hooks, int threadCount, String reportDir) {
        Builder options = new Builder();
        options.tags = tags;
        options.paths = paths;
        options.scenarioName = scenarioName;
        if (hooks != null) {
            options.hooks.addAll(hooks);
        }
        options.reportDir = reportDir;
        return options.parallel(threadCount);
    }

    //==========================================================================
    //
    public static class Builder<T extends Builder> {

        ClassLoader classLoader;
        Class optionsClass;
        String env;
        File workingDir;
        String buildDir;
        String configDir;
        int threadCount;
        int timeoutMinutes;
        String reportDir;
        String scenarioName;
        List<String> tags;
        List<String> paths;
        List<Feature> features;
        String relativeTo;
        final Collection<RuntimeHook> hooks = new ArrayList();
        RuntimeHookFactory hookFactory;
        HttpClientFactory clientFactory;
        boolean forTempUse;
        boolean backupReportDir = true;
        boolean outputHtmlReport = true;
        boolean outputJunitXml;
        boolean outputCucumberJson;
        boolean dryRun;
        boolean debugMode;
        Map<String, String> systemProperties;
        Map<String, Object> callSingleCache;
        Map<String, ScenarioCall.Result> callOnceCache;
        SuiteReports suiteReports;
        JobConfig jobConfig;
        Map<String, DriverRunner> drivers;

        // synchronize because the main user is karate-gatling
        public synchronized Builder copy() {
            Builder b = new Builder();
            b.classLoader = classLoader;
            b.optionsClass = optionsClass;
            b.env = env;
            b.workingDir = workingDir;
            b.buildDir = buildDir;
            b.configDir = configDir;
            b.threadCount = threadCount;
            b.timeoutMinutes = timeoutMinutes;
            b.reportDir = reportDir;
            b.scenarioName = scenarioName;
            b.tags = tags;
            b.paths = paths;
            b.features = features;
            b.relativeTo = relativeTo;
            b.hooks.addAll(hooks); // final
            b.hookFactory = hookFactory;
            b.clientFactory = clientFactory;
            b.forTempUse = forTempUse;
            b.backupReportDir = backupReportDir;
            b.outputHtmlReport = outputHtmlReport;
            b.outputJunitXml = outputJunitXml;
            b.outputCucumberJson = outputCucumberJson;
            b.dryRun = dryRun;
            b.debugMode = debugMode;
            b.systemProperties = systemProperties;
            b.callSingleCache = callSingleCache;
            b.callOnceCache = callOnceCache;
            b.suiteReports = suiteReports;
            b.jobConfig = jobConfig;
            b.drivers = drivers;
            return b;
        }

        public List<Feature> resolveAll() {
            if (classLoader == null) {
                classLoader = Thread.currentThread().getContextClassLoader();
            }
            if (clientFactory == null) {
                clientFactory = HttpClientFactory.DEFAULT;
            }
            if (systemProperties == null) {
                systemProperties = new HashMap(System.getProperties());
            } else {
                systemProperties.putAll(new HashMap(System.getProperties()));
            }
            // env
            String tempOptions = StringUtils.trimToNull(systemProperties.get(Constants.KARATE_OPTIONS));
            if (tempOptions != null) {
                LOGGER.info("using system property '{}': {}", Constants.KARATE_OPTIONS, tempOptions);
                Main ko = Main.parseKarateOptions(tempOptions);
                if (ko.tags != null) {
                    tags = ko.tags;
                }
                if (ko.paths != null) {
                    paths = ko.paths;
                }
                dryRun = ko.dryRun || dryRun;
            }
            String tempEnv = StringUtils.trimToNull(systemProperties.get(Constants.KARATE_ENV));
            if (tempEnv != null) {
                LOGGER.info("using system property '{}': {}", Constants.KARATE_ENV, tempEnv);
                env = tempEnv;
            } else if (env != null) {
                LOGGER.info("karate.env is: '{}'", env);
            }
            // config dir
            String tempConfig = StringUtils.trimToNull(systemProperties.get(Constants.KARATE_CONFIG_DIR));
            if (tempConfig != null) {
                LOGGER.info("using system property '{}': {}", Constants.KARATE_CONFIG_DIR, tempConfig);
                configDir = tempConfig;
            }
            if (workingDir == null) {
                workingDir = FileUtils.WORKING_DIR;
            }
            if (configDir == null) {
                try {
                    ResourceUtils.getResource(workingDir, "classpath:karate-config.js");
                    configDir = "classpath:"; // default mode
                } catch (Exception e) {
                    configDir = workingDir.getPath();
                }
            }
            if (configDir.startsWith("file:") || configDir.startsWith("classpath:")) {
                // all good
            } else {
                configDir = "file:" + configDir;
            }
            if (configDir.endsWith(":") || configDir.endsWith("/") || configDir.endsWith("\\")) {
                // all good
            } else {
                configDir = configDir + File.separator;
            }
            if (buildDir == null) {
                buildDir = FileUtils.getBuildDir();
            }
            if (reportDir == null) {
                reportDir = buildDir + File.separator + Constants.KARATE_REPORTS;
            }
            // hooks
            if (hookFactory != null) {
                hook(hookFactory.create());
            }
            // features
            if (features == null) {
                if (paths != null && !paths.isEmpty()) {
                    if (relativeTo != null) {
                        paths = paths.stream().map(p -> {
                            if (p.startsWith("classpath:")) {
                                return p;
                            }
                            if (!p.endsWith(".feature")) {
                                p = p + ".feature";
                            }
                            return relativeTo + "/" + p;
                        }).collect(Collectors.toList());
                    }
                } else if (relativeTo != null) {
                    paths = new ArrayList();
                    paths.add(relativeTo);
                }
                features = ResourceUtils.findFeatureFiles(workingDir, paths);
            }
            if (scenarioName != null) {
                for (Feature feature : features) {
                    feature.setCallName(scenarioName);
                }
            }
            if (callSingleCache == null) {
                callSingleCache = new HashMap();
            }
            if (callOnceCache == null) {
                callOnceCache = new HashMap();
            }
            if (suiteReports == null) {
                suiteReports = SuiteReports.DEFAULT;
            }
            if (drivers != null) {
                Map<String, DriverRunner> customDrivers = drivers;
                drivers = DriverOptions.driverRunners();
                drivers.putAll(customDrivers); // allows override of Karate drivers (e.g. custom 'chrome')
            } else {
                drivers = DriverOptions.driverRunners();
            }
            if (jobConfig != null) {
                reportDir = jobConfig.getExecutorDir();
                if (threadCount < 1) {
                    threadCount = jobConfig.getExecutorCount();
                }
                timeoutMinutes = jobConfig.getTimeoutMinutes();
            }
            if (threadCount < 1) {
                threadCount = 1;
            }
            return features;
        }

        protected T forTempUse() {
            forTempUse = true;
            return (T) this;
        }

        //======================================================================
        //
        public T configDir(String dir) {
            this.configDir = dir;
            return (T) this;
        }

        public T karateEnv(String env) {
            this.env = env;
            return (T) this;
        }

        public T systemProperty(String key, String value) {
            if (systemProperties == null) {
                systemProperties = new HashMap();
            }
            systemProperties.put(key, value);
            return (T) this;
        }

        public T workingDir(File value) {
            if (value != null) {
                this.workingDir = value;
            }
            return (T) this;
        }

        public T buildDir(String value) {
            if (value != null) {
                this.buildDir = value;
            }
            return (T) this;
        }

        public T classLoader(ClassLoader value) {
            classLoader = value;
            return (T) this;
        }

        public T relativeTo(Class clazz) {
            relativeTo = "classpath:" + ResourceUtils.toPathFromClassPathRoot(clazz);
            return (T) this;
        }

        /**
         * @see com.intuit.karate.Runner#builder()
         * @deprecated
         */
        @Deprecated
        public T fromKarateAnnotation(Class<?> clazz) {
            KarateOptions ko = clazz.getAnnotation(KarateOptions.class);
            if (ko != null) {
                LOGGER.warn("the @KarateOptions annotation is deprecated, please use Runner.builder()");
                if (ko.tags().length > 0) {
                    tags = Arrays.asList(ko.tags());
                }
                if (ko.features().length > 0) {
                    paths = Arrays.asList(ko.features());
                }
            }
            return relativeTo(clazz);
        }

        public T path(String... value) {
            path(Arrays.asList(value));
            return (T) this;
        }

        public T path(List<String> value) {
            if (value != null) {
                if (paths == null) {
                    paths = new ArrayList();
                }
                paths.addAll(value);
            }
            return (T) this;
        }

        public T tags(List<String> value) {
            if (value != null) {
                if (tags == null) {
                    tags = new ArrayList();
                }
                tags.addAll(value);
            }
            return (T) this;
        }

        public T tags(String... tags) {
            tags(Arrays.asList(tags));
            return (T) this;
        }

        public T features(Collection<Feature> value) {
            if (value != null) {
                if (features == null) {
                    features = new ArrayList();
                }
                features.addAll(value);
            }
            return (T) this;
        }

        public T features(Feature... value) {
            return features(Arrays.asList(value));
        }

        public T reportDir(String value) {
            if (value != null) {
                this.reportDir = value;
            }
            return (T) this;
        }

        public T scenarioName(String name) {
            this.scenarioName = name;
            return (T) this;
        }

        public T timeoutMinutes(int timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
            return (T) this;
        }

        public T hook(RuntimeHook hook) {
            if (hook != null) {
                hooks.add(hook);
            }
            return (T) this;
        }

        public T hooks(Collection<RuntimeHook> hooks) {
            if (hooks != null) {
                this.hooks.addAll(hooks);
            }
            return (T) this;
        }

        public T hookFactory(RuntimeHookFactory hookFactory) {
            this.hookFactory = hookFactory;
            return (T) this;
        }

        public T clientFactory(HttpClientFactory clientFactory) {
            this.clientFactory = clientFactory;
            return (T) this;
        }

        // don't allow junit 5 builder to run in parallel
        public Builder threads(int value) {
            threadCount = value;
            return this;
        }

        public T outputHtmlReport(boolean value) {
            outputHtmlReport = value;
            return (T) this;
        }

        public T backupReportDir(boolean value) {
            backupReportDir = value;
            return (T) this;
        }

        public T outputCucumberJson(boolean value) {
            outputCucumberJson = value;
            return (T) this;
        }

        public T outputJunitXml(boolean value) {
            outputJunitXml = value;
            return (T) this;
        }

        public T dryRun(boolean value) {
            dryRun = value;
            return (T) this;
        }

        public T debugMode(boolean value) {
            debugMode = value;
            return (T) this;
        }

        public T callSingleCache(Map<String, Object> value) {
            callSingleCache = value;
            return (T) this;
        }
        
        public T callOnceCache(Map<String, ScenarioCall.Result> value) {
            callOnceCache = value;
            return (T) this;
        }        

        public T suiteReports(SuiteReports value) {
            suiteReports = value;
            return (T) this;
        }

        public T customDrivers(Map<String, DriverRunner> customDrivers) {
            drivers = customDrivers;
            return (T) this;
        }

        public Results jobManager(JobConfig value) {
            jobConfig = value;
            Suite suite = new Suite(this);
            suite.run();
            return suite.buildResults();
        }

        public Results parallel(int threadCount) {
            threads(threadCount);
            Suite suite = new Suite(this);
            suite.run();
            return suite.buildResults();
        }

        @Override
        public String toString() {
            return paths + "";
        }

    }

    public static Builder path(String... paths) {
        Builder builder = new Builder();
        return builder.path(paths);
    }

    public static Builder path(List<String> paths) {
        Builder builder = new Builder();
        return builder.path(paths);
    }

    public static Builder builder() {
        return new Runner.Builder();
    }

}
