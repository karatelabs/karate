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

import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class FeatureResult extends HashMap<String, Object> {

    private final String name;
    private final List<ResultElement> elements = new ArrayList();
    private final List<TagResult> tags;
    private final String packageQualifiedName;

    private int scenarioCount;
    private int failedCount;
    private List<Throwable> errors;
    private long duration;

    private ScriptValueMap resultVars;

    public FeatureResult(Feature feature) {
        put("elements", elements);
        put("keyword", "Feature");
        put("line", feature.getLine());
        String relativePath = feature.getRelativePath();
        put("uri", relativePath);
        put("name", relativePath); // hack for json / html report
        packageQualifiedName = feature.getPackageQualifiedName();
        name = feature.getName();
        put("id", StringUtils.toIdString(feature.getName()));
        String temp = feature.getName() == null ? "" : feature.getName();
        if (feature.getDescription() != null) {
            temp = temp + "\n" + feature.getDescription();
        }
        put("description", temp.trim());
        List<Tag> list = feature.getTags();
        if (list != null) {
            tags = new ArrayList(list.size());
            put("tags", tags);
            for (Tag tag : list) {
                tags.add(new TagResult(tag));
            }
        } else {
            tags = Collections.EMPTY_LIST;
        }
    }
    
    public String getErrorMessages() {
        if (errors == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Iterator<Throwable> iterator = errors.iterator();
        while (iterator.hasNext()) {
            Throwable error = iterator.next();
            sb.append(error.getMessage());
            if (iterator.hasNext()) {
                sb.append(',');
            }
        }
        return sb.toString();
    }

    public long getDuration() {
        return duration;
    }        

    public int getFailedCount() {
        return failedCount;
    }

    public int getScenarioCount() {
        return scenarioCount;
    }

    public boolean isFailed() {
        return errors != null && !errors.isEmpty();
    }

    public List<Throwable> getErrors() {
        return errors;
    }

    public Map<String, Object> getResultAsPrimitiveMap() {
        if (resultVars == null) {
            return Collections.EMPTY_MAP;
        }
        return resultVars.toPrimitiveMap();
    }

    public void setResultVars(ScriptValueMap resultVars) {
        this.resultVars = resultVars;
    }

    public String getPackageQualifiedName() {
        return packageQualifiedName;
    }

    public List<TagResult> getTags() {
        return tags;
    }

    public void addError(Throwable error) {
        failedCount++;
        if (errors == null) {
            errors = new ArrayList();
        }
        errors.add(error);
    }

    public void addResult(ResultElement element) {
        elements.add(element);
        duration += element.getDuration();
        if (element.isFailed()) {            
            if (element.isBackground()) {
                scenarioCount++; // since we will never enter the scenario
            }
            addError(element.getError());
        }
        if (!element.isBackground()) {
            scenarioCount++;
        }
    }

    public String getName() {
        return name;
    }

    public List<ResultElement> getElements() {
        return elements;
    }

}
