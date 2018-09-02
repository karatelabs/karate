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
package com.intuit.karate.cucumber;

import com.intuit.karate.CallContext;
import com.intuit.karate.FileResource;
import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.core.Engine;
import com.intuit.karate.core.ExecutionContext;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.FeatureResult;
import cucumber.api.CucumberOptions;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import java.util.Arrays;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.intuit.karate.core.ExecutionHook;
import com.intuit.karate.core.FeatureExecutionUnit;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class CucumberRunner {

    private static final Logger logger = LoggerFactory.getLogger(CucumberRunner.class);

    public static KarateStats parallel(Class<?> clazz, int threadCount) {
        return parallel(clazz, threadCount, null);
    }

    public static KarateStats parallel(Class<?> clazz, int threadCount, String reportDir) {
        CucumberOptions co = clazz.getAnnotation(CucumberOptions.class);
        List<String> tags;
        List<String> features;
        if (co == null) {
            logger.warn("CucumberOptions annotation not found on class: {}", clazz);
            tags = null;
            features = null;
        } else {
            String[] tagsArray = co.tags();
            tags = Arrays.asList(tagsArray);
            String[] featuresArray = co.features();
            features = Arrays.asList(featuresArray);
        }
        if (features == null || features.isEmpty()) {
            String relative = FileUtils.toRelativeClassPath(clazz);
            features = Collections.singletonList(relative);
        }        
        return parallel(tags, features, null, threadCount, reportDir);
    }   
    
    public static KarateStats parallel(List<String> tags, List<String> paths, int threadCount, String reportDir) {
        return parallel(tags, paths, null, threadCount, reportDir);
    }    
    
    public static KarateStats parallel(List<String> tags, List<String> paths, ExecutionHook hook, int threadCount, String reportDir) {
        String tagSelector = tags == null ? null : Engine.fromCucumberOptionsTags(tags);
        List<FileResource> files = FileUtils.scanForFeatureFiles(paths);
        return parallel(tagSelector, files, hook, threadCount, reportDir);
    }
    
    public static KarateStats parallel(String tagSelector, List<FileResource> resources, int threadCount, String reportDir) {
        return parallel(tagSelector, resources, null, threadCount, reportDir);
    }     
    
    public static KarateStats parallel(String tagSelector, List<FileResource> resources, ExecutionHook hook, int threadCount, String reportDir) {
        if (reportDir == null) {
            reportDir = Engine.getBuildDir() + File.separator + "surefire-reports";
        }
        final String finalReportDir = reportDir;
        logger.info("Karate version: {}", FileUtils.getKarateVersion());
        KarateStats stats = KarateStats.startTimer();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        int executedFeatureCount = 0;
        try {
            int count = resources.size();
            List<Callable<FeatureResult>> callables = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                FileResource resource = resources.get(i);
                int index = i + 1;
                Feature feature = FeatureParser.parse(resource.file, resource.relativePath);
                callables.add(() -> {
                    // we are now within a separate thread. the reporter filters logs by self thread
                    String threadName = Thread.currentThread().getName();
                    FeatureResult result = Engine.executeFeatureSync(null, feature, tagSelector, new CallContext(true, hook));                                        
                    if (result.getScenarioCount() > 0) { // possible that zero scenarios matched tags                   
                        File file = Engine.saveResultJson(finalReportDir, result);
                        Engine.saveResultXml(finalReportDir, result);
                        String status = result.isFailed() ? "fail" : "pass";
                        logger.info("<<{}>> feature {} of {}: {}", status, index, count, feature.getRelativePath());
                        result.printStats(feature.getRelativePath(), file.getPath());                        
                    } else {
                        logger.info("<<skip>> feature {} of {}: {}", index, count, feature.getRelativePath());
                    }
                    return result;
                });
            }
            List<Future<FeatureResult>> futures = executor.invokeAll(callables);
            stats.stopTimer();
            for (Future<FeatureResult> future : futures) {
                FeatureResult result = future.get(); // guaranteed to be not-null
                int scenarioCount = result.getScenarioCount();                
                stats.addToTestCount(scenarioCount);
                if (scenarioCount != 0) {
                    executedFeatureCount++;
                }
                stats.addToFailCount(result.getFailedCount());
                stats.addToTimeTaken(result.getDuration());
                if (result.isFailed()) {                    
                    stats.addToFailedList(result.getFeature().getPackageQualifiedName(), result.getErrorMessages());
                }
            }
        } catch (Exception e) {
            logger.error("karate parallel runner failed: ", e.getMessage());
            stats.setFailureReason(e);
        } finally {
            executor.shutdownNow();
        }
        stats.setFeatureCount(executedFeatureCount);
        stats.printStats(threadCount);
        return stats;
    }

    public static Map<String, Object> runFeature(Feature feature, Map<String, Object> vars, boolean evalKarateConfig) {
        CallContext callContext = new CallContext(vars, evalKarateConfig);
        FeatureResult result = Engine.executeFeatureSync(null, feature, null, callContext);
        return result.getResultAsPrimitiveMap();
    }

    public static Map<String, Object> runFeature(File file, Map<String, Object> vars, boolean evalKarateConfig) {
        Feature feature = FeatureParser.parse(file);
        return runFeature(feature, vars, evalKarateConfig);
    }

    public static Map<String, Object> runFeature(Class relativeTo, String path, Map<String, Object> vars, boolean evalKarateConfig) {
        File file = FileUtils.getFileRelativeTo(relativeTo, path);
        return runFeature(file, vars, evalKarateConfig);
    }

    public static Map<String, Object> runFeature(String path, Map<String, Object> vars, boolean evalKarateConfig) {
        Feature feature = FeatureParser.parse(path);
        return runFeature(feature, vars, evalKarateConfig);
    }
    
    // this is called by karate-gatling !
    public static void callAsync(String path, CallContext callContext, Consumer<Runnable> system, Runnable next) { 
        Feature feature = FileUtils.resolveFeature(path);
        ScriptEnv env = ScriptEnv.forEnvAndFeatureFile(null, feature.getFile());
        ExecutionContext ec = new ExecutionContext(feature, env, callContext, system);
        FeatureExecutionUnit exec = new FeatureExecutionUnit(ec);
        exec.submit(next);
    }  

}
