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

import com.intuit.karate.FileUtils;
import com.intuit.karate.Results;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * as of version 0.9.0 - replaced by {@link com.intuit.karate.Results}
 * 
 * @author pthomas3
 */
@Deprecated
public class KarateStats {
    
    private int featureCount;
    private int testCount;
    private int failCount;
    private double timeTakenMillis;    
    private final long startTime;
    private long endTime;
    private Map<String, String> failedMap;
    private Throwable failureReason;
    private String reportDir;
    
    protected KarateStats(Results from) {
        featureCount = from.getFeatureCount();
        testCount = from.getScenarioCount();
        failCount = from.getFailCount();
        timeTakenMillis = from.getTimeTakenMillis();
        startTime = from.getStartTime();
        endTime = from.getEndTime();
        failedMap = from.getFailedMap();
        failureReason = from.getFailureReason();
        reportDir = from.getReportDir();
    }
    
    private KarateStats(long startTime) {
        this.startTime = startTime;
    }
    
    public void addToFailedList(String name, String errorMessage) {
        if (failedMap == null) {
            failedMap = new LinkedHashMap();
        }
        failedMap.put(name, errorMessage);
    }
    
    public static KarateStats startTimer() {
        return new KarateStats(System.currentTimeMillis());
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
    
    public void addToTestCount(int count) {
        testCount += count;
    }
    
    public void addToFailCount(int count) {
        failCount += count;
    }
    
    public void addToTimeTaken(double time) {
        timeTakenMillis += time;
    }
    
    public void stopTimer() {
        endTime = System.currentTimeMillis();
    }
    
    public void printStats(int threadCount) {
        double elapsedTime = endTime - startTime;
        System.out.println("Karate version: " + FileUtils.getKarateVersion());
        System.out.println("====================================================");
        System.out.println(String.format("elapsed time: %.2f | total thread time: %.2f", elapsedTime / 1000, timeTakenMillis / 1000));
        double efficiency = timeTakenMillis / (elapsedTime * threadCount);
        System.out.println(String.format("features: %5d | threads: %3d | efficiency: %.2f", 
                featureCount, threadCount, efficiency));
        System.out.println(String.format("scenarios: %4d | passed: %4d | failed: %4d", 
                testCount, testCount - failCount, failCount));
        System.out.println("====================================================");
        if (failedMap != null) {
            System.out.println("failed features:");
            failedMap.forEach((k, v) -> {
                System.out.println(k + ": " + v);
            });
        }
        if (failureReason != null) {
            if (failCount == 0) {
                failCount = 1;
            }
            System.out.println("*** runner exception stack trace ***");
            failureReason.printStackTrace();
        }
    }

    public void setFeatureCount(int featureCount) {
        this.featureCount = featureCount;
    }        

    public int getFeatureCount() {
        return featureCount;
    }        

    public int getTestCount() {
        return testCount;
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
