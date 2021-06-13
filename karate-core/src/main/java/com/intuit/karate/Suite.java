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
import com.intuit.karate.driver.DriverRunner;
import com.intuit.karate.report.ReportUtils;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioCall;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.core.SyncExecutorService;
import com.intuit.karate.core.Tags;
import com.intuit.karate.http.HttpClientFactory;
import com.intuit.karate.job.JobManager;
import com.intuit.karate.report.SuiteReports;
import com.intuit.karate.resource.Resource;
import com.intuit.karate.resource.ResourceUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Suite implements Runnable {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Suite.class);

    public final long startTime;
    protected long endTime;
    protected int skippedCount;

    public final String env;
    public final String tagSelector;
    public final boolean dryRun;
    public final boolean debugMode;
    public final File workingDir;
    public final String buildDir;
    public final String reportDir;
    public final ClassLoader classLoader;
    public final int threadCount;
    public final int timeoutMinutes;
    public final int featuresFound;
    public final List<Feature> features;
    public final List<CompletableFuture> futures;
    public final Set<File> featureResultFiles;
    public final Collection<RuntimeHook> hooks;
    public final HttpClientFactory clientFactory;
    public final Map<String, String> systemProperties;

    public final boolean backupReportDir;
    public final SuiteReports suiteReports;

    public final boolean outputHtmlReport;
    public final boolean outputCucumberJson;
    public final boolean outputJunitXml;

    public final boolean parallel;
    public final ExecutorService scenarioExecutor;
    public final ExecutorService pendingTasks;

    public final JobManager jobManager;

    public final String karateBase;
    public final String karateConfig;
    public final String karateConfigEnv;

    public final Map<String, Object> callSingleCache;
    public final Map<String, ScenarioCall.Result> callOnceCache;
    private final ReentrantLock progressFileLock;

    public final Map<String, DriverRunner> drivers;

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
        if (rb.forTempUse) {
            dryRun = false;
            debugMode = false;
            backupReportDir = false;
            outputHtmlReport = false;
            outputCucumberJson = false;
            outputJunitXml = false;
            classLoader = Thread.currentThread().getContextClassLoader();
            clientFactory = HttpClientFactory.DEFAULT;
            startTime = -1;
            env = rb.env;
            systemProperties = null;
            tagSelector = null;
            threadCount = -1;
            timeoutMinutes = -1;
            hooks = Collections.EMPTY_LIST;
            features = null;
            featuresFound = -1;
            futures = null;
            featureResultFiles = null;
            workingDir = FileUtils.WORKING_DIR;
            buildDir = FileUtils.getBuildDir();
            reportDir = FileUtils.getBuildDir();
            karateBase = null;
            karateConfig = null;
            karateConfigEnv = null;
            parallel = false;
            scenarioExecutor = null;
            pendingTasks = null;
            callSingleCache = null;
            callOnceCache = null;
            suiteReports = null;
            jobManager = null;
            progressFileLock = null;
            drivers = null;
        } else {
            startTime = System.currentTimeMillis();
            rb.resolveAll();
            backupReportDir = rb.backupReportDir;
            outputHtmlReport = rb.outputHtmlReport;
            outputCucumberJson = rb.outputCucumberJson;
            outputJunitXml = rb.outputJunitXml;
            dryRun = rb.dryRun;
            debugMode = rb.debugMode;
            classLoader = rb.classLoader;
            clientFactory = rb.clientFactory;
            env = rb.env;
            systemProperties = rb.systemProperties;
            tagSelector = Tags.fromKarateOptionsTags(rb.tags);
            hooks = rb.hooks;
            features = rb.features;
            featuresFound = features.size();
            futures = new ArrayList(featuresFound);
            callSingleCache = rb.callSingleCache;
            callOnceCache = rb.callOnceCache;
            suiteReports = rb.suiteReports;
            featureResultFiles = new HashSet();
            workingDir = rb.workingDir;
            buildDir = rb.buildDir;
            reportDir = rb.reportDir;
            karateBase = read("classpath:karate-base.js");
            karateConfig = read(rb.configDir + "karate-config.js");
            if (env != null) {
                karateConfigEnv = read(rb.configDir + "karate-config-" + env + ".js");
            } else {
                karateConfigEnv = null;
            }
            if (rb.jobConfig != null) {
                jobManager = new JobManager(rb.jobConfig);
            } else {
                jobManager = null;
            }
            drivers = rb.drivers;
            threadCount = rb.threadCount;
            timeoutMinutes = rb.timeoutMinutes;
            parallel = threadCount > 1;
            if (parallel) {
                scenarioExecutor = Executors.newFixedThreadPool(threadCount);
                pendingTasks = Executors.newSingleThreadExecutor();
            } else {
                scenarioExecutor = SyncExecutorService.INSTANCE;
                pendingTasks = SyncExecutorService.INSTANCE;
            }
            progressFileLock = new ReentrantLock();
        }
    }

    @Override
    public void run() {
        try {
            if (backupReportDir) {
                backupReportDirIfExists();
            }
            hooks.forEach(h -> h.beforeSuite(this));
            int index = 0;
            for (Feature feature : features) {
                final int featureNum = ++index;
                FeatureRuntime fr = FeatureRuntime.of(this, feature);
                final CompletableFuture future = new CompletableFuture();
                futures.add(future);
                fr.setNext(() -> {
                    onFeatureDone(fr.result, featureNum);
                    future.complete(Boolean.TRUE);
                });
                pendingTasks.submit(fr);
            }
            if (featuresFound > 1) {
                logger.debug("waiting for {} features to complete", featuresFound);
            }
            if (jobManager != null) {
                jobManager.start();
            }
            CompletableFuture[] futuresArray = futures.toArray(new CompletableFuture[futures.size()]);
            if (timeoutMinutes > 0) {
                CompletableFuture.allOf(futuresArray).get(timeoutMinutes, TimeUnit.MINUTES);
            } else {
                CompletableFuture.allOf(futuresArray).join();
            }
            endTime = System.currentTimeMillis();
        } catch (Throwable t) {
            logger.error("runner failed: " + t);
        } finally {
            scenarioExecutor.shutdownNow();
            pendingTasks.shutdownNow();
            if (jobManager != null) {
                jobManager.server.stop();
            }
            hooks.forEach(h -> h.afterSuite(this));
        }
    }

    public void saveFeatureResults(FeatureResult fr) {
        File file = ReportUtils.saveKarateJson(reportDir, fr, null);
        synchronized (featureResultFiles) {
            featureResultFiles.add(file);
        }
        if (outputHtmlReport) {
            suiteReports.featureReport(this, fr).render();
        }
        if (outputCucumberJson) {
            ReportUtils.saveCucumberJson(reportDir, fr, null);
        }
        if (outputJunitXml) {
            ReportUtils.saveJunitXml(reportDir, fr, null);
        }
        fr.printStats();
    }

    private void onFeatureDone(FeatureResult fr, int index) {
        if (fr.getScenarioCount() > 0) { // possible that zero scenarios matched tags
            try { // edge case that reports are not writable     
                saveFeatureResults(fr);
                String status = fr.isFailed() ? "fail" : "pass";
                logger.info("<<{}>> feature {} of {} ({} remaining) {}", status, index, featuresFound, getFeaturesRemaining() - 1, fr.getFeature());
            } catch (Throwable t) {
                logger.error("<<error>> unable to write report file(s): {} - {}", fr.getFeature(), t + "");
                fr.printStats();
            }
        } else {
            skippedCount++;
            if (logger.isTraceEnabled()) {
                logger.trace("<<skip>> feature {} of {}: {}", index, featuresFound, fr.getFeature());
            }
        }
        if (progressFileLock.tryLock()) {
            saveProgressJson();
            progressFileLock.unlock();
        }
    }

    public Stream<FeatureResult> getFeatureResults() {
        return featureResultFiles.stream()
                .map(file -> FeatureResult.fromKarateJson(workingDir, Json.of(FileUtils.toString(file)).asMap()));
    }

    public Stream<ScenarioResult> getScenarioResults() {
        return getFeatureResults().flatMap(fr -> fr.getScenarioResults().stream());
    }

    public ScenarioResult retryScenario(Scenario scenario) {
        FeatureRuntime fr = FeatureRuntime.of(this, scenario.getFeature());
        ScenarioRuntime runtime = new ScenarioRuntime(fr, scenario);
        runtime.run();
        return runtime.result;
    }

    public Results updateResults(ScenarioResult sr) {
        Scenario scenario = sr.getScenario();
        FeatureResult fr;
        File file = new File(reportDir + File.separator + scenario.getFeature().getKarateJsonFileName());
        if (file.exists()) {
            String json = FileUtils.toString(file);
            fr = FeatureResult.fromKarateJson(workingDir, Json.of(json).asMap());
        } else {
            fr = new FeatureResult(scenario.getFeature());
        }
        List<ScenarioResult> scenarioResults = fr.getScenarioResults();
        int count = scenarioResults.size();
        int found = -1;
        for (int i = 0; i < count; i++) {
            ScenarioResult temp = scenarioResults.get(i);
            if (temp.getScenario().isEqualTo(scenario)) {
                found = i;
                break;
            }
        }
        if (found != -1) {
            scenarioResults.set(found, sr);
        } else {
            scenarioResults.add(sr);
        }
        fr.sortScenarioResults();
        saveFeatureResults(fr);
        return buildResults();
    }

    private void backupReportDirIfExists() {
        File file = new File(reportDir);
        if (file.exists()) {
            File dest = new File(reportDir + "_" + System.currentTimeMillis());
            if (file.renameTo(dest)) {
                logger.info("backed up existing '{}' dir to: {}", reportDir, dest);
            } else {
                logger.warn("failed to backup existing dir: {}", file);
            }
        }
    }

    public long getFeaturesRemaining() {
        return futures.stream().filter(f -> !f.isDone()).count();
    }

    private File saveProgressJson() {
        long remaining = getFeaturesRemaining() - 1;
        Map<String, Object> map = Collections.singletonMap("featuresRemaining", remaining);
        String json = JsonUtils.toJson(map);
        File file = new File(reportDir + File.separator + "karate-progress-json.txt");
        FileUtils.writeToFile(file, json);
        return file;
    }

    public Results buildResults() {
        return Results.of(this);
    }

}
