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

import com.intuit.karate.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;

/**
 *
 * @author pthomas3
 */
public class HtmlTagsReport extends HtmlReport {

    private final Element tbody;

    private final Map<String, List<FeatureResult>> TAG_FEATURES = new LinkedHashMap();

    public HtmlTagsReport() {
        set("/html/head/title", "Karate Tags Report");
        setById("nav-type", "Tags");
        Element table = node("table", "tags-table table table-sm");
        contentContainer.appendChild(table);
        Element thead = node("thead", null);
        table.appendChild(thead);
        Element tr = node("tr", null);
        thead.appendChild(tr);
        tr.appendChild(th("Tag", null));
        tr.appendChild(th("Scenarios", null));
        tbody = node("tbody", null);
        table.appendChild(tbody);
    }

    public void addFeatureResult(FeatureResult result) {
        Set<String> distinct = new HashSet();
        for (ScenarioResult sr : result.getScenarioResults()) {
            Scenario scenario = sr.getScenario();
            Tags tags = scenario.getTagsEffective();
            distinct.addAll(tags.getTagKeys());
        }
        for (String tagKey : distinct) {
            List<FeatureResult> list = TAG_FEATURES.get(tagKey);
            if (list == null) {
                list = new ArrayList();
                TAG_FEATURES.put(tagKey, list);
            }
            list.add(result);
        }
    }

    public File save(String targetDir) {
        setById("nav-pass", 0 + "");
        setById("nav-fail", 0 + "");
        TAG_FEATURES.forEach((tagKey, results) -> {
            Element tr = node("tr", null);
            tbody.appendChild(tr);
            tr.appendChild(td(tagKey, null));
            Element td = node("td", null);
            tr.appendChild(td);
            for (FeatureResult fr : results) {
                Element featureLink = node("a", null);
                td.appendChild(featureLink);
                featureLink.setAttribute("href", "#");
                featureLink.setTextContent(fr.getDisplayUri());
                for (ScenarioResult sr : fr.getScenarioResults()) {
                    Element scenarioLink = node("a", null);
                    td.appendChild(scenarioLink);
                    scenarioLink.setAttribute("href", "#");
                    scenarioLink.setTextContent(sr.getScenario().getNameForReport());
                }
            }
        });
        return saveHtmlToFile(targetDir, "karate-tags.html");
    }

}
