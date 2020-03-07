/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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

import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.Results;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import java.io.File;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class HtmlReport {

    private final Document doc;
    private final DecimalFormat formatter;
    private final String baseName;
    private final Node navContainer;

    private int stepCounter;

    private void stepHtml(StepResult stepResult, Node parent, int depth) {
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
        String refNum = ++stepCounter + "";
        Element stepContainer = div("step-container");
        stepContainer.setAttribute("id", refNum);
        stepContainer.appendChild(div("step-ref " + extraClass, refNum));
        for (int i = 0; i < depth; i++) {
            stepContainer.appendChild(div("step-indent", " "));
        }
        stepContainer.appendChild(div("step-cell " + extraClass, step.getPrefix() + ' ' + step.getText()));
        Node stepRow = div("step-row",
                stepContainer,
                div("time-cell " + extraClass, Engine.formatNanos(result.getDurationNanos(), formatter)));
        parent.appendChild(stepRow);
        if (step.getTable() != null) {
            Node table = node("table", null);
            parent.appendChild(table);
            for (List<String> row : step.getTable().getRows()) {
                Node tr = node("tr", null);
                table.appendChild(tr);
                for (String cell : row) {
                    tr.appendChild(node("td", null, cell));
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (step.getDocString() != null) {
            sb.append(step.getDocString());
        }
        if (stepResult.isShowLog() && stepResult.getStepLog() != null) {
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
            Element docStringNode = node("div", "preformatted", sb.toString());
            docStringNode.setAttribute("data-parent", refNum);
            parent.appendChild(docStringNode);
        }
        List<Embed> embeds = stepResult.getEmbeds();
        if (embeds != null) {
            for (Embed embed : embeds) {
                Element embedNode;
                String mimeType = embed.getMimeType().toLowerCase();
                if (mimeType.contains("image")) {
                    embedNode = node("img", null);
                    String src = embed.getAsHtmlData();
                    XmlUtils.addAttributes(embedNode, Collections.singletonMap("src", src));
                } else if (mimeType.contains("html")) {
                    Node html;
                    try {
                        html = XmlUtils.toXmlDoc(embed.getAsString()).getDocumentElement();
                    } catch (Exception e) {
                        html = div(null, e.getMessage());
                    }
                    html = doc.importNode(html, true);
                    embedNode = div(null, html);
                } else {
                    embedNode = div(null);
                    embedNode.setTextContent(embed.getAsString());
                }
                Element embedContainer = div("embed", embedNode);
                embedContainer.setAttribute("data-parent", refNum);
                parent.appendChild(embedContainer);
            }
        }
        List<FeatureResult> callResults = stepResult.getCallResults();
        if (callResults != null) { // this is a 'call'
            int index = 1;
            for (FeatureResult callResult : callResults) {
                callHtml(callResult, parent, depth, refNum + "." + index++);
            }
        }
    }

    private void callHtml(FeatureResult featureResult, Node parent, int depth, String callRefNum) {
        List<StepResult> stepResults = featureResult.getAllScenarioStepResultsNotHidden();
        if (stepResults.isEmpty()) {
            return;
        }
        String extraClass = featureResult.isFailed() ? "failed" : "passed";
        Element stepContainer = div("step-container");
        stepContainer.setAttribute("id", callRefNum);
        stepContainer.appendChild(div("step-ref " + extraClass, ">>"));
        for (int i = 0; i < depth; i++) {
            stepContainer.appendChild(div("step-indent", " "));
        }
        stepContainer.appendChild(div("step-cell " + extraClass, featureResult.getCallName()));
        Node stepRow = div("step-row",
                stepContainer,
                div("time-cell " + extraClass, Engine.formatMillis(featureResult.getDurationMillis(), formatter)));
        parent.appendChild(stepRow);
        String callArg = featureResult.getCallArgPretty();
        if (callArg != null) {
            Element callArgContainer = div("callarg-container");
            callArgContainer.setAttribute("data-parent", callRefNum);
            parent.appendChild(callArgContainer);
            callArgContainer.appendChild(div("step-ref", " "));
            for (int i = 0; i < depth; i++) {
                callArgContainer.appendChild(div("step-indent", " "));
            }
            callArgContainer.appendChild(node("div", "preformatted", callArg));
        }
        for (StepResult sr : stepResults) {
            stepHtml(sr, parent, depth + 1);
        }
    }

    //==========================================================================
    //
    public static File saveFeatureResult(String targetDir, FeatureResult result) {
        HtmlReport report = new HtmlReport(result);
        return report.save(targetDir);
    }

    private HtmlReport(FeatureResult result) {
        formatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        formatter.applyPattern("0.######");
        String html = getResourceAsString("report-template.html");
        doc = XmlUtils.toXmlDoc(html);
        baseName = result.getPackageQualifiedName();
        set("/html/head/title", baseName);
        Node leftnav = XmlUtils.getNodeByPath(doc, "/html/body/div/div[1]", false);
        navContainer = div("nav-container");
        leftnav.appendChild(navContainer);
        Node content = XmlUtils.getNodeByPath(doc, "/html/body/div/div[2]", false);
        for (ScenarioResult sr : result.getScenarioResults()) {
            Node scenarioDiv = div("scenario");
            content.appendChild(scenarioDiv);
            String scenarioMeta = sr.getScenario().getDisplayMeta();
            String scenarioName = sr.getScenario().getName();
            Element scenarioHeadingDiv = div("scenario-heading",
                    node("span", "scenario-keyword", sr.getScenario().getKeyword() + ": " + scenarioMeta),
                    node("span", "scenario-name", scenarioName));
            scenarioHeadingDiv.setAttribute("id", scenarioMeta);
            scenarioDiv.appendChild(scenarioHeadingDiv);
            String extraClass = sr.isFailed() ? "failed" : "passed";
            Element scenarioNav = div("scenario-nav " + extraClass);
            navContainer.appendChild(scenarioNav);
            Element scenarioLink = node("a", null, scenarioMeta + " " + scenarioName);
            scenarioNav.appendChild(scenarioLink);
            scenarioLink.setAttribute("href", "#" + scenarioMeta);
            for (StepResult stepResult : sr.getStepResults()) {
                stepHtml(stepResult, scenarioDiv, 0);
            }
        }
    }

    private File save(String targetDir) {
        String fileName = baseName + ".html";
        File file = new File(targetDir + File.separator + fileName);
        String xml = "<!DOCTYPE html>\n" + XmlUtils.toString(doc, false);
        try {
            initStaticResources(targetDir); // TODO improve init
            FileUtils.writeToFile(file, xml);
            System.out.println("\nHTML report: (paste into browser to view) | Karate version: "
                    + FileUtils.getKarateVersion() + "\n"
                    + file.toURI()
                    + "\n---------------------------------------------------------\n");
        } catch (Exception e) {
            System.out.println("html report output failed: " + e.getMessage());
        }
        return file;
    }

    private static final String[] RESOURCES = new String[]{
        "bootstrap.min.css",
        "bootstrap.min.js",
        "jquery.min.js",
        "jquery.tablesorter.min.js",
        "karate-logo.png",
        "karate-logo.svg",
        "karate-report.css",
        "karate-report.js"
    };

    private void initStaticResources(String targetDir) {
        String resPath = targetDir + File.separator + "res" + File.separator;
        File res = new File(resPath);
        if (res.exists()) {
            return;
        }
        ClassLoader cl = getClass().getClassLoader();
        for (String name : RESOURCES) {
            byte[] bytes = FileUtils.toBytes(cl.getResourceAsStream("res/" + name));
            File dest = new File(resPath + name);
            FileUtils.writeToFile(dest, bytes);
        }
    }

    private static String getResourceAsString(String name) {
        return FileUtils.toString(HtmlReport.class.getClassLoader().getResourceAsStream(name));
    }

    private void set(String path, String value) {
        XmlUtils.setByPath(doc, path, value);
    }

    private Element div(String clazz, String value) {
        return node("div", clazz, value);
    }

    private Element div(String clazz, Node... childNodes) {
        Element parent = node("div", clazz);
        for (Node child : childNodes) {
            parent.appendChild(child);
        }
        return parent;
    }

    private Element node(String name, String clazz, String text) {
        return XmlUtils.createElement(doc, name, text, clazz == null ? null : Collections.singletonMap("class", clazz));
    }

    private Element node(String name, String clazz) {
        return node(name, clazz, null);
    }

    public static File saveTimeline(String targetDir, Results results, String fileName) {
        Map<String, Integer> groupsMap = new LinkedHashMap();
        List<ScenarioResult> scenarioResults = results.getScenarioResults();
        List<Map> items = new ArrayList(scenarioResults.size());
        int id = 1;
        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
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
            String startTime = dateFormat.format(new Date(sr.getStartTime()));
            String endTime = dateFormat.format(new Date(sr.getEndTime()));
            content = content + " " + startTime + "-" + endTime;
            String scenarioTitle = StringUtils.trimToEmpty(s.getName());
            if (!scenarioTitle.isEmpty()) {
                content = content + " " + scenarioTitle;
            }
            item.put("title", content);
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
        String html = getResourceAsString("timeline-template.html");
        html = html.replaceFirst("//timeline//", sb.toString());
        FileUtils.writeToFile(htmlFile, html);
        return htmlFile;
    }

}
