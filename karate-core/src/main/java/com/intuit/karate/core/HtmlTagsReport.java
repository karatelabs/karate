/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.w3c.dom.Element;

/**
 *
 * @author pthomas3
 */
public class HtmlTagsReport extends HtmlReport {

    private final List<FeatureResult> FEATURES = new ArrayList();

    public HtmlTagsReport() {
        set("/html/head/title", "Karate Tags Report");
        setById("nav-type", "Tags");
        contentContainer.appendChild(div("page-heading alert alert-primary", summaryLink()));        
    }

    public void addFeatureResult(FeatureResult result) {
        FEATURES.add(result);
    }

    public File save(String targetDir) {
        Set<String> allTags = new TreeSet();
        Set<String> failedTags = new TreeSet();
        Map<String, Set<String>> featureTagsMap = new HashMap();
        for (FeatureResult fr : FEATURES) {
            Set<String> featureTags = new HashSet();
            featureTagsMap.put(fr.getPackageQualifiedName(), featureTags);
            for (ScenarioResult sr : fr.getScenarioResults()) {
                Tags tags = sr.getScenario().getTagsEffective();
                Collection<String> tagKeys = tags.getTagKeys();
                allTags.addAll(tagKeys);
                featureTags.addAll(tagKeys);
                if (sr.isFailed()) {
                    failedTags.addAll(tagKeys);
                }
            }
        }
        setById("nav-pass", (allTags.size() - failedTags.size()) + "");
        setById("nav-fail", failedTags.size() + "");
        Element table = node("table", "tags-table table table-sm table-bordered");
        contentContainer.appendChild(table);
        Element thead = node("thead", null);
        table.appendChild(thead);
        Element headRow = node("tr", null);
        thead.appendChild(headRow);
        headRow.appendChild(th("Feature", "feature-cell"));
        for (String tagKey : allTags) {
            String tagClass = failedTags.contains(tagKey) ? "failed" : "passed";
            headRow.appendChild(th(tagKey, tagClass));
        }
        Element tbody = node("tbody", null);
        table.appendChild(tbody);
        for (FeatureResult fr : FEATURES) {
            Element tr = node("tr", null);
            tbody.appendChild(tr);
            Element featureCell = node("td", "feature-cell");
            tr.appendChild(featureCell);
            Element featureLink = node("a", "");
            featureCell.appendChild(featureLink);
            featureLink.setAttribute("href", getHtmlFileName(fr));
            featureLink.setTextContent(fr.getDisplayUri());
            Set<String> featureTags = featureTagsMap.get(fr.getPackageQualifiedName());
            for (String tagKey : allTags) {
                Element td;
                String tagClass = fr.isFailed() ? "failed" : "passed";
                if (featureTags.contains(tagKey)) {
                    td = td("X", tagClass);
                } else {
                    td = td("", null);
                }
                tr.appendChild(td);
            }
        }
        return saveHtmlToFile(targetDir, "karate-tags.html");
    }

}
