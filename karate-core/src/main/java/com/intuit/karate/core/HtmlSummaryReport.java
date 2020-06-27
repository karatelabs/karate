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
import org.w3c.dom.Element;

/**
 *
 * @author pthomas3
 */
public class HtmlSummaryReport extends HtmlReport {

    private final Element tbody;
    private final HtmlTagsReport tagsReport;
    
    private int passCount;
    private int failCount;   

    public HtmlSummaryReport() {
        tagsReport = new HtmlTagsReport();
        set("/html/head/title", "Karate Summary Report");
        setById("nav-type", "Features");
        contentContainer.appendChild(div("page-heading alert alert-primary", tagsLink())); 
        Element table = node("table", "features-table table table-sm");
        contentContainer.appendChild(table);
        Element thead = node("thead", null);
        table.appendChild(thead);
        Element tr = node("tr", null);
        thead.appendChild(tr);
        tr.appendChild(th("Feature", null));
        tr.appendChild(th("Title", null));
        tr.appendChild(th("Passed", "num"));
        tr.appendChild(th("Failed", "num"));
        tr.appendChild(th("Scenarios", "num"));
        tr.appendChild(th("Time (ms)", "num"));
        tbody = node("tbody", null);
        table.appendChild(tbody);
    }

    public void addFeatureResult(FeatureResult result) {
        String rowClass = result.isFailed() ? "failed-lt" : "passed-lt";
        Element tr = node("tr", rowClass);
        tbody.appendChild(tr);
        String featureUri = result.getDisplayUri();
        String featurePath = getHtmlFileName(result);
        Element tdFeature = node("td", null);
        tr.appendChild(tdFeature);
        Element featureLink = node("a", null);
        tdFeature.appendChild(featureLink);
        featureLink.setTextContent(featureUri);
        featureLink.setAttribute("href", featurePath);
        tr.appendChild(td(result.getFeature().getNameAndDescription(), null));
        tr.appendChild(td(result.getPassedCount() + "", "num"));
        tr.appendChild(td(result.getFailedCount() + "", "num"));
        tr.appendChild(td(result.getScenarioCount() + "", "num"));
        String duration = formatter.format(result.getDurationMillis());
        tr.appendChild(td(duration, "num"));
        if (result.isFailed()) {
            failCount++;
            Element featureNav = div("nav-item failed");
            Element failedLink = node("a", null);
            featureNav.appendChild(failedLink);
            failedLink.setTextContent(result.getFeature().getNameForReport());
            failedLink.setAttribute("href", featurePath);
            navContainer.appendChild(featureNav);
        } else {
            passCount++;
        }
        tagsReport.addFeatureResult(result);
    }

    public File save(String targetDir) {
        tagsReport.save(targetDir);
        setById("nav-pass", passCount + "");
        setById("nav-fail", failCount + "");
        File file = saveHtmlToFile(targetDir, "karate-summary.html");
        System.out.println("\nHTML report: (paste into browser to view) | Karate version: "
                + FileUtils.getKarateVersion() + "\n"
                + file.toURI()
                + "\n===================================================================\n");
        return file;
    }

}
