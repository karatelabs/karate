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

import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.StepDefs;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.exception.KarateAbortException;
import com.intuit.karate.exception.KarateException;
import cucumber.api.DataTable;
import cucumber.api.java.en.When;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class Engine {

    private static final List<MethodPattern> PATTERNS = new ArrayList();

    private static final Consumer<Runnable> SYNC_EXECUTOR = r -> r.run();
    private static final Consumer<Void> NO_OP = r -> {};

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

    public static String getBuildDir() {
        String command = System.getProperty("sun.java.command", "");
        return command.contains("org.gradle.") ? "build" : "target";
    }

    public static FeatureResult execute(String envString, Feature feature, String tagSelector, CallContext callContext) {
        File file = feature.getFile();
        ScriptEnv env = ScriptEnv.forEnvTagsAndFeatureFile(envString, tagSelector, file);
        if (callContext == null) {
            callContext = new CallContext(null, true);
        }
        boolean enableFileLogAppender = callContext.asyncSystem == null;
        ExecutionContext exec = new ExecutionContext(feature, env, callContext, enableFileLogAppender);
        FeatureExecutionUnit unit = new FeatureExecutionUnit(exec);
        unit.submit(SYNC_EXECUTOR, NO_OP);
        return exec.result;
    }

    public static Result execute(Step step, StepDefs stepDefs) {
        String text = step.getText();
        String featurePath = step.getScenario() == null ? "(unknown)" : step.getScenario().getFeature().getRelativePath();
        List<MethodMatch> matches = findMethodsMatching(text);
        if (matches.isEmpty()) {
            KarateException e = new KarateException("no step-definition method match found for: " + text);
            return Result.failed(0, e, featurePath, step);
        } else if (matches.size() > 1) {
            KarateException e = new KarateException("more than one step-definition method matched: " + text + " - " + matches);
            return Result.failed(0, e, featurePath, step);
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
        long startTime = System.nanoTime();
        try {
            match.method.invoke(stepDefs, args);
            return Result.passed(getElapsedTime(startTime));
        } catch (InvocationTargetException e) { // target will be KarateException
            if (e.getTargetException() instanceof KarateAbortException) {
                return Result.aborted(getElapsedTime(startTime));
            } else {
                return Result.failed(getElapsedTime(startTime), e.getTargetException(), featurePath, step);
            }
        } catch (Exception e) {
            return Result.failed(getElapsedTime(startTime), e, featurePath, step);
        }
    }

    public static File saveResultJson(String targetDir, FeatureResult result) {
        List<FeatureResult> single = Collections.singletonList(result);
        String json = JsonUtils.toPrettyJsonString(JsonUtils.toJsonDoc(single));
        File file = new File(targetDir + "/" + result.getPackageQualifiedName() + ".json");
        FileUtils.writeToFile(file, json);
        return file;
    }

    private static String formatNanos(long nanos, DecimalFormat formatter) {
        double seconds = (double) nanos / 1000000000;
        return formatter.format(seconds);
    }

    private static Throwable appendSteps(List<StepResult> steps, StringBuilder sb) {
        Throwable error = null;
        for (StepResult sr : steps) {
            int length = sb.length();
            sb.append(sr.getKeyword());
            sb.append(' ');
            sb.append(sr.getName());
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
        root.setAttribute("name", result.getName()); // will be uri
        root.setAttribute("skipped", "0");
        String baseName = result.getPackageQualifiedName();
        int testCount = 0;
        int failureCount = 0;
        long totalDuration = 0;
        Iterator<ResultElement> iterator = result.getElements().iterator();
        ResultElement prev = null;
        StringBuilder sb = new StringBuilder();
        while (iterator.hasNext()) {
            ResultElement element = iterator.next();
            totalDuration += element.getDuration();
            if (element.isFailed()) {
                failureCount++;
            }
            Throwable error;
            if (element.isBackground()) {
                sb.setLength(0);
                prev = element;
                appendSteps(element.getSteps(), sb);
            } else {
                Element testCase = doc.createElement("testcase");
                root.appendChild(testCase);
                testCase.setAttribute("classname", baseName);
                testCount++;
                long duration = element.getDuration();
                if (prev != null) {
                    duration += prev.getDuration();
                } else {
                    sb.setLength(0);
                }
                error = appendSteps(element.getSteps(), sb);
                String name = element.getName();
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
                prev = null;
            }
        }
        root.setAttribute("tests", testCount + "");
        root.setAttribute("failures", failureCount + "");
        root.setAttribute("time", formatNanos(totalDuration, formatter));
        String xml = XmlUtils.toString(doc, true);
        File file = new File(targetDir + "/" + baseName + ".xml");
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

    private static void appendLog(Document doc, Node parent, String log) {
        if (!log.isEmpty()) {
            Node pre = node(doc, "div", "preformatted");
            pre.setTextContent(log);
            parent.appendChild(pre);
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
        Iterator<ResultElement> iterator = result.getElements().iterator();
        ResultElement prev = null;
        while (iterator.hasNext()) {
            ResultElement element = iterator.next();
            if (element.isBackground()) {
                prev = element;
            } else {
                Node scenarioDiv = div(doc, "scenario");
                append(doc, "/html/body/div", scenarioDiv);
                Node scenarioHeadingDiv = div(doc, "scenario-heading",
                        node(doc, "span", "scenario-keyword", element.getKeyword() + ": "),
                        node(doc, "span", "scenario-name", element.getName()));
                scenarioDiv.appendChild(scenarioHeadingDiv);
                prev = null;
            }
        }
        File file = new File(targetDir + "/" + baseName + ".html");
        String xml = XmlUtils.toString(doc, true);
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
