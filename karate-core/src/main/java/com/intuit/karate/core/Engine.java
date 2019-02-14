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
import org.w3c.dom.Node;
import com.intuit.karate.StepActions;
import cucumber.api.java.en.When;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import com.intuit.karate.core.AdhocCoverageTool;

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

    public static String getBuildDir() {
        String command = System.getProperty("sun.java.command", "");
        return command.contains("org.gradle.") ? "build" : "target";
    }

    public static FeatureResult executeFeatureSync(String env, Feature feature, String tagSelector, CallContext callContext) {
        FeatureContext featureContext = new FeatureContext(env, feature, tagSelector);
        if (callContext == null) {
            callContext = new CallContext(null, true);
        }
        ExecutionContext exec = new ExecutionContext(System.currentTimeMillis(), featureContext, callContext, null, null, null);
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

    private static String formatNanos(long nanos, DecimalFormat formatter) {
        return formatter.format(nanosToSeconds(nanos));
    }

    private static String formatMillis(double millis, DecimalFormat formatter) {
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

    public static String getClasspathResource(String name) {
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
                div(doc, "time-cell " + extraClass, formatMillis(featureResult.getDurationMillis(), formatter)));
        parent.appendChild(stepRow);
        String callArg = featureResult.getCallArgPretty();
        if (callArg != null) {
            parent.appendChild(node(doc, "div", "preformatted", callArg));
        }
    }

    private static void stepHtml(Document doc, DecimalFormat formatter, StepResult stepResult, Node parent) {
        AdhocCoverageTool.m.get("stepHtml")[0] = true;
        Step step = stepResult.getStep();
        Result result = stepResult.getResult();
        String extraClass;
        if (result.isFailed()) {
            AdhocCoverageTool.m.get("stepHtml")[1] = true;
            extraClass = "failed";
        } else if (result.isSkipped()) {
            AdhocCoverageTool.m.get("stepHtml")[2] = true;
            extraClass = "skipped";
        } else {
            AdhocCoverageTool.m.get("stepHtml")[3] = true;
            extraClass = "passed";
        }
        Node stepRow = div(doc, "step-row",
                div(doc, "step-cell " + extraClass, step.getPrefix() + ' ' + step.getText()),
                div(doc, "time-cell " + extraClass, formatNanos(result.getDurationNanos(), formatter)));
        parent.appendChild(stepRow);
        if (step.getTable() != null) {
            AdhocCoverageTool.m.get("stepHtml")[4] = true;
            Node table = node(doc, "table", null);
            parent.appendChild(table);
            for (List<String> row : step.getTable().getRows()) {
                AdhocCoverageTool.m.get("stepHtml")[5] = true;
                Node tr = node(doc, "tr", null);
                table.appendChild(tr);
                for (String cell : row) {
                    AdhocCoverageTool.m.get("stepHtml")[6] = true;
                    tr.appendChild(node(doc, "td", null, cell));
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (step.getDocString() != null) {
            AdhocCoverageTool.m.get("stepHtml")[7] = true;
            sb.append(step.getDocString());
        }
        if (stepResult.getStepLog() != null) {
            AdhocCoverageTool.m.get("stepHtml")[8] = true;
            if (sb.length() > 0) {
                AdhocCoverageTool.m.get("stepHtml")[9] = true;
                sb.append('\n');
            }
            sb.append(stepResult.getStepLog());
        }
        if (result.isFailed()) {
            AdhocCoverageTool.m.get("stepHtml")[10] = true;
            if (sb.length() > 0) {
                AdhocCoverageTool.m.get("stepHtml")[11] = true;
                sb.append('\n');
            }
            sb.append(result.getError().getMessage());
        }
        if (sb.length() > 0) {
            AdhocCoverageTool.m.get("stepHtml")[12] = true;
            parent.appendChild(node(doc, "div", "preformatted", sb.toString()));
        }
        Embed embed = stepResult.getEmbed();
        if (embed != null) {
            AdhocCoverageTool.m.get("stepHtml")[13] = true;
            Node embedNode;
            String mimeType = embed.getMimeType().toLowerCase();
            if (mimeType.contains("image")) {
                AdhocCoverageTool.m.get("stepHtml")[14] = true;
                embedNode = node(doc, "img", null);
                String src = "data:" + embed.getMimeType() + ";base64," + embed.getBase64();
                XmlUtils.addAttributes((Element) embedNode, Collections.singletonMap("src", src));
            } else if (mimeType.contains("html")) {
                AdhocCoverageTool.m.get("stepHtml")[15] = true;
                Node html;
                try {
                    AdhocCoverageTool.m.get("stepHtml")[16] = true;
                    html = XmlUtils.toXmlDoc(embed.getAsString()).getDocumentElement();
                } catch (Exception e) {
                    AdhocCoverageTool.m.get("stepHtml")[17] = true;
                    html = div(doc, null, e.getMessage());
                }
                html = doc.importNode(html, true);
                embedNode = div(doc, null, html);
            } else {
                AdhocCoverageTool.m.get("stepHtml")[18] = true;
                embedNode = div(doc, null);
                embedNode.setTextContent(embed.getAsString());
            }
            parent.appendChild(div(doc, "embed", embedNode));
        }
        List<FeatureResult> callResults = stepResult.getCallResults();
        if (callResults != null) { // this is a 'call'
            AdhocCoverageTool.m.get("stepHtml")[19] = true;
            for (FeatureResult callResult : callResults) {
                AdhocCoverageTool.m.get("stepHtml")[20] = true;
                callHtml(doc, formatter, callResult, parent);
                Node calledStepsDiv = div(doc, "scenario-steps-nested");
                parent.appendChild(calledStepsDiv);
                for (StepResult sr : callResult.getStepResults()) { // flattens all steps in called feature
                    AdhocCoverageTool.m.get("stepHtml")[21] = true;
                    stepHtml(doc, formatter, sr, calledStepsDiv);
                }
            }
        }
    }

    public static File saveResultHtml(String targetDir, FeatureResult result, String fileName) {
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        formatter.applyPattern("0.######");
        String html = getClasspathResource("report-template.html");
        String img = getClasspathResource("karate-logo.svg");
        Node svg = XmlUtils.toXmlDoc(img);
        String js = getClasspathResource("report-template-js.txt");
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
        if (fileName == null) {
            fileName = baseName + ".html";
        }
        File file = new File(targetDir + File.separator + fileName);
        String xml = "<!DOCTYPE html>\n" + XmlUtils.toString(doc, false);
        try {
            FileUtils.writeToFile(file, xml);
            System.out.println("HTML report: (paste into browser to view) | Karate version: "
                    + FileUtils.getKarateVersion() + "\n"
                    + file.toURI()
                    + "\n---------------------------------------------------------\n");
        } catch (Exception e) {
            System.out.println("html report output failed: " + e.getMessage());
        }
        return file;
    }

    private static long getElapsedTime(long startTime) {
        return System.nanoTime() - startTime;
    }

    public static File saveStatsJson(String targetDir, Results results, String fileName) {
        String json = JsonUtils.toJson(results.toMap());
        if (fileName == null) {
            fileName = "results-json.txt";
        }
        File file = new File(targetDir + File.separator + fileName);
        FileUtils.writeToFile(file, json);
        return file;
    }

    public static File saveTimelineHtml(String targetDir, Results results, String fileName) {
        Map<String, Integer> groupsMap = new LinkedHashMap();
        List<ScenarioResult> scenarioResults = results.getScenarioResults();
        List<Map> items = new ArrayList(scenarioResults.size());
        int id = 1;
        for (ScenarioResult sr : scenarioResults) {
            String threadName = sr.getThreadName();
            Integer groupId = groupsMap.get(threadName);
            if (groupId == null) {
                groupId = groupsMap.size() + 1;
                groupsMap.put(threadName, groupId);
            }
            Map<String, Object> item = new LinkedHashMap(7);
            items.add(item);
            item.put("id", id++);
            item.put("group", groupId);
            Scenario s = sr.getScenario();
            String featureName = s.getFeature().getResource().getFileNameWithoutExtension();
            String content = featureName + s.getDisplayMeta();
            item.put("content", content);
            item.put("start", sr.getStartTime());
            item.put("end", sr.getEndTime());
            item.put("title", content + " " + sr.getStartTime() + "-" + sr.getEndTime());
        }
        List<Map> groups = new ArrayList(groupsMap.size());
        groupsMap.forEach((k, v) -> {
            Map<String, Object> group = new LinkedHashMap(2);
            groups.add(group);
            group.put("id", v);
            group.put("content", k);
        });
        StringBuilder sb = new StringBuilder();
        sb.append("\nvar groups = new vis.DataSet(").append(JsonUtils.toJson(groups)).append(");").append('\n');
        sb.append("var items = new vis.DataSet(").append(JsonUtils.toJson(items)).append(");").append('\n');
        sb.append("var container = document.getElementById('visualization');\n"
                + "var timeline = new vis.Timeline(container);\n"
                + "timeline.setOptions({ groupOrder: 'content' });\n"
                + "timeline.setGroups(groups);\n"
                + "timeline.setItems(items);\n");
        if (fileName == null) {
            fileName = File.separator + "timeline.html";
        }
        File htmlFile = new File(targetDir + fileName);
        String html = getClasspathResource("timeline-template.html");
        html = html.replaceFirst("//timeline//", sb.toString());
        FileUtils.writeToFile(htmlFile, html);
        return htmlFile;
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
