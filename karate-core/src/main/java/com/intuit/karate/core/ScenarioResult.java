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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minidev.json.annotate.JsonIgnore;

/**
 *
 * @author pthomas3
 */
public class ScenarioResult implements ResultElement {

    private int line;
    private String id;
    private String name;
    private String description;
    private Type type = Type.SCENARIO;
    private String keyword = "Scenario";
    private List<StepResult> steps = new ArrayList();
    private List<TagResult> tags = Collections.EMPTY_LIST;
    
    public ScenarioResult(Scenario scenario) {
        line = scenario.getLine();
        name = scenario.getName();
        id = StringUtils.toIdString(name);
        description = scenario.getDescription();
        if (scenario.isOutline()) {
            keyword = "Scenario Outline";
        }
        List<Tag> list = scenario.getTags();
        if (list != null) {
            tags = new ArrayList(list.size());
            for (Tag tag : list) {
                tags.add(new TagResult(tag));                
            }
        }        
    }    
    
    private boolean failed = false;
    
    @JsonIgnore
    public boolean isFailed() {
        return failed;
    }
    
    @Override
    public void addStepResult(StepResult stepResult) {
        steps.add(stepResult);
        if (stepResult.getResult().isFailed()) {
            failed = true;
        }
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
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

    @Override
    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    @Override
    public List<StepResult> getSteps() {
        return steps;
    }

    public void setSteps(List<StepResult> steps) {
        this.steps = steps;
    }

    public List<TagResult> getTags() {
        return tags;
    }

    public void setTags(List<TagResult> tags) {
        this.tags = tags;
    }
        
}
