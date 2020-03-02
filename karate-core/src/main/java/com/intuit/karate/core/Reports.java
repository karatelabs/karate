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
public class Reports {

    private static String getResourceAsString(String name) {
        return FileUtils.toString(Reports.class.getClassLoader().getResourceAsStream(name));
    }

    private static void set(Document doc, String path, String value) {
        XmlUtils.setByPath(doc, path, value);
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

    private static void callHtml(Document doc, DecimalFormat formatter, FeatureResult featureResult, Node parent, int depth) {
        String extraClass = featureResult.isFailed() ? "failed" : "passed";
        Node stepContainer = div(doc, "step-container");
        for (int i = 0; i < depth; i++) {
            stepContainer.appendChild(div(doc, "step-indent", " "));
        }                 
        stepContainer.appendChild(div(doc, "step-cell " + extraClass, featureResult.getCallName()));
        Node stepRow = div(doc, "step-row",
                stepContainer,
                div(doc, "time-cell " + extraClass, Engine.formatMillis(featureResult.getDurationMillis(), formatter)));
        parent.appendChild(stepRow);
        String callArg = featureResult.getCallArgPretty();
        if (callArg != null) {
            parent.appendChild(node(doc, "div", "preformatted", callArg));
        }
    }
        
    private static void stepHtml(Document doc, DecimalFormat formatter, StepResult stepResult, Node parent, int depth) {
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
        Node stepContainer = div(doc, "step-container");
        for (int i = 0; i < depth; i++) {
            stepContainer.appendChild(div(doc, "step-indent", " "));
        }        
        stepContainer.appendChild(div(doc, "step-cell " + extraClass, step.getPrefix() + ' ' + step.getText()));
        Node stepRow = div(doc, "step-row", 
                stepContainer,
                div(doc, "time-cell " + extraClass, Engine.formatNanos(result.getDurationNanos(), formatter)));
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
            parent.appendChild(node(doc, "div", "preformatted", sb.toString()));
        }
        List<Embed> embeds = stepResult.getEmbeds();
        if (embeds != null) {
            for (Embed embed : embeds) {
                Node embedNode;
                String mimeType = embed.getMimeType().toLowerCase();
                if (mimeType.contains("image")) {
                    embedNode = node(doc, "img", null);
                    String src = embed.getAsHtmlData();
                    XmlUtils.addAttributes((Element) embedNode, Collections.singletonMap("src", src));
                } else if (mimeType.contains("html")) {
                    Node html;
                    try {
                        html = XmlUtils.toXmlDoc(embed.getAsString()).getDocumentElement();
                    } catch (Exception e) {
                        html = div(doc, null, e.getMessage());
                    }
                    html = doc.importNode(html, true);
                    embedNode = div(doc, null, html);
                } else {
                    embedNode = div(doc, null);
                    embedNode.setTextContent(embed.getAsString());
                }
                parent.appendChild(div(doc, "embed", embedNode));
            }
        }
        List<FeatureResult> callResults = stepResult.getCallResults();
        if (callResults != null) { // this is a 'call'
            for (FeatureResult callResult : callResults) {
                callHtml(doc, formatter, callResult, parent, depth);
                for (StepResult sr : callResult.getStepResults()) { // flattens all steps in called feature
                    stepHtml(doc, formatter, sr, parent, depth + 1);
                }
            }
        }
    }    
    
    private static final String[] RESOURCES = new String[] {
        "bootstrap.min.css", 
        "bootstrap.min.js", 
        "jquery.min.js", 
        "jquery.tablesorter.min.js",
        "karate-logo.png",
        "karate-logo.svg",
        "karate-report.css",
        "karate-report.js"
    };
    
    public static void initStaticResources(String targetDir) {
        String resPath = targetDir + File.separator + "res" + File.separator;
        File res = new File(resPath);
        if (res.exists()) {
            return;
        }
        ClassLoader cl = Reports.class.getClassLoader();
        for (String name : RESOURCES) {
            byte[] bytes = FileUtils.toBytes(cl.getResourceAsStream("res/" + name));
            File dest = new File(resPath + name);
            FileUtils.writeToFile(dest, bytes);
        }
    }

    public static File saveResultHtml(String targetDir, FeatureResult result, String fileName) {
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        formatter.applyPattern("0.######");
        String html = getResourceAsString("report-template.html");
        Document doc = XmlUtils.toXmlDoc(html);
        String baseName = result.getPackageQualifiedName();
        set(doc, "/html/head/title", baseName);
        Node content = XmlUtils.getNodeByPath(doc, "/html/body/div/div[2]", false);
        for (ScenarioResult sr : result.getScenarioResults()) {
            Node scenarioDiv = div(doc, "scenario");
            content.appendChild(scenarioDiv);
            Node scenarioHeadingDiv = div(doc, "scenario-heading",
                    node(doc, "span", "scenario-keyword", sr.getScenario().getKeyword() + ": " + sr.getScenario().getDisplayMeta()),
                    node(doc, "span", "scenario-name", sr.getScenario().getName()));
            scenarioDiv.appendChild(scenarioHeadingDiv);
            for (StepResult stepResult : sr.getStepResults()) {
                stepHtml(doc, formatter, stepResult, scenarioDiv, 0);
            }
        }
        if (fileName == null) {
            fileName = baseName + ".html";
        }
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

    public static File saveTimelineHtml(String targetDir, Results results, String fileName) {
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
