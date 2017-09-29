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
import com.intuit.karate.FileUtils;
import com.intuit.karate.Script;
import com.intuit.karate.ScriptValueMap;
import cucumber.runtime.model.CucumberFeature;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class CucumberRunner {

    private static final Logger logger = LoggerFactory.getLogger(CucumberRunner.class);

    public static KarateStats parallel(Class clazz, int threadCount) {
        return parallel(clazz, threadCount, "target/surefire-reports");
    }

    public static KarateStats parallel(Class clazz, int threadCount, String reportDir) {
        KarateStats stats = KarateStats.startTimer();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            KarateRuntimeOptions kro = new KarateRuntimeOptions(clazz);
            List<KarateFeature> karateFeatures = KarateFeature.loadFeatures(kro);
            int count = karateFeatures.size();
            stats.setFeatureCount(count);
            List<Callable<KarateReporter>> callables = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                KarateFeature karateFeature = karateFeatures.get(i);
                int index = i + 1;                
                CucumberFeature feature = karateFeature.getFeature();                
                callables.add(() -> {
                    // we are now within a separate thread. the reporter filters logs by self thread
                    String threadName = Thread.currentThread().getName();
                    KarateReporter reporter = karateFeature.getReporter(reportDir);
                    KarateRuntime runtime = karateFeature.getRuntime(reporter);
                    try {                        
                        feature.run(reporter, reporter, runtime);
                        logger.info("<<<< feature {} of {} on thread {}: {}", index, count, threadName, feature.getPath());
                    } catch (Exception e) {
                        logger.error("karate xml/json generation failed for: {}", feature.getPath());
                        reporter.setFailureReason(e);
                    } finally { // try our best to close the report file gracefully so that report generation is not broken
                        reporter.done();
                    }
                    return reporter;
                });
            }            
            List<Future<KarateReporter>> futures = executor.invokeAll(callables);
            stats.stopTimer();            
            for (Future<KarateReporter> future : futures) {
                KarateReporter reporter = future.get(); // guaranteed to be not-null
                KarateJunitFormatter formatter = reporter.getJunitFormatter();
                if (reporter.getFailureReason() != null) {
                    logger.error("karate xml/json generation failed: {}", formatter.getFeaturePath());
                    logger.error("karate xml/json error stack trace", reporter.getFailureReason());
                }
                stats.addToTestCount(formatter.getTestCount());
                stats.addToFailCount(formatter.getFailCount());
                stats.addToSkipCount(formatter.getSkipCount());
                stats.addToTimeTaken(formatter.getTimeTaken());
                if (formatter.isFail()) {
                    stats.addToFailedList(formatter.getFeaturePath());
                }
            }            
        } catch (Exception e) {
            logger.error("karate parallel runner failed: ", e.getMessage());
            stats.setFailureReason(e);
        } finally {
            executor.shutdownNow();                        
        }
        stats.printStats(threadCount);
        return stats;
    }

    private static Map<String, Object> runFeature(File file, Map<String, Object> vars, boolean evalKarateConfig) {
        FeatureWrapper featureWrapper = FeatureWrapper.fromFile(file, Thread.currentThread().getContextClassLoader());
        CallContext callContext = new CallContext(null, vars, false, evalKarateConfig);
        ScriptValueMap scriptValueMap = CucumberUtils.call(featureWrapper, callContext);
        return Script.simplify(scriptValueMap);
    }

    public static Map<String, Object> runFeature(Class relativeTo, String path, Map<String, Object> vars, boolean evalKarateConfig) {
        File dir = FileUtils.getDirContaining(relativeTo);
        File file = new File(dir.getPath() + File.separator + path);
        return runFeature(file, vars, evalKarateConfig);
    }

    public static Map<String, Object> runClasspathFeature(String classPath, Map<String, Object> vars, boolean evalKarateConfig) {
        URL url = Thread.currentThread().getContextClassLoader().getResource(classPath);
        if (url == null) {
            throw new RuntimeException("file not found: " + classPath);
        }
        File file = new File(url.getFile());
        return runFeature(file, vars, evalKarateConfig);
    }

}
