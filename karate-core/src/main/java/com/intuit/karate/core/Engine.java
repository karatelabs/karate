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

import com.intuit.karate.Action;
import com.intuit.karate.Actions;
import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.FeatureContext;
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
import org.w3c.dom.Node;
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

    private static final Runnable NO_OP = () -> {
        // no op
    };

    private static final double MILLION = 1000000;
    private static final double BILLION = 1000000000;

    public static double nanosToSeconds(long nanos) {
        return (double) nanos / BILLION;
    }

    public static double nanosToMillis(long nanos) {
        return (double) nanos / 1000000;
    }

    public static String getBuildDir() {
        String command = System.getProperty("sun.java.command", "");
        return command.contains("org.gradle.") ? "build" : "target";
    }

    public static FeatureResult executeFeatureSync(Feature feature, String tagSelector, CallContext callContext) {
        FeatureContext featureContext = new FeatureContext(feature, tagSelector);
        if (callContext == null) {
            callContext = new CallContext(null, true);
        }
        ExecutionContext exec = new ExecutionContext(featureContext, callContext, null);
        FeatureExecutionUnit unit = new FeatureExecutionUnit(exec);
        unit.submit(NO_OP);
        return exec.result;
    }

    private static final String UNKNOWN = "-unknown-";

    public static String getFeatureName(Step step) {
        if (step.getScenario() == null) {
            return UNKNOWN;
        }
        return step.getScenario().getFeature().getPath().getFileName().toString();
    }

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
        Object[] args = match.convertArgs(last);
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

    public static File saveResultJson(String targetDir, FeatureResult result) {
        List<Map> single = Collections.singletonList(result.toMap());
        String json = JsonUtils.toJson(single);
        File file = new File(targetDir + File.separator + result.getPackageQualifiedName() + ".json");
        FileUtils.writeToFile(file, json);
        return file;
    }

    private static String formatNanos(long nanos, DecimalFormat formatter) {
        return formatter.format(nanosToSeconds(nanos));
    }

    private static String formatSeconds(double seconds, DecimalFormat formatter) {
        return formatter.format(seconds);
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

    public static File saveResultXml(String targetDir, FeatureResult result) {
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
            totalDuration += sr.getDuration();
            if (sr.isFailed()) {
                failureCount++;
            }
            Element testCase = doc.createElement("testcase");
            root.appendChild(testCase);
            testCase.setAttribute("classname", baseName);
            testCount++;
            long duration = sr.getDuration();
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
        File file = new File(targetDir + File.separator + baseName + ".xml");
        FileUtils.writeToFile(file, xml);
        return file;
    }

    private static String getFile(String name) {
        return FileUtils.toString(Engine.class.getClassLoader().getResourceAsStream(name));
    }

    private static void set(Document doc, String path, String value) {
        XmlUtils.setByPath(doc, path, value);
    }

    private static void append(Document doc, String path, Node node) {
        Node temp = XmlUtils.getNodeByPath(doc, path, true);
        temp.appendChild(node);
    }

    private static Node div(Document doc, String clazz, String value) {
        return node(doc, "div", clazz, value);
    }

    private static Node div(Document doc, String clazz, Node... childNodes) {
        Node parent = node(doc, "div", clazz);
        for (Node child : childNodes) {
            parent.appendChild(child);
        }
        return parent;
    }

    private static Node node(Document doc, String name, String clazz, String text) {
        return XmlUtils.createElement(doc, name, text, clazz == null ? null : Collections.singletonMap("class", clazz));
    }

    private static Node node(Document doc, String name, String clazz) {
        return node(doc, name, clazz, null);
    }

    private static void callHtml(Document doc, DecimalFormat formatter, FeatureResult featureResult, Node parent) {
        String extraClass = featureResult.isFailed() ? "failed" : "passed";
        Node stepRow = div(doc, "step-row",
                div(doc, "step-cell " + extraClass, featureResult.getCallName()),
                div(doc, "time-cell " + extraClass, formatSeconds(featureResult.getDuration(), formatter)));
        parent.appendChild(stepRow);
        String callArg = featureResult.getCallArgPretty();
        if (callArg != null) {
            parent.appendChild(node(doc, "div", "preformatted", callArg));
        }
    }

    private static void stepHtml(Document doc, DecimalFormat formatter, StepResult stepResult, Node parent) {
        Step step = stepResult.getStep();
        Result result = stepResult.getResult();
        String extraClass;
        if (result.isFailed()) {
            extraClass = "failed";
        } else if (result.isSkipped()) {
            extraClass = "skipped";
        } else {
            extraClass = "passed";
        }
        Node stepRow = div(doc, "step-row",
                div(doc, "step-cell " + extraClass, step.getPrefix() + ' ' + step.getText()),
                div(doc, "time-cell " + extraClass, formatNanos(result.getDuration(), formatter)));
        parent.appendChild(stepRow);
        if (step.getTable() != null) {
            Node table = node(doc, "table", null);
            parent.appendChild(table);
            for (List<String> row : step.getTable().getRows()) {
                Node tr = node(doc, "tr", null);
                table.appendChild(tr);
                for (String cell : row) {
                    tr.appendChild(node(doc, "td", null, cell));
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (step.getDocString() != null) {
            sb.append(step.getDocString());
        }
        if (stepResult.getStepLog() != null) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(stepResult.getStepLog());
        }
        if (result.isFailed()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(result.getError().getMessage());
        }
        if (sb.length() > 0) {
            parent.appendChild(node(doc, "div", "preformatted", sb.toString()));
        }
        List<FeatureResult> callResults = stepResult.getCallResults();
        if (callResults != null) { // this is a 'call'
            for (FeatureResult callResult : callResults) {
                callHtml(doc, formatter, callResult, parent);
                Node calledStepsDiv = div(doc, "scenario-steps-nested");
                parent.appendChild(calledStepsDiv);
                for (StepResult sr : callResult.getStepResults()) { // flattens all steps in called feature
                    stepHtml(doc, formatter, sr, calledStepsDiv);
                }
            }
        }

    }

    public static File saveResultHtml(String targetDir, FeatureResult result) {
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        formatter.applyPattern("0.######");
        String html = getFile("report-template.html");
        String img = getFile("karate-logo.svg");
        Node svg = XmlUtils.toXmlDoc(img);
        String js = getFile("report-template.js");
        Document doc = XmlUtils.toXmlDoc(html);
        XmlUtils.setByPath(doc, "/html/body/img", svg);
        String baseName = result.getPackageQualifiedName();
        set(doc, "/html/head/title", baseName);
        set(doc, "/html/head/script", js);
        for (ScenarioResult sr : result.getScenarioResults()) {
            Node scenarioDiv = div(doc, "scenario");
            append(doc, "/html/body/div", scenarioDiv);
            Node scenarioHeadingDiv = div(doc, "scenario-heading",
                    node(doc, "span", "scenario-keyword", sr.getScenario().getKeyword() + ": " + sr.getScenario().getDisplayMeta()),
                    node(doc, "span", "scenario-name", sr.getScenario().getName()));
            scenarioDiv.appendChild(scenarioHeadingDiv);
            for (StepResult stepResult : sr.getStepResults()) {
                stepHtml(doc, formatter, stepResult, scenarioDiv);
            }
        }
        File file = new File(targetDir + File.separator + baseName + ".html");
        String xml = "<!DOCTYPE html>\n" + XmlUtils.toString(doc, false);
        try {
            FileUtils.writeToFile(file, xml);
            System.out.println("Karate version: " + FileUtils.getKarateVersion());
            System.out.println("html report: (paste into browser to view)\n"
                    + "-----------------------------------------\n"
                    + file.toURI() + '\n');
        } catch (Exception e) {
            System.out.println("html report output failed: " + e.getMessage());
        }
        return file;
    }

    private static long getElapsedTime(long startTime) {
        return System.nanoTime() - startTime;
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

    public static String fromCucumberOptionsTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return fromCucumberOptionsTags(tags.toArray(new String[]{}));
    }

    public static String fromCucumberOptionsTags(String... tags) {
        if (tags == null || tags.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.length; i++) {
            String and = tags[i];
            if (and.startsWith("~")) {
                sb.append("not('").append(and.substring(1)).append("')");
            } else {
                sb.append("anyOf(");
                List<String> or = StringUtils.split(and, ',');
                for (String tag : or) {
                    sb.append('\'').append(tag).append('\'').append(',');
                }
                sb.setLength(sb.length() - 1);
                sb.append(')');
            }
            if (i < (tags.length - 1)) {
                sb.append(" && ");
            }
        }
        return sb.toString();
    }

}
