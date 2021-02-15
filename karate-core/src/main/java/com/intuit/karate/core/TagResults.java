/*
 * The MIT License
 *
 * Copyright 2021 Intuit Inc.
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

import com.intuit.karate.report.ReportUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author pthomas3
 */
public class TagResults {

    private final Set<String> allTagKeys = new TreeSet();
    private final Set<String> failedTagKeys = new TreeSet();
    private final List<Map<String, Object>> featureTagsList = new ArrayList();

    public void addFeatureResult(FeatureResult fr) {
        Map<String, Object> featureTags = new HashMap();
        featureTagsList.add(featureTags);
        featureTags.put("featureSummary", fr.toSummaryJson());
        Set<String> tagKeysSet = new TreeSet();
        featureTags.put("tagKeys", tagKeysSet);
        Set<String> failedTagKeysSet = new TreeSet();
        featureTags.put("failedTagKeys", failedTagKeysSet);        
        for (ScenarioResult sr : fr.getScenarioResults()) {
            Tags tags = sr.getScenario().getTagsEffective();
            allTagKeys.addAll(tags.getTagKeys());
            tagKeysSet.addAll(tags.getTagKeys());
            if (sr.isFailed()) {
                failedTagKeys.addAll(tags.getTagKeys());
                failedTagKeysSet.addAll(tags.getTagKeys());
            }
        }
    }
    
    public Map<String, Object> toKarateJson() {
        Map<String, Object> map = new HashMap();
        map.put("tagKeysPassed", allTagKeys.size() - failedTagKeys.size());
        map.put("tagKeysFailed", failedTagKeys.size());
        map.put("resultDate", ReportUtils.getDateString());
        map.put("tagKeys", allTagKeys);
        map.put("failedTagKeys", failedTagKeys);
        map.put("featureTags", featureTagsList);
        return map;
    }

}
