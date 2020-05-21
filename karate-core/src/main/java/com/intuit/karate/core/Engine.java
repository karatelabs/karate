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

import com.intuit.karate.Actions;
import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.Results;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.exception.KarateAbortException;
import com.intuit.karate.exception.KarateException;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.intuit.karate.StepActions;
import cucumber.api.java.en.When;
import java.util.Collection;
import java.util.HashMap;

/**
 *
 * @author pthomas3
 */
public class Engine {

    private Engine() {
        // only static methods
    }

    private static final Collection<MethodPattern> PATTERNS;

    static {
        Map<String, MethodPattern> temp = new HashMap();
        List<MethodPattern> overwrite = new ArrayList();
        for (Method method : StepActions.class.getMethods()) {
            When when = method.getDeclaredAnnotation(When.class);
            if (when != null) {
                String regex = when.value();
                temp.put(regex, new MethodPattern(method, regex));
            } else {
                Action action = method.getDeclaredAnnotation(Action.class);
                if (action != null) {
                    String regex = action.value();
                    overwrite.add(new MethodPattern(method, regex));
                }
            }
        }
        for (MethodPattern mp : overwrite) {
            temp.put(mp.regex, mp);
        }
        PATTERNS = temp.values();
    }

    private static final double MILLION = 1000000;
    private static final double BILLION = 1000000000;

    public static double nanosToSeconds(long nanos) {
        return (double) nanos / BILLION;
    }

    public static double nanosToMillis(long nanos) {
        return (double) nanos / MILLION;
    }

    public static FeatureResult executeFeatureSync(String env, Feature feature, String tagSelector, CallContext callContext) {
        FeatureContext featureContext = new FeatureContext(env, feature, tagSelector);
        if (callContext == null) {
            callContext = new CallContext(null, true);
        }
        ExecutionContext exec = new ExecutionContext(null, System.currentTimeMillis(), featureContext, callContext, null, null, null);
        FeatureExecutionUnit unit = new FeatureExecutionUnit(exec);
        unit.run();
        return exec.result;
    }

    private static final String UNKNOWN = "-unknown-";

    public static String getFeatureName(Step step) {
        if (step.getScenario() == null) {
            return UNKNOWN;
        }
        return step.getScenario().getFeature().getPath().getFileName().toString();
    }

    public static final ThreadLocal<ScenarioContext> THREAD_CONTEXT = new ThreadLocal();

    public static Result executeStep(Step step, Actions actions) {
        String text = step.getText();
        List<MethodMatch> matches = findMethodsMatching(text);
        if (matches.isEmpty()) {
            KarateException e = new KarateException("no step-definition method match found for: " + text);
            return Result.failed(0, e, step);
        } else if (matches.size() > 1) {
            KarateException e = new KarateException("more than one step-definition method matched: " + text + " - " + matches);
            return Result.failed(0, e, step);
        }
        MethodMatch match = matches.get(0);
        Object last;
        if (step.getDocString() != null) {
            last = step.getDocString();
        } else if (step.getTable() != null) {
            last = step.getTable().getRowsAsMaps();
        } else {
            last = null;
        }
        Object[] args;
        try {
            args = match.convertArgs(last);
        } catch (Exception ee) { // edge case where user error causes [request =] to match [request docstring]
            KarateException e = new KarateException("no step-definition method match found for: " + text);
            return Result.failed(0, e, step);
        }
        long startTime = System.nanoTime();
        try {            
            match.method.invoke(actions, args);
            return Result.passed(getElapsedTime(startTime));
        } catch (InvocationTargetException e) { // target will be KarateException
            if (e.getTargetException() instanceof KarateAbortException) {
                return Result.aborted(getElapsedTime(startTime));
            } else {
                return Result.failed(getElapsedTime(startTime), e.getTargetException(), step);
            }
        } catch (Exception e) {
            return Result.failed(getElapsedTime(startTime), e, step);
        }
    }

    public static File saveResultJson(String targetDir, FeatureResult result, String fileName) {
        List<Map> single = Collections.singletonList(result.toMap());
        String json = JsonUtils.toJson(single);
        if (fileName == null) {
            fileName = result.getPackageQualifiedName() + ".json";
        }
        File file = new File(targetDir + File.separator + fileName);
        FileUtils.writeToFile(file, json);
        return file;
    }

    public static String formatNanos(long nanos, DecimalFormat formatter) {
        return formatter.format(nanosToSeconds(nanos));
    }

    public static String formatMillis(double millis, DecimalFormat formatter) {
        return formatter.format(millis / 1000);
    }

    private static Throwable appendSteps(List<StepResult> steps, StringBuilder sb) {
        Throwable error = null;
        for (StepResult sr : steps) {
            int length = sb.length();
            sb.append(sr.getStep().getPrefix());
            sb.append(' ');
            sb.append(sr.getStep().getText());
            sb.append(' ');
            do {
                sb.append('.');
            } while (sb.length() - length < 75);
            sb.append(' ');
            sb.append(sr.getResult().getStatus());
            sb.append('\n');
            if (sr.getResult().isFailed()) {
                sb.append("\nStack Trace:\n");
                StringWriter sw = new StringWriter();
                error = sr.getResult().getError();
                error.printStackTrace(new PrintWriter(sw));
                sb.append(sw.toString());
                sb.append('\n');
            }
        }
        return error;
    }

    public static File saveResultXml(String targetDir, FeatureResult result, String fileName) {
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        formatter.applyPattern("0.######");
        Document doc = XmlUtils.newDocument();
        Element root = doc.createElement("testsuite");
        doc.appendChild(root);
        root.setAttribute("name", result.getDisplayUri()); // will be uri
        root.setAttribute("skipped", "0");
        String baseName = result.getPackageQualifiedName();
        int testCount = 0;
        int failureCount = 0;
        long totalDuration = 0;
        Throwable error;
        Iterator<ScenarioResult> iterator = result.getScenarioResults().iterator();
        StringBuilder sb = new StringBuilder();
        while (iterator.hasNext()) {
            ScenarioResult sr = iterator.next();
            totalDuration += sr.getDurationNanos();
            if (sr.isFailed()) {
                failureCount++;
            }
            Element testCase = doc.createElement("testcase");
            root.appendChild(testCase);
            testCase.setAttribute("classname", baseName);
            testCount++;
            long duration = sr.getDurationNanos();
            error = appendSteps(sr.getStepResults(), sb);
            String name = sr.getScenario().getName();
            if (StringUtils.isBlank(name)) {
                name = testCount + "";
            }
            testCase.setAttribute("name", name);
            testCase.setAttribute("time", formatNanos(duration, formatter));
            Element stepsHolder;
            if (error != null) {
                stepsHolder = doc.createElement("failure");
                stepsHolder.setAttribute("message", error.getMessage());
            } else {
                stepsHolder = doc.createElement("system-out");
            }
            testCase.appendChild(stepsHolder);
            stepsHolder.setTextContent(sb.toString());
        }
        root.setAttribute("tests", testCount + "");
        root.setAttribute("failures", failureCount + "");
        root.setAttribute("time", formatNanos(totalDuration, formatter));
        String xml = XmlUtils.toString(doc, true);
        if (fileName == null) {
            fileName = baseName + ".xml";
        }
        File file = new File(targetDir + File.separator + fileName);
        FileUtils.writeToFile(file, xml);
        return file;
    }

    private static long getElapsedTime(long startTime) {
        return System.nanoTime() - startTime;
    }

    public static File saveStatsJson(String targetDir, Results results) {
        String json = JsonUtils.toJson(results.toMap());
        String fileName = "results-json.txt";
        File file = new File(targetDir + File.separator + fileName);
        FileUtils.writeToFile(file, json);
        return file;
    }

    private static List<MethodMatch> findMethodsMatching(String text) {
        List<MethodMatch> matches = new ArrayList(1);
        for (MethodPattern pattern : PATTERNS) {
            List<String> args = pattern.match(text);
            if (args != null) {
                matches.add(new MethodMatch(pattern.method, args));
            }
        }
        return matches;
    }

}
