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
public class FeatureResult {

    private final Feature feature;
    private final String displayName;
    private final List<ScenarioResult> scenarioResults = new ArrayList();

    private int scenarioCount;
    private int failedCount;
    private List<Throwable> errors;
    private long duration;

    private ScriptValueMap resultVars;
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap(8);
        List<Map> list = new ArrayList(scenarioResults.size());
        map.put("elements", list);
        for (ScenarioResult re : scenarioResults) {
            if (re.getScenario().getFeature().isBackgroundPresent()) {
                list.add(re.backgroundToMap());
            }
            list.add(re.toMap());
        }
        map.put("keyword", Feature.KEYWORD);
        map.put("line", feature.getLine());        
        map.put("uri", displayName);        
        map.put("name", displayName);
        map.put("id", StringUtils.toIdString(feature.getName()));
        String temp = feature.getName() == null ? "" : feature.getName();
        if (feature.getDescription() != null) {
            temp = temp + "\n" + feature.getDescription();
        }        
        map.put("description", temp.trim());
        if (feature.getTags() != null) {
            map.put("tags", Tags.toResultList(feature.getTags()));
        }   
        return map;
    }

    public FeatureResult(Feature feature) {
        this.feature = feature;
        displayName = FileUtils.removePrefix(feature.getRelativePath());
    }

    public Feature getFeature() {
        return feature;
    }        

    public String getDisplayName() {
        return displayName;
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

    public void addError(Throwable error) {
        failedCount++;
        if (errors == null) {
            errors = new ArrayList();
        }
        errors.add(error);
    }

    public void addResult(ScenarioResult result) {
        scenarioResults.add(result);
        duration += result.getDuration();
        scenarioCount++;
        if (result.isFailed()) {            
            addError(result.getError());
        }
    }

    public List<ScenarioResult> getScenarioResults() {
        return scenarioResults;
    }

}
