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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Scenario {

    private final int exampleIndex;

    private Map<String, Object> exampleData;
    private String dynamicExpression;
    TestScenario testScenario = new TestScenario(null, null, 0, null, null, null, null);

    public Scenario(Feature feature, FeatureSection section, int exampleIndex) {
        this.testScenario.setFeature(feature);
        this.testScenario.setSection(section);
        this.exampleIndex = exampleIndex;
    }
    
    public boolean isEqualTo(Scenario other) {
        return other.testScenario.getSection().getIndex() == testScenario.getSection().getIndex() && other.exampleIndex == exampleIndex;
    }

    public String getNameAndDescription() {
        String temp = "";
        if (testScenario.getName() != null) {
            temp = temp + testScenario.getName();
        }
        if (testScenario.getDescription() != null) {
            if (!temp.isEmpty()) {
                temp = temp + " ";
            }
            temp = temp + testScenario.getDescription();
        }
        return temp;
    }

    public String getRefIdAndName() {
        if (testScenario.getName() == null) {
            return getRefId();
        } else {
            return getRefId() + " " + testScenario.getName();
        }
    }

    // only called for dynamic scenarios
    public Scenario copy(int exampleIndex) {
        Scenario s = new Scenario(testScenario.getFeature(), testScenario.getSection(), exampleIndex);
        s.testScenario.setName(getName());
        s.testScenario.setDescription(getDescription());
        s.testScenario.setTags(getTags());
        s.testScenario.setLine(getLine());
        s.dynamicExpression = dynamicExpression;
        s.testScenario.setSteps(new ArrayList(getSteps().size()));
        for (Step step : testScenario.getSteps()) {
            Step temp = new Step(s, step.getIndex());
            s.testScenario.getSteps().add(temp);
            temp.setLine(step.getLine());
            temp.setEndLine(step.getEndLine());
            temp.setPrefix(step.getPrefix());
            temp.setText(step.getText());
            temp.setDocString(step.getDocString());
            temp.setTable(step.getTable());
        }
        return s;
    }

    public void replace(String token, String value) {
        if (value == null) {
            // this can happen for a dynamic scenario outline !
            // give up trying a cucumber-style placeholder sub
            // user should be fine with karate-style plain-old variables
            return;
        }
        testScenario.setName(getName().replace(token, value));
        for (Step step : testScenario.getSteps()) {
            String text = step.getText();
            step.setText(text.replace(token, value));
            String docString = step.getDocString();
            if (docString != null) {
                step.setDocString(docString.replace(token, value));
            }
            Table table = step.getTable();
            if (table != null) {
                step.setTable(table.replace(token, value));
            }
        }
    }

    public Step getStepByLine(int line) {
        for (Step step : getStepsIncludingBackground()) {
            if (step.getLine() == line) {
                return step;
            }
        }
        return null;
    }

    public String getRefId() {
        int num = testScenario.getSection().getIndex() + 1;
        String meta = "[" + num;
        if (exampleIndex != -1) {
            meta = meta + "." + (exampleIndex + 1);
        }
        return meta + ":" + testScenario.getLine() + "]";
    }

    public String getDebugInfo() {
        return testScenario.getFeature() + ":" + testScenario.getLine();
    }

    public String getUniqueId() {
        String id = testScenario.getFeature().getResource().getPackageQualifiedName() + "_" + (testScenario.getSection().getIndex() + 1);
        return exampleIndex == -1 ? id : id + "_" + (exampleIndex + 1);
    }

    public List<Step> getBackgroundSteps() {
        return testScenario.getFeature().isBackgroundPresent() ? testScenario.getFeature().getBackground().getSteps() : Collections.EMPTY_LIST;
    }

    public List<Step> getStepsIncludingBackground() {
        List<Step> background = testScenario.getFeature().isBackgroundPresent() ? testScenario.getFeature().getBackground().getSteps() : null;
        int count = background == null ? testScenario.getSteps().size() : testScenario.getSteps().size() + background.size();
        List<Step> temp = new ArrayList(count);
        if (background != null) {
            temp.addAll(background);
        }
        temp.addAll(testScenario.getSteps());
        return temp;
    }

    private Tags tagsEffective; // cache

    public Tags getTagsEffective() {
        if (tagsEffective == null) {
            tagsEffective = Tags.merge(testScenario.getFeature().getTags(), testScenario.getTags());
        }
        return tagsEffective;
    }

    public FeatureSection getSection() {
        return testScenario.getSection();
    }

    public Feature getFeature() {
        return testScenario.getFeature();
    }

    public int getLine() {
        return testScenario.getLine();
    }

    public void setLine(int line) {
        this.testScenario.setLine(line);
    }        

    public List<Tag> getTags() {
        return testScenario.getTags();
    }

    public void setTags(List<Tag> tags) {
        this.testScenario.setTags(tags);
    }

    public String getName() {
        return testScenario.getName();
    }

    public void setName(String name) {
        this.testScenario.setName(name);
    }

    public String getDescription() {
        return testScenario.getDescription();
    }

    public void setDescription(String description) {
        this.testScenario.setDescription(description);
    }

    public List<Step> getSteps() {
        return testScenario.getSteps();
    }

    public void setSteps(List<Step> steps) {
        this.testScenario.setSteps(steps);
    }

    public boolean isOutlineExample() {
        return exampleIndex != -1;
    }

    public boolean isDynamic() {
        return dynamicExpression != null;
    }

    public String getDynamicExpression() {
        return dynamicExpression;
    }

    public void setDynamicExpression(String dynamicExpression) {
        this.dynamicExpression = dynamicExpression;
    }

    public Map<String, Object> getExampleData() {
        return exampleData;
    }

    public void setExampleData(Map<String, Object> exampleData) {
        this.exampleData = exampleData;
    }

    public int getExampleIndex() {
        return exampleIndex;
    }

    @Override
    public String toString() {
        return testScenario.getFeature().toString() + getRefId();
    }

    public URI getUriToLineNumber() {
        return URI.create(testScenario.getFeature().getResource().getUri() + "?line=" + testScenario.getLine());
    }

}
