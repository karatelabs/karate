/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.gherkin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Step {

    private final Feature feature;
    private final Scenario scenario; // can be  null for background !!
    private final int index;

    private int line;
    private int endLine;
    private List<String> comments;
    private String prefix;
    private String keyword;
    private String text;
    private String docString;
    private int docStringLine = -1; // 0-indexed line in feature where docstring content starts
    private Table table;

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

    @SuppressWarnings("unchecked")
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
        if (map.get("comments") instanceof List) {
            step.setComments((List<String>) map.get("comments"));
        }
        step.setPrefix((String) map.get("prefix"));
        step.setText((String) map.get("text"));
        step.setDocString((String) map.get("docString"));
        if (map.get("table") instanceof List) {
            List<Map<String, Object>> table = (List<Map<String, Object>>) map.get("table");
            if (table != null) {
                step.setTable(Table.fromKarateJson(table));
            }
        }
        return step;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> map = new HashMap<>();
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
            map.put("table", table.toJson());
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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
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

    /**
     * Returns the 0-indexed line in the feature file where the docstring content starts.
     * Returns -1 if not set (e.g., when deserialized from JSON without this info).
     */
    public int getDocStringLine() {
        return docStringLine;
    }

    public void setDocStringLine(int docStringLine) {
        this.docStringLine = docStringLine;
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

    public boolean isFake() {
        return getIndex() == -1;
    }

    public boolean isSetup() {
        return scenario != null && scenario.isSetup();
    }

    @Override
    public String toString() {
        String temp = prefix;
        if (keyword != null) {
            temp += " " + keyword;
        }
        temp += " " + text;
        if (docString != null) {
            temp = temp + "\n\"\"\"\n" + docString + "\n\"\"\"";
        }
        if (table != null) {
            temp = temp + " " + table.toString();
        }
        return temp;
    }

}
