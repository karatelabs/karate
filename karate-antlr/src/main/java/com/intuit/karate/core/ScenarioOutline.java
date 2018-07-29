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

    private List<Tag> tags;
    private String description;
    private List<Step> steps;
    private List<ExampleTable> exampleTables;

    public List<Scenario> getScenarios() {
        List<Scenario> list = new ArrayList();
        for (ExampleTable et : exampleTables) {
            Table t = et.getTable();
            int rowCount = t.getRows().size();
            for (int i = 1; i < rowCount; i++) { // don't include header row
                Scenario s = new Scenario();
                list.add(s);
                s.setTags(tags);
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
                    Step step = new Step();
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

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
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
