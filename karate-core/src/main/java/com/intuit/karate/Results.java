/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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

import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.HtmlSummaryReport;
import com.intuit.karate.core.HtmlTimelineReport;
import com.intuit.karate.core.ScenarioResult;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 *
 * @author pthomas3
 */
public class Results {

    private final Suite suite;
    private final int featureCount;
    private final int scenarioCount;
    private final int skippedCount;
    private final double timeTakenMillis;
    private final long endTime;
    private final List<Throwable> errors = new ArrayList();

    public static Results of(Suite suite) {
        return new Results(suite);
    }

    private Results(Suite suite) {
        this.suite = suite;
        endTime = suite.endTime;
        skippedCount = suite.skippedCount;
        AtomicInteger fc = new AtomicInteger();
        AtomicInteger sc = new AtomicInteger();
        AtomicInteger time = new AtomicInteger();
        HtmlSummaryReport summary;
        HtmlTimelineReport timeline;
        if (suite.outputHtmlReport) {
            summary = new HtmlSummaryReport();
            timeline = new HtmlTimelineReport();
        } else {
            summary = null;
            timeline = null;
        }
        suite.getFeatureResults().forEach(fr -> {
            if (!fr.isEmpty()) {
                if (timeline != null) {
                    timeline.addFeatureResult(fr);
                }
                if (summary != null) {
                    summary.addFeatureResult(fr);
                }
                fc.incrementAndGet();
                Long duration = Math.round(fr.getDurationMillis());
                time.addAndGet(duration.intValue());
            }
            sc.addAndGet(fr.getScenarioCount());
            errors.addAll(fr.getErrors());
        });
        featureCount = fc.get();
        scenarioCount = sc.get();
        timeTakenMillis = time.get();
        saveStatsJson();
        printStats();
        if (timeline != null) {
            timeline.save(suite.reportDir);
        } 
        if (summary != null) {
            summary.save(suite.reportDir);
        }        
    }

    public Stream<FeatureResult> getFeatureResults() {
        return suite.getFeatureResults();
    }

    public Stream<ScenarioResult> getScenarioResults() {
        return suite.getScenarioResults();
    }

    private void saveStatsJson() {
        String json = JsonUtils.toJson(toMap());
        File file = new File(suite.reportDir + File.separator + "karate-results-json.txt");
        FileUtils.writeToFile(file, json);
    }

    private void printStats() {
        System.out.println("Karate version: " + FileUtils.KARATE_VERSION);
        System.out.println("======================================================");
        System.out.println(String.format("elapsed: %6.2f | threads: %4d | thread time: %.2f ",
                getElapsedTime() / 1000, suite.threadCount, timeTakenMillis / 1000));
        System.out.println(String.format("features: %5d | ignored: %4d | efficiency: %.2f", featureCount, skippedCount, getEfficiency()));
        System.out.println(String.format("scenarios: %4d | passed: %5d | failed: %d",
                scenarioCount, getPassCount(), errors.size()));
        System.out.println("======================================================");
        if (!errors.isEmpty()) {
            System.out.println(getErrorMessages());
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap();
        map.put("version", FileUtils.KARATE_VERSION);
        map.put("threads", suite.threadCount);
        map.put("features", featureCount);
        map.put("ignored", skippedCount);
        map.put("scenarios", scenarioCount);
        map.put("failed", errors.size());
        map.put("passed", getPassCount());
        map.put("elapsedTime", getElapsedTime());
        map.put("totalTime", getTimeTakenMillis());
        map.put("efficiency", getEfficiency());
        return map;
    }

    public String getReportDir() {
        return suite.reportDir;
    }

    public String getErrorMessages() {
        StringBuilder sb = new StringBuilder();
        sb.append("failed features:\n");
        errors.forEach(e -> sb.append(e.getMessage()).append("\n"));
        return sb.toString();
    }

    public double getElapsedTime() {
        return endTime - suite.startTime;
    }

    public double getEfficiency() {
        return timeTakenMillis / (getElapsedTime() * suite.threadCount);
    }

    public int getPassCount() {
        return scenarioCount - errors.size();
    }

    public int getFeatureCount() {
        return featureCount;
    }

    public int getScenarioCount() {
        return scenarioCount;
    }

    public int getFailCount() {
        return errors.size();
    }

    public double getTimeTakenMillis() {
        return timeTakenMillis;
    }

    public long getStartTime() {
        return suite.startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public List<Throwable> getErrors() {
        return errors;
    }

    public Suite getSuite() {
        return suite;
    }

}
