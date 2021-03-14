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

import com.intuit.karate.KarateException;
import com.intuit.karate.resource.MemoryResource;
import com.intuit.karate.resource.Resource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author pthomas3
 */
public class Step {

    private final Feature feature;
    private final Scenario scenario; // can be  null for background !!
    private final int index;

    private int line;
    private int endLine;
    private List<String> comments;
    private String prefix;
    private String text;
    private String docString;
    private Table table;

    public static final List<String> PREFIXES = Arrays.asList("*", "Given", "When", "Then", "And", "But");

    public void parseAndUpdateFrom(String text) {
        final String stepText = text.trim();
        boolean hasPrefix = PREFIXES.stream().anyMatch(prefixValue -> stepText.startsWith(prefixValue));
        // to avoid parser considering text without prefix as scenario comments / doc-string
        if (!hasPrefix) {
            text = "* " + stepText;
        }
        Resource resource = new MemoryResource(scenario.getFeature().getResource().getFile(), "Feature:\nScenario:\n" + text);
        Feature tempFeature = Feature.read(resource);
        Step tempStep = tempFeature.getStep(0, -1, 0);
        if (tempStep == null) {
            throw new KarateException("invalid expression: " + text);
        }
        this.prefix = tempStep.prefix;
        this.text = tempStep.text;
        this.docString = tempStep.docString;
        this.table = tempStep.table;
    }

    public String getDebugInfo() {
        return feature + ":" + line;
    }

    public boolean isPrint() {
        return text != null && text.startsWith("print");
    }

    public boolean isPrefixStar() {
        return "*".equals(prefix);
    }

    public Feature getFeature() {
        return feature;
    }

    public Step(Feature feature, int index) {
        this.feature = feature;
        this.scenario = null;
        this.index = index;
    }

    public Step(Scenario scenario, int index) {
        this.scenario = scenario;
        this.feature = scenario.getFeature();
        this.index = index;
    }

    public static Step fromKarateJson(Scenario scenario, Map<String, Object> map) {
        int index = (Integer) map.get("index");
        Boolean background = (Boolean) map.get("background");
        if (background == null) {
            background = false;
        }
        Step step = background ? new Step(scenario.getFeature(), index) : new Step(scenario, index);
        int line = (Integer) map.get("line");
        step.setLine(line);
        Integer endLine = (Integer) map.get("endLine");
        if (endLine == null) {
            endLine = line;
        }
        step.setEndLine(endLine);
        if(map.get("comments") instanceof List) {
            step.setComments((List) map.get("comments"));
        }
        step.setPrefix((String) map.get("prefix"));
        step.setText((String) map.get("text"));
        step.setDocString((String) map.get("docString"));
        if(map.get("table") instanceof List) {
            List<Map<String, Object>> table = (List) map.get("table");
            if (table != null) {
                step.setTable(Table.fromKarateJson(table));
            }
        }
        return step;
    }

    public Map<String, Object> toKarateJson() {
        Map<String, Object> map = new HashMap();
        if (isBackground()) {
            map.put("background", true);
        }
        map.put("index", index);
        map.put("line", line);
        if (endLine != line) {
            map.put("endLine", endLine);
        }
        if (comments != null && !comments.isEmpty()) {
            map.put("comments", comments);
        }
        map.put("prefix", prefix);
        map.put("text", text);
        if (docString != null) {
            map.put("docString", docString);
        }
        if (table != null) {
            map.put("table", table.toKarateJson());
        }
        return map;
    }

    public boolean isBackground() {
        return scenario == null;
    }

    public boolean isOutline() {
        return scenario != null && scenario.isOutlineExample();
    }

    public int getIndex() {
        return index;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getLineCount() {
        return endLine - line + 1;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDocString() {
        return docString;
    }

    public void setDocString(String docString) {
        this.docString = docString;
    }

    public Table getTable() {
        return table;
    }

    public void setTable(Table table) {
        this.table = table;
    }

    public List<String> getComments() {
        return comments;
    }

    public void setComments(List<String> comments) {
        this.comments = comments;
    }

    @Override
    public String toString() {
        String temp = prefix + " " + text;
        if (docString != null) {
            temp = temp + "\n\"\"\"\n" + docString + "\n\"\"\"";
        }
        if (table != null) {
            temp = temp + " " + table.toString();
        }
        return temp;
    }

}
