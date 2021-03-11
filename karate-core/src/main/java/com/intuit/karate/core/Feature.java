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

import com.intuit.karate.Constants;
import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import com.intuit.karate.resource.FileResource;
import com.intuit.karate.resource.Resource;
import com.intuit.karate.resource.ResourceUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class Feature {

    public static final String KEYWORD = "Feature";

    private final Resource resource;

    private int line;
    private List<Tag> tags;
    private String name;
    private String description;
    private Background background;
    private List<FeatureSection> sections = new ArrayList();

    private String callTag;
    private String callName;
    private int callLine = -1;

    public static Feature read(String path) {
        return read(ResourceUtils.getResource(FileUtils.WORKING_DIR, path));
    }

    public static Feature read(File file) {
        return read(new FileResource(file));
    }

    public static Feature read(Resource resource) {
        Feature feature = new Feature(resource);
        FeatureParser.parse(feature);
        return feature;
    }

    private Feature(Resource resource) {
        this.resource = resource;
    }

    public Resource getResource() {
        return resource;
    }

    public String getPackageQualifiedName() {
        return resource.getPackageQualifiedName();
    }

    public String getKarateJsonFileName() {
        return getPackageQualifiedName() + Constants.KARATE_JSON_SUFFIX;
    }

    public boolean isBackgroundPresent() {
        return background != null && background.getSteps() != null;
    }

    public String getNameAndDescription() {
        String temp = "";
        if (!StringUtils.isBlank(name)) {
            temp = temp + name;
        }
        if (!StringUtils.isBlank(description)) {
            if (!temp.isEmpty()) {
                temp = temp + " | ";
            }
            temp = temp + description;
        }
        return temp;
    }

    public String getNameForReport() {
        if (name == null) {
            return "[" + resource.getFileNameWithoutExtension() + "]";
        } else {
            return "[" + resource.getFileNameWithoutExtension() + "] " + name;
        }
    }

    public Step findStepByLine(int line) {
        for (FeatureSection section : sections) {
            List<Step> steps = section.isOutline()
                    ? section.getScenarioOutline().getSteps() : section.getScenario().getStepsIncludingBackground();
            for (Step step : steps) {
                if (step.getLine() == line) {
                    return step;
                }
            }
        }
        return null;
    }

    public void addSection(FeatureSection section) {
        section.setIndex(sections.size());
        sections.add(section);
    }

    public FeatureSection getSection(int sectionIndex) {
        return sections.get(sectionIndex);
    }

    public Scenario getScenario(int sectionIndex, int exampleIndex) {
        FeatureSection section = getSection(sectionIndex);
        if (exampleIndex == -1) {
            return section.getScenario();
        }
        ScenarioOutline outline = section.getScenarioOutline();
        return outline.getScenarios().get(exampleIndex);
    }

    public Step getStep(int sectionIndex, int exampleIndex, int stepIndex) {
        Scenario scenario = getScenario(sectionIndex, exampleIndex);
        List<Step> steps = scenario.getSteps();
        if (stepIndex == -1 || steps.isEmpty() || steps.size() <= stepIndex) {
            return null;
        }
        return steps.get(stepIndex);
    }

    public String getCallTag() {
        return callTag;
    }

    public void setCallTag(String callTag) {
        this.callTag = callTag;
    }

    public String getCallName() {
        return callName;
    }

    public void setCallName(String callName) {
        this.callName = callName;
    }

    public int getCallLine() {
        return callLine;
    }

    public void setCallLine(int callLine) {
        this.callLine = callLine;
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

    public Background getBackground() {
        return background;
    }

    public void setBackground(Background background) {
        this.background = background;
    }

    public List<FeatureSection> getSections() {
        return sections;
    }

    public void setSections(List<FeatureSection> sections) {
        this.sections = sections;
    }

    @Override
    public String toString() {
        return resource.toString();
    }

}
