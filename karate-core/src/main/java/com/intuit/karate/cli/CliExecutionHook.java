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

import com.intuit.karate.core.Engine;
import com.intuit.karate.core.ExecutionHook;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.PerfEvent;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.http.HttpRequestBuilder;
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
    }

    @Override
    public boolean beforeScenario(Scenario scenario, ScenarioContext context) {
        return true;
    }

    @Override
    public void afterScenario(ScenarioResult result, ScenarioContext context) {

    }

    @Override
    public boolean beforeFeature(Feature feature) {
        return true;
    }

    @Override
    public void afterFeature(FeatureResult result) {
        if (intellij) {
            Main.log(result);
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

}
