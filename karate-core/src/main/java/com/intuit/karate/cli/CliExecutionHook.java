/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.cli;

import com.intuit.karate.StringUtils;
import com.intuit.karate.core.Engine;
import com.intuit.karate.core.ExecutionHook;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.PerfEvent;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.http.HttpRequestBuilder;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author pthomas3
 */
public class CliExecutionHook implements ExecutionHook {

    private final boolean htmlReport;
    private final String targetDir;
    private final boolean intellij;
    private final ReentrantLock LOCK = new ReentrantLock();

    public CliExecutionHook(boolean htmlReport, String targetDir, boolean intellij) {
        this.htmlReport = htmlReport;
        this.targetDir = targetDir;
        this.intellij = intellij;
        if (intellij) {
            log(String.format(TEMPLATE_ENTER_THE_MATRIX, getCurrentTime()));
            log(String.format(TEMPLATE_SCENARIO_COUNTING_STARTED, 0, getCurrentTime()));
        }
    }

    public void close() {
        if (intellij) {
            log(String.format(TEMPLATE_SCENARIO_COUNTING_FINISHED, getCurrentTime()));
        }
    }

    @Override
    public boolean beforeScenario(Scenario scenario, ScenarioContext context) {
        if (intellij) {
            log(String.format(TEMPLATE_SCENARIO_STARTED, getCurrentTime()));
            Path absolutePath = scenario.getFeature().getResource().getPath().toAbsolutePath();
            log(String.format(TEMPLATE_TEST_STARTED, getCurrentTime(), absolutePath + ":" + scenario.getLine(), scenario.getNameForReport()));
        }
        return true;
    }

    @Override
    public void afterScenario(ScenarioResult result, ScenarioContext context) {
        if (intellij) {
            Scenario scenario = result.getScenario();
            if (result.isFailed()) {
                StringUtils.Pair error = details(result.getError());
                log(String.format(TEMPLATE_SCENARIO_FAILED, getCurrentTime()));
                log(String.format(TEMPLATE_TEST_FAILED, getCurrentTime(), escape(error.right), escape(error.left), scenario.getNameForReport(), ""));
            }
            log(String.format(TEMPLATE_SCENARIO_FINISHED, getCurrentTime()));
            log(String.format(TEMPLATE_TEST_FINISHED, getCurrentTime(), result.getDurationNanos() / 1000000, scenario.getNameForReport()));
        }
    }

    @Override
    public boolean beforeFeature(Feature feature) {
        if (intellij) {
            Path absolutePath = feature.getResource().getPath().toAbsolutePath();
            log(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), absolutePath + ":" + feature.getLine(), feature.getNameForReport()));
        }
        return true;
    }

    @Override
    public void afterFeature(FeatureResult result) {
        if (intellij) {
            log(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), result.getFeature().getNameForReport()));
        }
        if (htmlReport) {
            Engine.saveResultHtml(targetDir, result, null);
        }
        if (LOCK.tryLock()) {
            Engine.saveStatsJson(targetDir, result.getResults(), null);
            LOCK.unlock();
        }
    }

    @Override
    public String getPerfEventName(HttpRequestBuilder req, ScenarioContext context) {
        return null;
    }

    @Override
    public void reportPerfEvent(PerfEvent event) {

    }

    private static void log(String s) {
        System.out.println(s);
    }

    private static String getCurrentTime() {
        return DATE_FORMAT.format(new Date());
    }

    private static String escape(String source) {
        if (source == null) {
            return "";
        }
        return source.replace("|", "||").replace("\n", "|n").replace("\r", "|r").replace("'", "|'").replace("[", "|[").replace("]", "|]");
    }

    private static StringUtils.Pair details(Throwable error) {
        String fullMessage = error.getMessage().replace("\r", "").replace("\t", "  ");
        String[] messageInfo = fullMessage.split("\n", 2);
        if (messageInfo.length == 2) {
            return StringUtils.pair(messageInfo[0].trim(), messageInfo[1].trim());
        } else {
            return StringUtils.pair(fullMessage, "");
        }
    }

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSZ");

    private static final String TEAMCITY_PREFIX = "##teamcity";
    private static final String TEMPLATE_TEST_STARTED = TEAMCITY_PREFIX + "[testStarted timestamp = '%s' locationHint = '%s' captureStandardOutput = 'true' name = '%s']";
    private static final String TEMPLATE_TEST_FAILED = TEAMCITY_PREFIX + "[testFailed timestamp = '%s' details = '%s' message = '%s' name = '%s' %s]";
    private static final String TEMPLATE_SCENARIO_FAILED = TEAMCITY_PREFIX + "[customProgressStatus timestamp='%s' type='testFailed']";
    // private static final String TEMPLATE_TEST_PENDING = TEAMCITY_PREFIX + "[testIgnored name = '%s' message = 'Skipped step' timestamp = '%s']";
    private static final String TEMPLATE_TEST_FINISHED = TEAMCITY_PREFIX + "[testFinished timestamp = '%s' duration = '%s' name = '%s']";
    private static final String TEMPLATE_ENTER_THE_MATRIX = TEAMCITY_PREFIX + "[enteredTheMatrix timestamp = '%s']";
    private static final String TEMPLATE_TEST_SUITE_STARTED = TEAMCITY_PREFIX + "[testSuiteStarted timestamp = '%s' locationHint = 'file://%s' name = '%s']";
    private static final String TEMPLATE_TEST_SUITE_FINISHED = TEAMCITY_PREFIX + "[testSuiteFinished timestamp = '%s' name = '%s']";
    private static final String TEMPLATE_SCENARIO_COUNTING_STARTED = TEAMCITY_PREFIX + "[customProgressStatus testsCategory = 'Scenarios' count = '%s' timestamp = '%s']";
    private static final String TEMPLATE_SCENARIO_COUNTING_FINISHED = TEAMCITY_PREFIX + "[customProgressStatus testsCategory = '' count = '0' timestamp = '%s']";
    private static final String TEMPLATE_SCENARIO_STARTED = TEAMCITY_PREFIX + "[customProgressStatus type = 'testStarted' timestamp = '%s']";
    private static final String TEMPLATE_SCENARIO_FINISHED = TEAMCITY_PREFIX + "[customProgressStatus type = 'testFinished' timestamp = '%s']";

}
