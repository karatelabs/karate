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

import com.intuit.karate.core.ScenarioResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Results {

    private final int threadCount;
    private int featureCount;
    private int scenarioCount;
    private int failCount;
    private int skipCount;
    private double timeTakenMillis;
    private final long startTime;
    private long endTime;
    private Map<String, String> failedMap;
    private Throwable failureReason;
    private String reportDir;
    private final List<ScenarioResult> scenarioResults = new ArrayList();

    public void printStats(int threadCount) {
        System.out.println("Karate version: " + FileUtils.getKarateVersion());
        System.out.println("======================================================");
        System.out.println(String.format("elapsed: %6.2f | threads: %4d | thread time: %.2f ",
                getElapsedTime() / 1000, threadCount, timeTakenMillis / 1000));
        System.out.println(String.format("features: %5d | ignored: %4d | efficiency: %.2f", featureCount, skipCount, getEfficiency()));
        System.out.println(String.format("scenarios: %4d | passed: %5d | failed: %d",
                scenarioCount, getPassCount(), failCount));
        System.out.println("======================================================");
        System.out.println(getErrorMessages());
        if (failureReason != null) {
            if (failCount == 0) {
                failCount = 1;
            }
            System.out.println("*** runner exception stack trace ***");
            failureReason.printStackTrace();
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap();
        map.put("version", FileUtils.getKarateVersion());
        map.put("threads", threadCount);
        map.put("features", featureCount);
        map.put("ignored", skipCount);
        map.put("scenarios", scenarioCount);
        map.put("failed", failCount);
        map.put("passed", getPassCount());
        map.put("elapsedTime", getElapsedTime());
        map.put("totalTime", getTimeTakenMillis());
        map.put("efficiency", getEfficiency());        
        map.put("failures", failedMap);
        return map;
    }

    private Results(long startTime, int threadCount) {
        this.startTime = startTime;
        this.threadCount = threadCount;
    }

    public void addToFailedList(String name, String errorMessage) {
        if (failedMap == null) {
            failedMap = new LinkedHashMap();
        }
        failedMap.put(name, errorMessage);
    }

    public static Results startTimer(int threadCount) {
        return new Results(System.currentTimeMillis(), threadCount);
    }

    public String getReportDir() {
        return reportDir;
    }

    public void setReportDir(String reportDir) {
        this.reportDir = reportDir;
    }

    public void setFailureReason(Throwable failureReason) {
        this.failureReason = failureReason;
    }

    public Throwable getFailureReason() {
        return failureReason;
    }

    public void addToScenarioCount(int count) {
        scenarioCount += count;
    }
    
    public void incrementFeatureCount() {
        featureCount++;
    }    

    public void addToFailCount(int count) {
        failCount += count;
    }

    public void addToSkipCount(int count) {
        skipCount += count;
    }

    public void addToTimeTaken(double time) {
        timeTakenMillis += time;
    }

    public void stopTimer() {
        endTime = System.currentTimeMillis();
    }

    public void addScenarioResults(List<ScenarioResult> list) {
        scenarioResults.addAll(list);
    }

    public List<ScenarioResult> getScenarioResults() {
        return scenarioResults;
    }

    public String getErrorMessages() {
        StringBuilder sb = new StringBuilder();
        if (failedMap != null) {
            sb.append("failed features:\n");
            failedMap.forEach((k, v) -> {
                sb.append(k).append(": ").append(v).append('\n');
            });
        }
        return sb.toString();
    }

    public double getElapsedTime() {
        return endTime - startTime;
    }

    public double getEfficiency() {
        return timeTakenMillis / (getElapsedTime() * threadCount);
    }

    public int getPassCount() {
        return scenarioCount - failCount;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public int getFeatureCount() {
        return featureCount;
    }

    public int getScenarioCount() {
        return scenarioCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public double getTimeTakenMillis() {
        return timeTakenMillis;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public Map<String, String> getFailedMap() {
        return failedMap;
    }

}
