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

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class ScenarioOutline {

    public static final String KEYWORD = "Scenario Outline";

    private final Feature feature;
    private final FeatureSection section;

    private int line;
    private List<Tag> tags;
    private String name;
    private String description;
    private List<Step> steps;
    private List<ExampleTable> exampleTables;

    public ScenarioOutline(Feature feature, FeatureSection section) {
        this.feature = feature;
        this.section = section;
    }

    public List<Scenario> getScenarios() {
        List<Scenario> list = new ArrayList();
        for (ExampleTable et : exampleTables) {
            Table t = et.getTable();
            int rowCount = t.getRows().size();
            for (int i = 1; i < rowCount; i++) { // don't include header row
                Scenario s = new Scenario(feature, section, i - 1);
                list.add(s);
                s.setOutline(true);
                s.setLine(t.getLineNumberForRow(i));
                if (tags != null || et.getTags() != null) {
                    List<Tag> temp = new ArrayList();
                    if (tags != null) {
                        temp.addAll(tags);
                    }
                    if (et.getTags() != null) {
                        temp.addAll(et.getTags());
                    }
                    s.setTags(temp);
                }
                s.setName(name);
                s.setDescription(description);
                List<Step> replaced = new ArrayList(steps.size());
                for (Step original : steps) {
                    String text = original.getText();
                    String docString = original.getDocString();
                    Table table = original.getTable();
                    for (String key : t.getKeys()) {
                        String value = t.getValue(key, i);
                        String token = "<" + key + ">";
                        text = text.replace(token, value);
                        if (docString != null) {
                            docString = docString.replace(token, value);
                        } else if (table != null) {
                            table = table.replace(token, value);
                        }
                    }
                    Step step = new Step(s, original.getIndex());
                    step.setPrefix(original.getPrefix());
                    step.setText(text);
                    step.setDocString(docString);
                    step.setTable(table);
                    replaced.add(step);
                }
                s.setSteps(replaced);
            }
        }
        return list;
    }

    public FeatureSection getSection() {
        return section;
    }        

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }

    public List<ExampleTable> getExampleTables() {
        return exampleTables;
    }

    public void setExampleTables(List<ExampleTable> exampleTables) {
        this.exampleTables = exampleTables;
    }

}
