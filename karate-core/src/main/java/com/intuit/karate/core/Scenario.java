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
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Scenario {

    public static final String TYPE = "scenario";
    public static final String KEYWORD = "Scenario";

    private final Feature feature;
    private final FeatureSection section;
    private final int index;

    private List<Tag> tags;
    private int line;
    private String name;
    private String description;
    private List<Step> steps;
    private boolean outline;
    private Map<String, Object> exampleData;
    private int exampleIndex = -1;
    private String dynamicExpression;
    private boolean backgroundDone;
    
    protected Scenario() {
        this(null, null, -1);
    }

    public Scenario(Feature feature, FeatureSection section, int index) {
        this.feature = feature;
        this.section = section;
        this.index = index;
    }
    
    public String getNameAndDescription() {
        String temp = "";
        if (name != null) {
            temp = temp + name;
        }
        if (description != null) {
            if (!temp.isEmpty()) {
                temp = temp + " ";
            }
            temp = temp + description;
        }
        return temp;
    }

    public String getNameForReport() {
        if (name == null) {
            return getDisplayMeta();
        } else {
            return getDisplayMeta() + " " + name;
        }
    }

    // only called for dynamic scenarios
    public Scenario copy(int exampleIndex) {
        Scenario s = new Scenario(feature, section, exampleIndex);
        s.name = name;
        s.description = description;
        s.tags = tags;
        s.line = line;
        s.exampleIndex = exampleIndex;
        s.outline = true; // this is a dynamic scenario row
        s.steps = new ArrayList(steps.size());
        for (Step step : steps) {
            Step temp = new Step(feature, s, step.getIndex());
            s.steps.add(temp);
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
        name = name.replace(token, value);
        description = description.replace(token, value);
        for (Step step : steps) {
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

    public String getDisplayMeta() {
        int num = section.getIndex() + 1;
        String meta = "[" + num;
        if (index != -1) {
            meta = meta + "." + (index + 1);
        }
        return meta + ":" + line + "]";
    }

    public String getUniqueId() {
        int num = section.getIndex() + 1;
        String meta = "-" + num;
        if (index != -1) {
            meta = meta + "_" + (index + 1);
        }
        return meta;
    }

    public List<Step> getBackgroundSteps() {
        return feature.isBackgroundPresent() ? feature.getBackground().getSteps() : Collections.EMPTY_LIST;
    }

    public List<Step> getStepsIncludingBackground() {
        List<Step> background = feature.isBackgroundPresent() ? feature.getBackground().getSteps() : null;
        int count = background == null ? steps.size() : steps.size() + background.size();
        List<Step> temp = new ArrayList(count);
        if (background != null) {
            temp.addAll(background);
        }
        temp.addAll(steps);
        return temp;
    }

    public String getKeyword() {
        return outline ? ScenarioOutline.KEYWORD : KEYWORD;
    }

    private Tags tagsEffective; // cache

    public Tags getTagsEffective() {
        if (tagsEffective == null) {
            tagsEffective = Tags.merge(feature.getTags(), tags);
        }
        return tagsEffective;
    }

    public FeatureSection getSection() {
        return section;
    }

    public Feature getFeature() {
        return feature;
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

    public boolean isOutline() {
        return outline;
    }

    public void setOutline(boolean outline) {
        this.outline = outline;
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

    public boolean isBackgroundDone() {
        return backgroundDone;
    }

    public void setBackgroundDone(boolean backgroundDone) {
        this.backgroundDone = backgroundDone;
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

    public void setExampleIndex(int exampleIndex) {
        this.exampleIndex = exampleIndex;
    }

    @Override
    public String toString() {
        return feature.toString() + getDisplayMeta();
    }        

}
