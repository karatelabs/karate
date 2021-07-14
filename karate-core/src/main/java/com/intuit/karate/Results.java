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
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.TagResults;
import com.intuit.karate.core.TimelineResults;
import com.intuit.karate.report.ReportUtils;
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
    private final int featuresPassed;
    private final int featuresFailed;
    private final int featuresSkipped;
    private final int scenariosPassed;
    private final int scenariosFailed;
    private final double timeTakenMillis;
    private final long endTime;
    private final List<String> errors = new ArrayList();
    private final List<Map<String, Object>> featureSummary = new ArrayList();

    public static Results of(Suite suite) {
        return new Results(suite);
    }

    private Results(Suite suite) {
        this.suite = suite;
        // endTime may not be set for junit
        endTime = suite.endTime == 0 ? System.currentTimeMillis() : suite.endTime;
        featuresSkipped = suite.skippedCount;
        AtomicInteger fp = new AtomicInteger();
        AtomicInteger ff = new AtomicInteger();
        AtomicInteger sp = new AtomicInteger();
        AtomicInteger sf = new AtomicInteger();
        AtomicInteger time = new AtomicInteger();
        TimelineResults timeline = new TimelineResults();
        TagResults tags = new TagResults();
        suite.getFeatureResults().forEach(fr -> {
            if (!fr.isEmpty()) {
                timeline.addFeatureResult(fr);
                tags.addFeatureResult(fr);
                if (fr.isFailed()) {
                    ff.incrementAndGet();
                } else {
                    fp.incrementAndGet();
                }
                Long duration = Math.round(fr.getDurationMillis());
                time.addAndGet(duration.intValue());
                featureSummary.add(fr.toSummaryJson());
            }
            sp.addAndGet(fr.getPassedCount());
            sf.addAndGet(fr.getFailedCount());
            errors.addAll(fr.getErrors());
        });
        featuresPassed = fp.get();
        featuresFailed = ff.get();
        scenariosPassed = sp.get();
        scenariosFailed = sf.get();
        timeTakenMillis = time.get();
        saveStatsJson();
        printStats();
        if (suite.outputHtmlReport) {
            suite.suiteReports.timelineReport(suite, timeline).render();
            suite.suiteReports.tagsReport(suite, tags).render();
            // last so that path can be printed to the console 
            File file = suite.suiteReports.summaryReport(suite, this).render();
            System.out.println("\nHTML report: (paste into browser to view) | Karate version: "
                    + FileUtils.KARATE_VERSION + "\n"
                    + file.toPath().toUri()
                    + "\n===================================================================\n");
        }
    }

    public Stream<FeatureResult> getFeatureResults() {
        return suite.getFeatureResults();
    }

    public Stream<ScenarioResult> getScenarioResults() {
        return suite.getScenarioResults();
    }

    private void saveStatsJson() {
        String json = JsonUtils.toJson(toKarateJson());
        File file = new File(suite.reportDir + File.separator + "karate-summary-json.txt");
        FileUtils.writeToFile(file, json);
    }

    private void printStats() {
        System.out.println("Karate version: " + FileUtils.KARATE_VERSION);
        System.out.println("======================================================");
        System.out.println(String.format("elapsed: %6.2f | threads: %4d | thread time: %.2f ",
                getElapsedTime() / 1000, suite.threadCount, timeTakenMillis / 1000));
        System.out.println(String.format("features: %5d | skipped: %4d | efficiency: %.2f", getFeaturesTotal(), featuresSkipped, getEfficiency()));
        System.out.println(String.format("scenarios: %4d | passed: %5d | failed: %d",
                getScenariosTotal(), scenariosPassed, scenariosFailed));
        System.out.println("======================================================");
        if (!errors.isEmpty()) {
            System.out.println(">>> failed features:");
            System.out.println(getErrorMessages());
            System.out.println("<<<");
        }
    }

    public Map<String, Object> toKarateJson() {
        Map<String, Object> map = new HashMap();
        map.put("version", FileUtils.KARATE_VERSION);
        map.put("threads", suite.threadCount);
        map.put("featuresPassed", featuresPassed);
        map.put("featuresFailed", featuresFailed);
        map.put("featuresSkipped", featuresSkipped);
        map.put("scenariosPassed", scenariosPassed);
        map.put("scenariosfailed", errors.size());
        map.put("elapsedTime", getElapsedTime());
        map.put("totalTime", getTimeTakenMillis());
        map.put("efficiency", getEfficiency());
        map.put("resultDate", ReportUtils.getDateString());
        map.put("featureSummary", featureSummary);
        return map;
    }

    public String getReportDir() {
        return suite.reportDir;
    }

    public List<String> getErrors() {
        return errors;
    }

    public double getElapsedTime() {
        return endTime - suite.startTime;
    }

    public double getEfficiency() {
        return timeTakenMillis / (getElapsedTime() * suite.threadCount);
    }

    public int getScenariosPassed() {
        return scenariosPassed;
    }

    public int getScenariosFailed() {
        return scenariosFailed;
    }

    public int getScenariosTotal() {
        return scenariosPassed + scenariosFailed;
    }

    public int getFeaturesTotal() {
        return featuresPassed + featuresFailed;
    }

    public int getFeaturesPassed() {
        return featuresPassed;
    }

    public int getFeaturesFailed() {
        return featuresFailed;
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

    public String getErrorMessages() {
        return StringUtils.join(errors, "\n");
    }

    public Suite getSuite() {
        return suite;
    }

}
