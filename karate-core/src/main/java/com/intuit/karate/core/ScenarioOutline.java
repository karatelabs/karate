/*
 * The MIT License
 *
 * Copyright 2022 Karate Labs Inc.
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
 * @author pthomas3
 */
public class ScenarioOutline {
    
    private TestScenario testScenario = new TestScenario(null, null, 0, null, null, null, null);
    private List<ExamplesTable> examplesTables;
    
    public ScenarioOutline(Feature feature, FeatureSection section) {
        this.testScenario.setFeature(feature);
        this.testScenario.setSection(section);
    }
    
    public Scenario toScenario(String dynamicExpression, int exampleIndex, int updateLine, List<Tag> tagsForExamples) {
        Scenario s = new Scenario(testScenario.getFeature(), testScenario.getSection(), exampleIndex);
        s.setName(getName());
        s.setDescription(getDescription());
        s.setLine(updateLine);
        s.setDynamicExpression(dynamicExpression);
        if (getTags() != null || tagsForExamples != null) {
            List<Tag> temp = new ArrayList();
            if (getTags() != null) {
                temp.addAll(getTags());
            }
            if (tagsForExamples != null) {
                temp.addAll(tagsForExamples);
            }
            s.setTags(temp);
        }
        List<Step> temp = new ArrayList(getSteps().size());
        s.setSteps(temp);
        for (Step original : getSteps()) {
            Step step = new Step(s, original.getIndex());
            temp.add(step);
            step.setLine(original.getLine());
            step.setEndLine(original.getEndLine());
            step.setPrefix(original.getPrefix());
            step.setText(original.getText());
            step.setDocString(original.getDocString());
            step.setTable(original.getTable());
            step.setComments(original.getComments());
        }
        return s;
    }
    
    public List<Scenario> getScenarios() {
        return this.getScenarios(null);
    }
    
    public List<Scenario> getScenarios(FeatureRuntime fr) {
        List<Scenario> list = new ArrayList();
        boolean examplesHaveTags = examplesTables.stream().anyMatch(t -> !t.getTags().isEmpty());
        for (ExamplesTable examples : examplesTables) {
            boolean selectedForExecution = false;
            if (fr != null && examplesHaveTags && fr.caller.isNone()) {
                // getting examples in the context of an execution
                // if the examples do not have any tagged example, do not worry about selecting
                Tags tableTags = Tags.merge(fr.featureCall.feature.getTags(), getTags(), examples.getTags());
                boolean executeForTable = tableTags.evaluate(fr.suite.tagSelector, fr.suite.env);
                if (executeForTable) {
                    selectedForExecution = true;
                }
            } else {
                selectedForExecution = true;
            }
            if (selectedForExecution) {
                Table table = examples.getTable();
                if (table.isDynamic()) {
                    Scenario scenario = toScenario(table.getDynamicExpression(), -1, table.getLineNumberForRow(0), examples.getTags());
                    list.add(scenario);
                } else {
                    int rowCount = table.getRows().size();
                    for (int i = 1; i < rowCount; i++) { // don't include header row
                        int exampleIndex = i - 1; // next line will set exampleIndex on scenario
                        Scenario scenario = toScenario(null, exampleIndex, table.getLineNumberForRow(i), examples.getTags());
                        scenario.setExampleData(table.getExampleData(exampleIndex)); // and we set exampleData here
                        list.add(scenario);
                        for (String key : table.getKeys()) {
                            scenario.replace("<" + key + ">", table.getValueAsString(key, i));
                        }
                    }
                }
            }
        }
        return list;
    }
    
    public FeatureSection getSection() {
        return testScenario.getSection();
    }
    
    public int getLine() {
        return testScenario.getLine();
    }
    
    public void setLine(int line) {
        testScenario.setLine(line);
    }
    
    public List<Tag> getTags() {
        return testScenario.getTags();
    }
    
    public void setTags(List<Tag> tags) {
        testScenario.setTags(tags);
    }
    
    public String getName() {
        return testScenario.getName();
    }
    
    public void setName(String name) {
        testScenario.setName(name);
    }
    
    public String getDescription() {
        return testScenario.getDescription();
    }
    
    public void setDescription(String description) {
        testScenario.setDescription(description);
    }
    
    public List<Step> getSteps() {
        return testScenario.getSteps();
    }
    
    public void setSteps(List<Step> steps) {
        testScenario.setSteps(steps);
    }
    
    public List<ExamplesTable> getExamplesTables() {
        return examplesTables;
    }
    
    public void setExamplesTables(List<ExamplesTable> examplesTables) {
        this.examplesTables = examplesTables;
    }
    
}
