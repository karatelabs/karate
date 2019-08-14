package com.intuit.karate;

import com.intuit.karate.core.*;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RunnerBuilder {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RunnerBuilder.class);

    private int threadCount = 1;
    private Class<?> testClass;
    private List<String> paths;
    private String reportDir = FileUtils.getBuildDir() + File.separator + ScriptBindings.SUREFIRE_REPORTS;
    private List<String> tags;
    private Collection<ExecutionHook> hooks = Collections.emptyList();;
    private String scenarioName;

    private RunnerBuilder(){}

    public RunnerBuilder(Class<?> testClass){
        this.testClass = testClass;
    }

    public RunnerBuilder(String... paths){
        this.paths = Arrays.asList(paths);
    }

    public RunnerBuilder(List<String> tags, String... paths){
        this.paths = Arrays.asList(paths);
        this.tags = tags;
    }
    public RunnerBuilder threadCount(int threadCount) {
        this.threadCount = threadCount;
        return this;
    }

    public RunnerBuilder reportDir(String reportDir) {
        this.reportDir = reportDir;
        return this;
    }

    public RunnerBuilder hooks(Collection<ExecutionHook> hooks) {
        this.hooks.addAll(hooks);
        return this;
    }

    public RunnerBuilder hook(ExecutionHook hook){
        this.hooks.add(hook);
        return this;
    }

    public RunnerBuilder scenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
        return this;
    }

    public Results runParallel() {
        String tagSelector;
        List<Resource> resources;
        // check if ambiguous configuration provided
        if (testClass != null) {
            RunnerOptions options = RunnerOptions.fromAnnotationAndSystemProperties(testClass);
            tagSelector = options.getTags() == null ? null : Tags.fromKarateOptionsTags(options.getTags());
            resources = FileUtils.scanForFeatureFiles(options.getFeatures(), Thread.currentThread().getContextClassLoader());
        }else {
            tagSelector = Tags.fromKarateOptionsTags(tags);
            resources = FileUtils.scanForFeatureFiles(paths, Thread.currentThread().getContextClassLoader());
        }

        new File(reportDir).mkdirs();

        final String finalReportDir = reportDir;
        Results results = Results.startTimer(threadCount);
        ExecutorService featureExecutor = Executors.newFixedThreadPool(threadCount);
        ExecutorService scenarioExecutor = Executors.newWorkStealingPool(threadCount);
        int executedFeatureCount = 0;
        try {
            int count = resources.size();
            CountDownLatch latch = new CountDownLatch(count);
            List<FeatureResult> featureResults = new ArrayList(count);
            for (int i = 0; i < count; i++) {
                Resource resource = resources.get(i);
                int index = i + 1;
                Feature feature = FeatureParser.parse(resource);
                feature.setCallName(scenarioName);
                feature.setCallLine(resource.getLine());
                FeatureContext featureContext = new FeatureContext(null, feature, tagSelector);
                CallContext callContext = CallContext.forAsync(feature, hooks, null, false);
                ExecutionContext execContext = new ExecutionContext(results.getStartTime(), featureContext, callContext, reportDir,
                        r -> featureExecutor.submit(r), scenarioExecutor);
                featureResults.add(execContext.result);
                FeatureExecutionUnit unit = new FeatureExecutionUnit(execContext);
                unit.setNext(() -> {
                    FeatureResult result = execContext.result;
                    if (result.getScenarioCount() > 0) { // possible that zero scenarios matched tags
                        File file = Engine.saveResultJson(finalReportDir, result, null);
                        if (result.getScenarioCount() < 500) {
                            // TODO this routine simply cannot handle that size
                            Engine.saveResultXml(finalReportDir, result, null);
                        }
                        String status = result.isFailed() ? "fail" : "pass";
                        logger.info("<<{}>> feature {} of {}: {}", status, index, count, feature.getRelativePath());
                        result.printStats(file.getPath());
                    } else {
                        results.addToSkipCount(1);
                        if (logger.isTraceEnabled()) {
                            logger.trace("<<skip>> feature {} of {}: {}", index, count, feature.getRelativePath());
                        }
                    }
                    latch.countDown();
                });
                featureExecutor.submit(unit);
            }
            latch.await();
            results.stopTimer();
            for (FeatureResult result : featureResults) {
                int scenarioCount = result.getScenarioCount();
                results.addToScenarioCount(scenarioCount);
                if (scenarioCount != 0) {
                    executedFeatureCount++;
                }
                results.addToFailCount(result.getFailedCount());
                results.addToTimeTaken(result.getDurationMillis());
                if (result.isFailed()) {
                    results.addToFailedList(result.getPackageQualifiedName(), result.getErrorMessages());
                }
                results.addScenarioResults(result.getScenarioResults());
            }
        } catch (Exception e) {
            logger.error("karate parallel runner failed: ", e.getMessage());
            results.setFailureReason(e);
        } finally {
            featureExecutor.shutdownNow();
            scenarioExecutor.shutdownNow();
        }
        results.setFeatureCount(executedFeatureCount);
        results.printStats(threadCount);
        Engine.saveStatsJson(reportDir, results, null);
        Engine.saveTimelineHtml(reportDir, results, null);
        results.setReportDir(reportDir);
        return results;
    }
}
