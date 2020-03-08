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
import com.intuit.karate.XmlUtils;
import java.io.File;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class HtmlSummaryReport extends HtmlReport {

    public HtmlSummaryReport() {
        set("/html/head/title", "Karate Summary Report");
    }

    public void addFeatureResult(FeatureResult result) {
        String featureName = result.getDisplayUri();
        Node featureDiv = div("feature", featureName);
        contentContainer.appendChild(featureDiv);        
        Element featureNav = div("feature-nav", featureName);
        navContainer.appendChild(featureNav);
    }

    public File save(String targetDir) {
        String fileName = "karate-summary.html";
        File file = new File(targetDir + File.separator + fileName);
        String xml = "<!DOCTYPE html>\n" + XmlUtils.toString(doc, false);
        try {
            initStaticResources(targetDir); // TODO improve init
            FileUtils.writeToFile(file, xml);
            System.out.println("\nHTML summary: (paste into browser to view) | Karate version: "
                    + FileUtils.getKarateVersion() + "\n"
                    + file.toURI()
                    + "\n=========================================================\n");
        } catch (Exception e) {
            System.out.println("html report output failed: " + e.getMessage());
        }
        return file;
    }

}
