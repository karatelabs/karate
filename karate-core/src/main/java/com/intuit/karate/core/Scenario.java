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
import java.util.Collection;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class Scenario {

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

    public Scenario(Feature feature, FeatureSection section, int index) {
        this.feature = feature;
        this.section = section;
        this.index = index;
    }

    public List<Step> getStepsIncludingBackground() { 
        List<Step> background = feature.getBackground() == null ? null : feature.getBackground().getSteps();
        int count = background == null ? steps.size() : steps.size() + background.size();
        List<Step> temp = new ArrayList(count);
        if (background != null) {
            temp.addAll(background);
        }
        temp.addAll(steps);
        return temp;
    }

    public FeatureSection getSection() {
        return section;
    }

    public Feature getFeature() {
        return feature;
    }

    public Collection<Tag> getTagsEffective() {
        return Tags.merge(feature.getTags(), tags);
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

}
