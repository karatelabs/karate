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

    private final FeatureResult featureResult;

    private int stepCounter;

    private void stepHtml(boolean calledFromBackground, String scenarioMeta, StepResult stepResult, Node parent, int depth) {
        String refNum = ++stepCounter + "";
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
        boolean isBackground = calledFromBackground || (depth == 0 && step.isBackground());
        List<String> comments = step.getComments();
        if (comments != null) {
            for (String comment : comments) {
                Element commentContainer = div("step-container");
                String refClass = isBackground ? "bg-step" : extraClass;
                commentContainer.appendChild(div("step-ref " + refClass, ""));
                for (int i = 0; i < depth; i++) {
                    commentContainer.appendChild(div("step-indent", " "));
                }
                commentContainer.appendChild(div("step-cell comment", comment));
                Element commentRow = div("step-row",
                        commentContainer,
                        div("time-cell comment"));
                if (isBackground) {
                    commentRow.setAttribute("data-parent", scenarioMeta + "bg");
                }
                parent.appendChild(commentRow);
            }
        }
        Element stepContainer = div("step-container");
        stepContainer.setAttribute("id", refNum);
        String refClass = isBackground ? "bg-step" : extraClass;
        stepContainer.appendChild(div("step-ref " + refClass, refNum));
        for (int i = 0; i < depth; i++) {
            stepContainer.appendChild(div("step-indent", " "));
        }
        stepContainer.appendChild(div("step-cell " + extraClass, step.getPrefix() + ' ' + step.getText()));
        Element stepRow = div("step-row",
                stepContainer,
                div("time-cell " + extraClass, formatter.format(result.getDurationMillis())));
        if (isBackground) {
            stepRow.setAttribute("data-parent", scenarioMeta + "bg");
        }
        parent.appendChild(stepRow);
        if (step.getTable() != null) {
            Element table = node("table", null);
            table.setAttribute("data-parent", refNum);
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
                callHtml(isBackground, scenarioMeta, callResult, parent, depth, refNum + "." + index++);
            }
        }
    }

    private void callHtml(boolean isBackground, String scenarioMeta, FeatureResult featureResult, Node parent, int depth, String callRefNum) {
        List<StepResult> stepResults = featureResult.getAllScenarioStepResultsNotHidden();
        if (stepResults.isEmpty()) {
            return;
        }
        String extraClass = featureResult.isFailed() ? "failed" : "passed";
        Element stepContainer = div("step-container");
        stepContainer.setAttribute("id", callRefNum);
        stepContainer.appendChild(div("step-ref " + (isBackground ? "bg-step" : extraClass), ">>"));
        for (int i = 0; i < depth; i++) {
            stepContainer.appendChild(div("step-indent", " "));
        }
        stepContainer.appendChild(div("step-cell " + extraClass, featureResult.getCallName()));
        Element stepRow = div("step-row",
                stepContainer,
                div("time-cell " + extraClass, formatter.format(featureResult.getDurationMillis())));
        parent.appendChild(stepRow);
        if (isBackground) {
            stepRow.setAttribute("data-parent", scenarioMeta + "bg");
        }
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
            stepHtml(isBackground, scenarioMeta, sr, parent, depth + 1);
        }
    }

    //==========================================================================
    //
    public static File saveFeatureResult(String targetDir, FeatureResult result) {
        HtmlFeatureReport report = new HtmlFeatureReport(result);
        return report.save(targetDir);
    }

    private HtmlFeatureReport(FeatureResult featureResult) {
        this.featureResult = featureResult;
        Feature feature = featureResult.getFeature();
        set("/html/head/title", featureResult.getPackageQualifiedName());
        setById("nav-type", "Scenarios");
        setById("nav-pass", featureResult.getPassedCount() + "");
        setById("nav-fail", featureResult.getFailedCount() + "");
        String featureName = featureResult.getDisplayUri();
        String featureDescription = feature.getNameAndDescription();
        Node featureHeading = div("page-heading alert alert-primary",
                summaryLink(),
                node("span", "feature-label", "|"),
                tagsLink(),
                node("span", "feature-label", "|"),
                node("span", "feature-label", "Feature:"),
                node("span", "feature-name", featureName),
                node("span", "feature-description", featureDescription));
        contentContainer.appendChild(featureHeading);
        for (ScenarioResult sr : featureResult.getScenarioResults()) {
            Node scenarioDiv = div("scenario");
            contentContainer.appendChild(scenarioDiv);
            Scenario scenario = sr.getScenario();
            String scenarioMeta = scenario.getDisplayMeta();
            String scenarioName = scenario.getNameAndDescription();
            String extraClass = sr.isFailed() ? "failed" : "passed";
            Tags tags = scenario.getTagsEffective();
            Element tagsDiv = div("scenario-tags");
            for (Tag tag : tags.getOriginal()) {
                Element tagLink = node("a", "badge badge-primary");
                tagsDiv.appendChild(tagLink);
                tagLink.setAttribute("href", "karate-tags.html");
                tagLink.setTextContent(tag.getText());
            }
            Element headingContainer = div("heading-container",
                    tagsDiv,
                    node("span", "scenario-keyword", scenario.getKeyword() + ": " + scenarioMeta),
                    node("span", "scenario-name", scenarioName));
            String duration = formatter.format(sr.getDurationMillis());
            Element scenarioHeadingDiv = div("scenario-heading",
                    headingContainer,
                    div("scenario-time " + extraClass, "ms: " + duration));
            scenarioHeadingDiv.setAttribute("id", scenarioMeta);
            scenarioDiv.appendChild(scenarioHeadingDiv);
            Element scenarioNav = div("nav-item " + extraClass);
            navContainer.appendChild(scenarioNav);
            Element scenarioLink = node("a", null, scenarioMeta + " " + scenarioName);
            scenarioNav.appendChild(scenarioLink);
            scenarioLink.setAttribute("href", "#" + scenarioMeta);
            List<StepResult> stepResults = sr.getStepResults();
            if (!stepResults.isEmpty() && stepResults.get(0).getStep().isBackground()) {
                Element bgContainer = div("step-container");
                bgContainer.setAttribute("id", scenarioMeta + "bg");
                bgContainer.appendChild(div("step-ref bg-step", ">>"));
                bgContainer.appendChild(div("step-cell passed", "Background:"));
                Node bgRow = div("step-row",
                        bgContainer,
                        div("time-cell passed", ""));
                scenarioDiv.appendChild(bgRow);
            }
            for (StepResult stepResult : stepResults) {
                stepHtml(false, scenarioMeta, stepResult, scenarioDiv, 0);
            }
        }
    }

    private File save(String targetDir) {
        return saveHtmlToFile(targetDir, getHtmlFileName(featureResult));
    }

}
