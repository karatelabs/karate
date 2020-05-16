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

import com.intuit.karate.StringUtils;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class Step {

    private final Feature feature;
    private final Scenario scenario;
    private final int index;

    private int line;
    private int endLine;
    private List<String> comments;
    private String prefix;
    private String text;
    private String docString;
    private Table table;

    public String getDebugInfo() {
        String message = "feature: " + feature.getRelativePath();
        if (!isBackground()) {
            message = message + ", scenario: " + StringUtils.trimToNull(scenario.getName());
        }
        return message + ", line: " + line;
    }

    public boolean isPrint() {
        return text != null && text.startsWith("print");
    }

    public boolean isPrefixStar() {
        return "*".equals(prefix);
    }

    protected Step() {
        this(null, null, -1);
    }

    public Step(Feature feature, Scenario scenario, int index) {
        this.feature = feature;
        this.scenario = scenario;
        this.index = index;
    }

    public boolean isBackground() {
        return scenario == null;
    }

    public boolean isOutline() {
        return scenario != null && scenario.isOutline();
    }

    public Feature getFeature() {
        return feature;
    }

    public Scenario getScenario() {
        return scenario;
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
