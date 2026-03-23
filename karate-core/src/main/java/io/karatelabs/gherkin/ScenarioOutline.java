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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScenarioOutline {

    private final Feature feature;
    private final FeatureSection section;

    private int line;
    private List<Tag> tags;
    private String name;
    private String description;
    private List<Step> steps;
    private List<ExamplesTable> examplesTables;
    private int numScenarios = 0;

    public ScenarioOutline(Feature feature, FeatureSection section) {
        this.feature = feature;
        this.section = section;
    }

    public Scenario toScenario(String dynamicExpression, int exampleIndex, int updateLine, List<Tag> tagsForExamples) {
        Scenario s = new Scenario(feature, section, exampleIndex);
        s.setName(name);
        s.setDescription(description);
        s.setLine(updateLine);
        s.setDynamicExpression(dynamicExpression);
        if (tags != null || tagsForExamples != null) {
            List<Tag> temp = new ArrayList<>();
            if (tags != null) {
                temp.addAll(tags);
            }
            if (tagsForExamples != null) {
                temp.addAll(tagsForExamples);
            }
            s.setTags(temp);
        }
        List<Step> temp = new ArrayList<>(steps.size());
        s.setSteps(temp);
        for (Step original : steps) {
            Step step = new Step(s, original.getIndex());
            temp.add(step);
            step.setLine(original.getLine());
            step.setEndLine(original.getEndLine());
            step.setPrefix(original.getPrefix());
            step.setKeyword(original.getKeyword());
            step.setText(original.getText());
            step.setDocString(original.getDocString());
            step.setTable(original.getTable());
            step.setComments(original.getComments());
        }
        numScenarios++;
        return s;
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

    public List<ExamplesTable> getExamplesTables() {
        return examplesTables;
    }

    public int getNumExampleTables() {
        return examplesTables.size();
    }

    public List<Map<String, Object>> getAllExampleData() {
        List<Map<String, Object>> exampleData = new ArrayList<>();
        examplesTables.forEach(table -> exampleData.add(table.toJson()));
        return exampleData;
    }

    public void setExamplesTables(List<ExamplesTable> examplesTables) {
        this.examplesTables = examplesTables;
    }

    public int getNumScenarios() {
        return numScenarios;
    }

}
