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
import com.intuit.karate.StringUtils;
import com.intuit.karate.resource.ResourceUtils;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class HtmlTimelineReport {

    private final Map<String, Integer> groupsMap = new LinkedHashMap();
    private final List<Map> items = new ArrayList();
    private final DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private int id;

    public void addFeatureResult(FeatureResult fr) {
        fr.getScenarioResults().stream().forEach(sr -> {
            String threadName = sr.getExecutorName();
            Integer groupId = groupsMap.get(threadName);
            if (groupId == null) {
                groupId = groupsMap.size() + 1;
                groupsMap.put(threadName, groupId);
            }
            Map<String, Object> item = new LinkedHashMap(10);
            items.add(item);
            item.put("id", ++id);
            item.put("group", groupId);
            Scenario s = sr.getScenario();
            String featureName = s.getFeature().getResource().getFileNameWithoutExtension();
            String content = featureName + s.getDisplayMeta();
            item.put("content", content);
            long startTime = sr.getStartTime();
            item.put("start", startTime);
            long endTime = sr.getEndTime() - 1; // avoid overlap when rendering
            item.put("end", endTime);
            String startTimeString = dateFormat.format(new Date(startTime));
            String endTimeString = dateFormat.format(new Date(endTime));
            content = content + " " + startTimeString + "-" + endTimeString;
            String scenarioTitle = StringUtils.trimToEmpty(s.getName());
            if (!scenarioTitle.isEmpty()) {
                content = content + " " + scenarioTitle;
            }
            item.put("title", content);
            if (sr.isFailed()) {
                item.put("className", "failed");
            }
        });
    }

    public File save(String reportDir) {
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
        String html = ResourceUtils.classPathResourceToString("timeline-template.html");
        html = html.replace("//timeline//", sb.toString());
        File file = new File(reportDir + File.separator + "karate-timeline.html");
        FileUtils.writeToFile(file, html);
        return file;
    }

}
