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
    
    public Scenario toScenario(String dynamicExpression, int exampleIndex, int line, List<Tag> exampleTags) {
        Scenario s = new Scenario(feature, section, exampleIndex);
        s.setName(name);
        s.setDescription(description);
        s.setDynamicExpression(dynamicExpression);
        s.setOutline(true);
        s.setLine(line);
        if (tags != null || exampleTags != null) {
            List<Tag> temp = new ArrayList();
            if (tags != null) {
                temp.addAll(tags);
            }
            if (exampleTags != null) {
                temp.addAll(exampleTags);
            }
            s.setTags(temp);
        }
        List<Step> temp = new ArrayList(steps.size());
        s.setSteps(temp);
        for (Step original : steps) {
            Step step = new Step(feature, s, original.getIndex());
            temp.add(step);
            step.setLine(original.getLine());
            step.setEndLine(original.getEndLine());
            step.setPrefix(original.getPrefix());
            step.setText(original.getText());
            step.setDocString(original.getDocString());
            step.setTable(original.getTable());
        }
        return s;
    }    

    public List<Scenario> getScenarios() {
        List<Scenario> list = new ArrayList();
        for (ExampleTable examples : exampleTables) {
            Table table = examples.getTable();
            if (table.isDynamic()) {
                Scenario scenario = toScenario(table.getDynamicExpression(), -1, line, examples.getTags());
                list.add(scenario);
            } else {
                int rowCount = table.getRows().size();
                for (int i = 1; i < rowCount; i++) { // don't include header row
                    Scenario scenario = toScenario(null, i - 1, table.getLineNumberForRow(i), examples.getTags());
                    list.add(scenario);
                    for (String key : table.getKeys()) {
                        scenario.replace("<" + key + ">", table.getValue(key, i));
                    }
                }
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
