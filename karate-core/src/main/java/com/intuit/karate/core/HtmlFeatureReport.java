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
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class HtmlFeatureReport extends HtmlReport {

    private final String baseName;

    private int stepCounter;

    private void stepHtml(StepResult stepResult, Node parent, int depth) {
        Step step = stepResult.getStep();
        Result result = stepResult.getResult();
        String extraClass;
        if (result.isFailed()) {
            extraClass = "failed";
        } else if (result.isSkipped()) {
            extraClass = "skipped";
        } else {
            extraClass = "passed";
        }
        String refNum = ++stepCounter + "";
        Element stepContainer = div("step-container");
        stepContainer.setAttribute("id", refNum);
        stepContainer.appendChild(div("step-ref " + extraClass, refNum));
        for (int i = 0; i < depth; i++) {
            stepContainer.appendChild(div("step-indent", " "));
        }
        stepContainer.appendChild(div("step-cell " + extraClass, step.getPrefix() + ' ' + step.getText()));
        Node stepRow = div("step-row",
                stepContainer,
                div("time-cell " + extraClass, Engine.formatNanos(result.getDurationNanos(), formatter)));
        parent.appendChild(stepRow);
        if (step.getTable() != null) {
            Node table = node("table", null);
            parent.appendChild(table);
            for (List<String> row : step.getTable().getRows()) {
                Node tr = node("tr", null);
                table.appendChild(tr);
                for (String cell : row) {
                    tr.appendChild(node("td", null, cell));
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (step.getDocString() != null) {
            sb.append(step.getDocString());
        }
        if (stepResult.isShowLog() && stepResult.getStepLog() != null) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(stepResult.getStepLog());
        }
        if (result.isFailed()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(result.getError().getMessage());
        }
        if (sb.length() > 0) {
            Element docStringNode = node("div", "preformatted", sb.toString());
            docStringNode.setAttribute("data-parent", refNum);
            parent.appendChild(docStringNode);
        }
        List<Embed> embeds = stepResult.getEmbeds();
        if (embeds != null) {
            for (Embed embed : embeds) {
                Element embedNode;
                String mimeType = embed.getMimeType().toLowerCase();
                if (mimeType.contains("image")) {
                    embedNode = node("img", null);
                    String src = embed.getAsHtmlData();
                    XmlUtils.addAttributes(embedNode, Collections.singletonMap("src", src));
                } else if (mimeType.contains("html")) {
                    Node html;
                    try {
                        html = XmlUtils.toXmlDoc(embed.getAsString()).getDocumentElement();
                    } catch (Exception e) {
                        html = div(null, e.getMessage());
                    }
                    html = doc.importNode(html, true);
                    embedNode = div(null, html);
                } else {
                    embedNode = div(null);
                    embedNode.setTextContent(embed.getAsString());
                }
                Element embedContainer = div("embed", embedNode);
                embedContainer.setAttribute("data-parent", refNum);
                parent.appendChild(embedContainer);
            }
        }
        List<FeatureResult> callResults = stepResult.getCallResults();
        if (callResults != null) { // this is a 'call'
            int index = 1;
            for (FeatureResult callResult : callResults) {
                callHtml(callResult, parent, depth, refNum + "." + index++);
            }
        }
    }

    private void callHtml(FeatureResult featureResult, Node parent, int depth, String callRefNum) {
        List<StepResult> stepResults = featureResult.getAllScenarioStepResultsNotHidden();
        if (stepResults.isEmpty()) {
            return;
        }
        String extraClass = featureResult.isFailed() ? "failed" : "passed";
        Element stepContainer = div("step-container");
        stepContainer.setAttribute("id", callRefNum);
        stepContainer.appendChild(div("step-ref " + extraClass, ">>"));
        for (int i = 0; i < depth; i++) {
            stepContainer.appendChild(div("step-indent", " "));
        }
        stepContainer.appendChild(div("step-cell " + extraClass, featureResult.getCallName()));
        Node stepRow = div("step-row",
                stepContainer,
                div("time-cell " + extraClass, Engine.formatMillis(featureResult.getDurationMillis(), formatter)));
        parent.appendChild(stepRow);
        String callArg = featureResult.getCallArgPretty();
        if (callArg != null) {
            Element callArgContainer = div("callarg-container");
            callArgContainer.setAttribute("data-parent", callRefNum);
            parent.appendChild(callArgContainer);
            callArgContainer.appendChild(div("step-ref", " "));
            for (int i = 0; i < depth; i++) {
                callArgContainer.appendChild(div("step-indent", " "));
            }
            callArgContainer.appendChild(node("div", "preformatted", callArg));
        }
        for (StepResult sr : stepResults) {
            stepHtml(sr, parent, depth + 1);
        }
    }

    //==========================================================================
    //
    public static File saveFeatureResult(String targetDir, FeatureResult result, boolean showConsoleMessage) {
        HtmlFeatureReport report = new HtmlFeatureReport(result);
        return report.save(targetDir, showConsoleMessage);
    }

    private HtmlFeatureReport(FeatureResult result) {
        baseName = result.getPackageQualifiedName();
        set("/html/head/title", baseName);
        for (ScenarioResult sr : result.getScenarioResults()) {
            Node scenarioDiv = div("scenario");
            contentContainer.appendChild(scenarioDiv);
            String scenarioMeta = sr.getScenario().getDisplayMeta();
            String scenarioName = sr.getScenario().getName();
            Element scenarioHeadingDiv = div("scenario-heading",
                    node("span", "scenario-keyword", sr.getScenario().getKeyword() + ": " + scenarioMeta),
                    node("span", "scenario-name", scenarioName));
            scenarioHeadingDiv.setAttribute("id", scenarioMeta);
            scenarioDiv.appendChild(scenarioHeadingDiv);
            String extraClass = sr.isFailed() ? "failed" : "passed";
            Element scenarioNav = div("scenario-nav " + extraClass);
            navContainer.appendChild(scenarioNav);
            Element scenarioLink = node("a", null, scenarioMeta + " " + scenarioName);
            scenarioNav.appendChild(scenarioLink);
            scenarioLink.setAttribute("href", "#" + scenarioMeta);
            for (StepResult stepResult : sr.getStepResults()) {
                stepHtml(stepResult, scenarioDiv, 0);
            }
        }
    }

    private File save(String targetDir, boolean showConsoleMessage) {
        String fileName = baseName + ".html";
        File file = new File(targetDir + File.separator + fileName);
        String xml = "<!DOCTYPE html>\n" + XmlUtils.toString(doc, false);
        try {
            initStaticResources(targetDir); // TODO improve init
            FileUtils.writeToFile(file, xml);
            if (showConsoleMessage) {
                System.out.println("\nHTML report: (paste into browser to view) | Karate version: "
                        + FileUtils.getKarateVersion() + "\n"
                        + file.toURI()
                        + "\n---------------------------------------------------------\n");
            }
        } catch (Exception e) {
            System.out.println("html report output failed: " + e.getMessage());
        }
        return file;
    }

}
