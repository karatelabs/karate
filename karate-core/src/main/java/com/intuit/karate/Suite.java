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
import com.intuit.karate.core.HtmlFeatureReport;
import com.intuit.karate.core.HtmlSummaryReport;
import com.intuit.karate.core.HtmlTimelineReport;
import com.intuit.karate.core.Reports;
import com.intuit.karate.core.SyncExecutorService;
import com.intuit.karate.core.Tags;
import com.intuit.karate.http.HttpClientFactory;
import com.intuit.karate.job.JobManager;
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

    public final String env;
    public final String tagSelector;
    public final boolean dryRun;
    public final long startTime;
    public final File workingDir;
    public final String buildDir;
    public final String reportDir;
    public final ClassLoader classLoader;
    public final int threadCount;
    public final int timeoutMinutes;
    public final int featuresFound;
    public final List<Feature> features;
    public final List<CompletableFuture> futures;
    public final Results results;
    public final Set<File> featureResultFiles;
    public final Collection<RuntimeHook> hooks;
    public final HttpClientFactory clientFactory;
    public final Map<String, String> systemProperties;

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

    public final Map<String, Object> suiteCache;
    private final ReentrantLock progressFileLock;

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
            hooks = null;
            features = null;
            featuresFound = -1;
            futures = null;
            results = null;
            featureResultFiles = null;
            workingDir = FileUtils.WORKING_DIR;
            buildDir = FileUtils.getBuildDir();
            reportDir = null;
            karateBase = null;
            karateConfig = null;
            karateConfigEnv = null;
            parallel = false;
            scenarioExecutor = null;
            pendingTasks = null;
            suiteCache = null;
            jobManager = null;
            progressFileLock = null;
        } else {
            startTime = System.currentTimeMillis();
            rb.resolveAll();
            outputHtmlReport = rb.outputHtmlReport;
            outputCucumberJson = rb.outputCucumberJson;
            outputJunitXml = rb.outputJunitXml;
            dryRun = rb.dryRun;
            classLoader = rb.classLoader;
            clientFactory = rb.clientFactory;
            env = rb.env;
            systemProperties = rb.systemProperties;
            tagSelector = Tags.fromKarateOptionsTags(rb.tags);
            hooks = rb.hooks;
            features = rb.features;
            featuresFound = features.size();
            futures = new ArrayList(featuresFound);
            suiteCache = rb.suiteCache;
            results = new Results(this);
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
            int index = 0;
            for (Feature feature : features) {
                final int featureNum = ++index;
                FeatureRuntime fr = FeatureRuntime.of(this, feature);
                final CompletableFuture future = new CompletableFuture();
                futures.add(future);
                fr.setNext(() -> {
                    onFeatureDone(fr, featureNum);
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
            results.setEndTime(System.currentTimeMillis());
            if (outputHtmlReport) {
                HtmlSummaryReport summary = new HtmlSummaryReport();
                HtmlTimelineReport timeline = new HtmlTimelineReport();
                getFeatureResults().forEach(fr -> {
                    timeline.addFeatureResult(fr);
                    int scenarioCount = fr.getScenarioCount();
                    results.addToScenarioCount(scenarioCount);
                    if (scenarioCount != 0) {
                        results.incrementFeatureCount();
                    }
                    results.addToFailCount(fr.getFailedCount());
                    results.addToTimeTaken(fr.getDurationMillis());
                    if (fr.isFailed()) {
                        results.addToFailedList(fr.getPackageQualifiedName(), fr.getErrorMessages());
                    }
                    if (!fr.isEmpty()) {
                        HtmlFeatureReport.saveFeatureResult(reportDir, fr);
                        summary.addFeatureResult(fr);
                    }
                });
                summary.save(reportDir);
                timeline.save(reportDir);
                saveStatsJson();
            }                        
        } catch (Exception e) {
            logger.error("runner failed: " + e);
            results.setFailureReason(e);
        } finally {
            scenarioExecutor.shutdownNow();
            pendingTasks.shutdownNow();
            if (jobManager != null) {
                jobManager.server.stop();
            }
            results.printStats();
            hooks.forEach(h -> h.afterSuite(this));
        }
    }

    private void onFeatureDone(FeatureRuntime fr, int index) {
        FeatureResult result = fr.result;
        Feature feature = fr.feature;
        if (result.getScenarioCount() > 0) { // possible that zero scenarios matched tags
            try { // edge case that reports are not writable     
                File file = Reports.saveKarateJson(reportDir, result, null);
                synchronized (featureResultFiles) {
                    featureResultFiles.add(file);
                }
                if (outputCucumberJson) {
                    Reports.saveCucumberJson(reportDir, result, null);
                }
                if (outputJunitXml) {
                    Reports.saveJunitXml(reportDir, result, null);
                }
                String status = result.isFailed() ? "fail" : "pass";
                logger.info("<<{}>> feature {} of {} ({} remaining) {}", status, index, featuresFound, getFeaturesRemaining() - 1, feature);
                result.printStats();
            } catch (Exception e) {
                logger.error("<<error>> unable to write report file(s): {} - {}", feature, e + "");
                result.printStats();
            }
        } else {
            results.addToSkipCount(1);
            if (logger.isTraceEnabled()) {
                logger.trace("<<skip>> feature {} of {}: {}", index, featuresFound, feature);
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

    public void backupReportDirIfExists() {
        File file = new File(reportDir);
        if (file.exists()) {
            File dest = new File(reportDir + "_" + System.currentTimeMillis());
            if (!file.renameTo(dest)) {
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

    private File saveStatsJson() {
        String json = JsonUtils.toJson(results.toMap());
        File file = new File(reportDir + File.separator + "karate-results-json.txt");
        FileUtils.writeToFile(file, json);
        return file;
    }

}
