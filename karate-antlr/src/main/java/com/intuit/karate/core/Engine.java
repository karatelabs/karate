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
package com.intuit.karate.core;

import com.intuit.karate.StepDefs;
import com.intuit.karate.StringUtils;
import com.intuit.karate.exception.KarateAbortException;
import com.intuit.karate.exception.KarateException;
import cucumber.api.DataTable;
import cucumber.api.java.en.When;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Engine {

    private static final Logger logger = LoggerFactory.getLogger(Engine.class);

    private static final List<MethodPattern> PATTERNS = new ArrayList();

    static {
        for (Method method : StepDefs.class.getMethods()) {
            When when = method.getDeclaredAnnotation(When.class);
            if (when != null) {
                MethodPattern pattern = new MethodPattern(method, when);
                PATTERNS.add(pattern);
            }
        }     
    }

    private Engine() {
        // only static methods
    }
    
    public static FeatureResult execute(Feature feature, StepDefs stepDefs) {
        FeatureResult result = new FeatureResult(feature);
        for (FeatureSection section : feature.getSections()) {
            if (section.isOutline()) {
                List<Scenario> scenarios = section.getScenarioOutline().getScenarios();
                for (Scenario scenario : scenarios) {
                    execute(feature, scenario, stepDefs, result);
                }
            } else {
                Scenario scenario = section.getScenario();
                execute(feature, scenario, stepDefs, result);
            }
        }
        return result;
    }

    private static void execute(Feature feature, Scenario scenario, StepDefs stepDefs, FeatureResult featureResult) {
        boolean stopped = false;
        Background background = feature.getBackground();
        if (background != null) {
            BackgroundResult backgroundResult = new BackgroundResult(background);
            featureResult.addResult(backgroundResult);
            stopped = execute(background.getSteps(), stepDefs, backgroundResult, stopped);            
        }
        ScenarioResult scenarioResult = new ScenarioResult(scenario);
        featureResult.addResult(scenarioResult);
        execute(scenario.getSteps(), stepDefs, scenarioResult, stopped);        
    }

    private static boolean execute(List<Step> steps, StepDefs stepDefs, ResultCollector collector, boolean stopped) {
        for (Step step : steps) {
            if (stopped) {
                collector.addStepResult(new StepResult(step, Result.skipped()));
                continue;
            }
            String text = step.getText();
            List<MethodMatch> matches = Engine.findMethodsMatching(text);
            if (matches.isEmpty()) {
                String message = "no step-definition method match found for: " + text;
                stepDefs.getContext().logger.error(message);
                Result result = Result.failed(0, new KarateException(message));
                collector.addStepResult(new StepResult(step, result));
                stopped = true;
                continue;
            } else if (matches.size() > 1) {
                String message = "more than one step-definition method matched: " + text + " - " + matches;
                stepDefs.getContext().logger.error(message);
                Result result = Result.failed(0, new KarateException(message));
                collector.addStepResult(new StepResult(step, result));
                stopped = true;
                continue;
            }
            MethodMatch match = matches.get(0);
            Object last;
            if (step.getDocString() != null) {
                last = step.getDocString();
            } else if (step.getTable() != null) {
                last = DataTable.create(step.getTable().getRows());
            } else {
                last = null;
            }
            Object[] args = match.convertArgs(last);
            if (logger.isTraceEnabled()) {
                logger.debug("MATCH: {}, {}, {}", text, match, Arrays.asList(args));
            }
            Result result;
            long startTime = System.nanoTime();
            try {
                match.method.invoke(stepDefs, args);
                result = Result.passed(getElapsedTime(startTime));
            } catch (KarateAbortException ke) {
                result = Result.passed(getElapsedTime(startTime));
                stopped = true;
            } catch (InvocationTargetException e) { // will be KarateException
                result = Result.failed(getElapsedTime(startTime), e.getTargetException());
                stopped = true;
            } catch (Exception e) {
                result = Result.failed(getElapsedTime(startTime), e);
                stopped = true;
            }
            collector.addStepResult(new StepResult(step, result));
        }
        return stopped;
    }

    private static long getElapsedTime(long startTime) {
        return System.nanoTime() - startTime;
    }    

    public static List<MethodMatch> findMethodsMatching(String text) {
        List<MethodMatch> matches = new ArrayList(1);
        for (MethodPattern pattern : PATTERNS) {
            List<String> args = pattern.match(text);
            if (args != null) {
                matches.add(new MethodMatch(pattern.method, args));
            }
        }
        return matches;
    }

    public static String toIdString(String name) {
        return name.replaceAll("[\\s_]", "-").toLowerCase();
    }

    public static StringUtils.Pair splitByFirstLineFeed(String text) {
        String left = "";
        String right = "";
        if (text != null) {
            int pos = text.indexOf('\n');
            if (pos != -1) {
                left = text.substring(0, pos).trim();
                right = text.substring(pos).trim();                
            } else {
                left = text.trim();
            }
        }
        return StringUtils.pair(left, right);
    }

}
