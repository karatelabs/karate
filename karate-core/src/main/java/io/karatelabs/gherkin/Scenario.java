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

import io.karatelabs.common.Resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Scenario {

    private final Feature feature;
    private final FeatureSection section;
    private final int exampleIndex;

    private int line;
    private List<Tag> tags;
    private String name;
    private String description;
    private List<Step> steps;
    private Map<String, Object> exampleData;
    private String dynamicExpression;

    public Scenario(Feature feature, FeatureSection section, int exampleIndex) {
        this.feature = feature;
        this.section = section;
        this.exampleIndex = exampleIndex;
    }

    /**
     * Create a synthetic error scenario for error reporting when execution fails.
     * Used when a feature fails before any scenario can be executed
     * (e.g., dynamic expression evaluation failure).
     *
     * @param feature the feature that failed
     * @param errorName the name to use for the error scenario
     * @param line the line number
     * @return a synthetic Scenario for error reporting
     */
    public static Scenario createError(Feature feature, String errorName, int line) {
        FeatureSection section = new FeatureSection();
        section.setIndex(0);
        Scenario scenario = new Scenario(feature, section, -1);
        scenario.setName(errorName);
        scenario.setLine(line);
        scenario.setSteps(Collections.emptyList());
        section.setScenario(scenario);
        return scenario;
    }

    public boolean isEqualTo(Scenario other) {
        return other.section.getIndex() == section.getIndex() && other.exampleIndex == exampleIndex;
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

    public String getRefIdAndName() {
        if (name == null) {
            return getRefId();
        } else {
            return getRefId() + " " + name;
        }
    }

    // only called for dynamic scenarios
    public Scenario copy(int exampleIndex) {
        Scenario s = new Scenario(feature, section, exampleIndex);
        s.name = name;
        s.description = description;
        s.tags = tags;
        s.line = line;
        s.dynamicExpression = dynamicExpression;
        s.steps = new ArrayList<>(steps.size());
        for (Step step : steps) {
            Step temp = new Step(s, step.getIndex());
            s.steps.add(temp);
            temp.setLine(step.getLine());
            temp.setEndLine(step.getEndLine());
            temp.setPrefix(step.getPrefix());
            temp.setKeyword(step.getKeyword());
            temp.setText(step.getText());
            temp.setDocString(step.getDocString());
            temp.setTable(step.getTable());
            temp.setComments(step.getComments());
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
        if (name != null) {
            name = name.replace(token, value);
        }
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

    public String getRefId() {
        int num = section.getIndex() + 1;
        String meta = "[" + num;
        if (exampleIndex != -1) {
            meta = meta + "." + (exampleIndex + 1);
        }
        return meta + ":" + line + "]";
    }

    public String getDebugInfo() {
        return feature + ":" + line;
    }

    public String getUniqueId() {
        Resource resource = feature.getResource();
        if (resource == null) {
            return "";
        }
        String id = resource.getPackageQualifiedName() + "_" + (section.getIndex() + 1);
        return exampleIndex == -1 ? id : id + "_" + (exampleIndex + 1);
    }

    public List<Step> getBackgroundSteps() {
        return feature.isBackgroundPresent() ? feature.getBackground().getSteps() : Collections.emptyList();
    }

    public List<Step> getStepsIncludingBackground() {
        List<Step> background = feature.isBackgroundPresent() ? feature.getBackground().getSteps() : null;
        int count = background == null ? steps.size() : steps.size() + background.size();
        List<Step> temp = new ArrayList<>(count);
        if (background != null) {
            temp.addAll(background);
        }
        temp.addAll(steps);
        return temp;
    }

//    private Tags tagsEffective; // cache
//
//    public Tags getTagsEffective() {
//        if (tagsEffective == null) {
//            tagsEffective = Tags.merge(feature.getTags(), tags);
//        }
//        return tagsEffective;
//    }

    public FeatureSection getSection() {
        return section;
    }

    public Feature getFeature() {
        return feature;
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
        return feature.toString() + getRefId();
    }

    public URI getUriToLineNumber() {
        return URI.create(feature.getResource().getUri() + "?line=" + line);
    }

    public boolean isSetup() {
        return getTags() != null && getTags()
                .stream()
                .map(Tag::getName)
                .anyMatch((t) -> t.equals(Tag.SETUP));
    }

    public boolean isFail() {
        return getTags() != null && getTags()
                .stream()
                .map(Tag::getName)
                .anyMatch((t) -> t.equals(Tag.FAIL));
    }

    /**
     * Returns the effective tags for this scenario, including inherited feature-level tags.
     * Feature tags are merged with scenario-level tags.
     */
    public List<Tag> getTagsEffective() {
        List<Tag> featureTags = feature != null ? feature.getTags() : null;
        if (featureTags == null || featureTags.isEmpty()) {
            return tags != null ? tags : java.util.Collections.emptyList();
        }
        if (tags == null || tags.isEmpty()) {
            return featureTags;
        }
        // Merge feature + scenario tags
        List<Tag> merged = new java.util.ArrayList<>(featureTags);
        merged.addAll(tags);
        return merged;
    }

    /**
     * Returns true if this scenario (or its feature) has the @ignore tag.
     */
    public boolean isIgnore() {
        return getTagsEffective().stream()
                .map(Tag::getName)
                .anyMatch(Tag.IGNORE::equals);
    }

}
