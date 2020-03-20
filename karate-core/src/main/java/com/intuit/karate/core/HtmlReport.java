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
public abstract class HtmlReport {

    protected final Document doc;
    protected final Node navContainer;
    protected final Node contentContainer;
    protected final DecimalFormat formatter;
    protected final String dateString;

    public HtmlReport() {
        String html = getResourceAsString("report-template.html");
        doc = XmlUtils.toXmlDoc(html);
        formatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        formatter.applyPattern("0");
        Node leftNav = XmlUtils.getNodeByPath(doc, "/html/body/div/div[1]", false);
        navContainer = div("nav-container");
        leftNav.appendChild(navContainer);
        contentContainer = XmlUtils.getNodeByPath(doc, "/html/body/div/div[2]", false);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a");
        dateString = sdf.format(new Date());
        setById("nav-date", dateString);
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

    protected static void copyFile(ClassLoader cl, String srcPath, String destPath) {
        byte[] bytes = FileUtils.toBytes(cl.getResourceAsStream(srcPath));
        File dest = new File(destPath);
        FileUtils.writeToFile(dest, bytes);
    }

    protected void initStaticResources(String targetDir) {
        String resPath = targetDir + File.separator + "res" + File.separator;
        File res = new File(resPath);
        if (res.exists()) {
            return;
        }
        ClassLoader cl = getClass().getClassLoader();
        for (String name : RESOURCES) {
            copyFile(cl, "res/" + name, resPath + name);
        }
        copyFile(cl, "favicon.ico", targetDir + File.separator + "favicon.ico");
    }

    protected static String getResourceAsString(String name) {
        return FileUtils.toString(HtmlFeatureReport.class.getClassLoader().getResourceAsStream(name));
    }

    protected void set(String path, String value) {
        XmlUtils.setByPath(doc, path, value);
    }

    protected void setById(String id, String value) {
        String path = "//div[@id='" + id + "']";
        Node node = XmlUtils.getNodeByPath(doc, path, false);
        if (node != null) {
            node.setTextContent(value);
        }
    }

    protected Element div(String clazz, String value) {
        return node("div", clazz, value);
    }

    protected Element div(String clazz, Node... childNodes) {
        Element parent = node("div", clazz);
        for (Node child : childNodes) {
            parent.appendChild(child);
        }
        return parent;
    }

    protected Element node(String name, String clazz, String text) {
        return XmlUtils.createElement(doc, name, text, clazz == null ? null : Collections.singletonMap("class", clazz));
    }

    protected Element node(String name, String clazz) {
        return node(name, clazz, null);
    }

    protected Element th(String content, String clazz) {
        Element th = node("th", clazz);
        th.setTextContent(content);
        return th;
    }

    protected Element td(String content, String clazz) {
        Element td = node("td", clazz);
        td.setTextContent(content);
        return td;
    }

    protected Element summaryLink() {
        Element link = node("a", null);
        link.setAttribute("href", "karate-summary.html");
        link.setTextContent("Summary");
        return link;
    }
    
    protected Element tagsLink() {
        Element link = node("a", null);
        link.setAttribute("href", "karate-tags.html");
        link.setTextContent("Tags");
        return link;
    }    

    protected static String getHtmlFileName(FeatureResult result) {
        return result.getPackageQualifiedName() + ".html";
    }

    protected File saveHtmlToFile(String targetDir, String fileName) {
        File file = new File(targetDir + File.separator + fileName);
        try {
            String xml = "<!DOCTYPE html>\n" + XmlUtils.toString(doc, false);
            initStaticResources(targetDir); // TODO improve init
            FileUtils.writeToFile(file, xml);
        } catch (Exception e) {
            System.out.println("html report output failed: " + e.getMessage());
        }
        return file;
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
        html = html.replace("//timeline//", sb.toString());
        FileUtils.writeToFile(htmlFile, html);
        return htmlFile;
    }

}
