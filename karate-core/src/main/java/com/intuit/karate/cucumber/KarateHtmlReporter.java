/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.cucumber;

import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import cucumber.runtime.model.CucumberFeature;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Background;
import gherkin.formatter.model.DataTableRow;
import gherkin.formatter.model.DocString;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;
import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class KarateHtmlReporter extends KarateReporterBase {

    private final Reporter reporter;
    private final Formatter formatter;

    private CucumberFeature feature;
    private Document doc;
    private List<ReportStep> steps;
    private int currentScenario;
    private int exampleNumber;

    private final DecimalFormat NUMBER_FORMAT = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);

    public KarateHtmlReporter(Reporter reporter, Formatter formatter) {
        this.reporter = reporter;
        this.formatter = formatter;
        NUMBER_FORMAT.applyPattern("0.######");
    }

    private void append(String path, Node node) {
        Node temp = XmlUtils.getNodeByPath(doc, path, true);
        temp.appendChild(node);
    }

    private void set(String path, String value) {
        XmlUtils.setByPath(doc, path, value);
    }

    private Node node(String name, String clazz, String text) {
        return XmlUtils.createElement(doc, name, text, clazz == null ? null : Collections.singletonMap("class", clazz));
    }

    private Node node(String name, String clazz) {
        return node(name, clazz, null);
    }

    private Node div(String clazz, String value) {
        return node("div", clazz, value);
    }

    private Node div(String clazz, Node... childNodes) {
        Node parent = node("div", clazz);
        for (Node child : childNodes) {
            parent.appendChild(child);
        }
        return parent;
    }

    public void startKarateFeature(CucumberFeature feature) {
        currentScenario = 0;
        this.feature = feature;
        doc = XmlUtils.toXmlDoc("<html/>");
        set("/html/head/title", feature.getPath());
        String css = "body { font-family: sans-serif; font-size: small; }"
                + " table { border-collapse: collapse; }"
                + " table td { border: 1px solid gray; padding: 0.1em 0.2em; }"
                + " .scenario-heading { background-color: #F5F28F; padding: 0.2em 0.5em; border-bottom: 1px solid gray; }"
                + " .scenario-name { font-weight: bold; }"
                + " .scenario { border: 1px solid gray; margin-bottom: 1em; }"
                + " .scenario-steps { padding-left: 0.2em; }"
                + " .scenario-steps-nested { padding-left: 2em; }"
                + " .step-row { margin: 0.2em 0; }"
                + " .step-cell { display: inline-block; width: 85%; padding: 0.2em 0.5em; }"
                + " .time-cell { background-color: #92DD96; display: inline-block; width: 10%; padding: 0.2em 0.5em; }"
                + " .preformatted { white-space: pre-wrap; font-family: monospace; }"
                + " .passed { background-color: #92DD96; }"
                + " .failed { background-color: #F2928C; }"
                + " .skipped { background-color: #f5f28f; }"
                + " .passed_font { color: #92DD96; font-size: 15px; }"
                + " .failed_font { color: #F2928C; font-size: 15px; }"
                + " .skipped_font { color: #f5f28f; font-size: 15px; }"
                + " .passed_border { display: block; pading: 5px; border: 2px solid #92DD96; }"
                + " .failed_border { display: block; padding: 5px; border: 2px solid #F2928C; }"
                + " .skipped_border { display: block; padding: 5px; border: 2px solid #F5F28F; }"
                + " .sidenav { height: 100%; width: 300px; position: fixed; z-index: 1; top: 0; left: 0; background-color: #111; overflow-x: hidden; padding-top: 20px;}"
                + " .sidenav a { padding: 6px 6px 6px 16px; text-decoration: none; font-size: 15px; color: #000 !important; display: inline-block; border: 1px solid #111; }"
                + " .sidenav h2 { color: #fff; padding: 10px; }"
                + " .sidenav p { padding: 5px; font-size: 13px; }"
                + " .sidenav a:hover { color: #fff !important; }"
                + " .panel { position: relative; left: 5px; width: 20px !important; font-weight: 200; }"
                + " .scenario { margin-left: 300px; padding: 0px 10px; }"
                + " @media screen and (max-height: 450px) {"
                + "     .sidenav { padding-top: 15px; }"
                + "   .sidenav a { font-size: 18px; }"
                + " }";

        String js = "window.onload = function(){"
                + "\n  possibleModes = ['failed', 'skipped', 'passed'];"
                + "\n  tests = {};"
                + "\n  modeBorder = {};" 
                + "\n  for (mode of possibleModes) {"
                + "\n    tests[mode] = [];"
                + "\n    modeBorder[mode] = document.createElement('div');"
                + "\n    modeBorder[mode].classList.add(mode+'_border');"
                + "\n  }"
                + "\n  sidenavDiv = document.createElement('div');"
                + "\n  sidenavDiv.classList.add('sidenav');"
                + "\n  sidenavDiv.innerHTML += '<h2>Test Suite Navigation</h2>';"
                + "\n  document.body.appendChild(sidenavDiv);"
                + "\n  scenarios = document.getElementsByClassName('step-cell');"
                + "\n  for (i = 0; i < scenarios.length; ++i) {"
                + "\n    scenarios[i].id = getIdForTest(i);"
                + "\n    scenarios[i].innerHTML = (getTextForTest(i) + ' : ' + scenarios[i].innerHTML);"
                + "\n    mode = scenarios[i].classList[1];"
                + "\n    tests[mode].push(i);"
                + "\n  }"
                + "\n  for (mode of possibleModes) {"
                + "\n    buildSidebarVerboseReport(mode);"
                + "\n    buildSidebarAnchors(mode);"
                + "\n    sidenavDiv.appendChild(modeBorder[mode]);"
                + "\n  }"
                + "\n  console.log(tests);"
                + "\n  console.log(modeBorder);"
                + "\n  function getIdForTest(i) {"
                + "\n    return('test_'+(i+1));"
                + "\n  }"
                + "\n  function getTextForTest(i) {"
                + "\n    return('Test '+(i+1));"  
                + "\n  }"
                + "\n  function buildSidebarVerboseReport(mode) {"
                + "\n    verboseReport = document.createElement('p');"
                + "\n    verboseReport.appendChild(document.createTextNode('# of ' + mode + ' tests: ' + tests[mode].length + '/' + scenarios.length));"
                + "\n    verboseReport.appendChild(document.createElement('br'));"
                + "\n    verboseReport.appendChild(document.createTextNode('('+(tests[mode].length/scenarios.length)*100 + '%)'));"
                + "\n    verboseReport.classList.add(mode+'_font');"
                + "\n    modeBorder[mode].appendChild(verboseReport);"
                + "\n  }"
                + "\n  function buildSidebarAnchors(mode) {"
                + "\n    suchTests = tests[mode];"
                + "\n    for (i=0; i<suchTests.length; ++i) {"
                + "\n      anchor = document.createElement('a');"
                + "\n      anchor.setAttribute('href', '#'+getIdForTest(suchTests[i]));"
                + "\n      anchor.appendChild(document.createTextNode(suchTests[i]+1));"
                + "\n      anchor.classList.add('panel');"
                + "\n      anchor.classList.add(mode);"
                + "\n      modeBorder[mode].appendChild(anchor);"
                + "\n      if ((i+1)%6==0) {"
                + "\n        modeBorder[mode].appendChild(document.createElement('br'));"
                + "\n      }"
                + "\n    }"
                + "\n    modeBorder[mode].appendChild(document.createElement('br'));"
                + "\n  }"
                + "\n }";

        set("/html/head/style", css);
        set("/html/body/script", js);
    }

    public void endKarateFeature() {
        String xml = "<!DOCTYPE html>\n" + XmlUtils.toString(doc);
        String packageName = FileUtils.toPackageQualifiedName(feature.getPath());
        File file = new File("target/surefire-reports/TEST-" + packageName + ".html");
        try {
            FileUtils.writeToFile(file, xml);
            System.out.println("html report: (paste into browser to view)\n"
                    + "-----------------------------------------\n"
                    + file.toURI() + '\n');
        } catch (Exception e) {
            System.out.println("html report output failed: " + e.getMessage());
        }
    }

    private String getScenarioName(Scenario scenario) {
        String scenarioName = StringUtils.trimToNull(scenario.getName());
        if (scenarioName == null) {
            scenarioName = currentScenario + "";
        }
        if (scenario.getKeyword().equals("Scenario Outline")) {
            return scenarioName + " (" + (++exampleNumber) + ")";
        } else {
            return scenarioName;
        }
    }

    private String getDuration(Result result) {
        if (result.getDuration() == null) {
            return "-";
        }
        double duration = ((double) result.getDuration()) / 1000000000;
        return NUMBER_FORMAT.format(duration);
    }

    private void appendLog(Node parent, String log) {
        if (!log.isEmpty()) {
            Node pre = node("div", "preformatted");
            pre.setTextContent(log);
            parent.appendChild(pre);
        }
    }

    //==========================================================================
    private ReportStep prevStep;
    private Stack<List<ReportStep>> callStack;

    @Override
    public void karateStepProceed(Step step, Match match, Result result, CallContext callContext) {
        String log = logAppender.collect();
        // step should be first        
        int prevDepth = prevStep == null ? 0 : prevStep.getCallContext().callDepth;
        int currDepth = callContext.callDepth;
        prevStep = new ReportStep(step, match, result, log, callContext);
        if (currDepth > prevDepth) { // called            
            List<ReportStep> temp = new ArrayList();
            temp.add(prevStep);
            callStack.push(temp);
        } else {
            if (currDepth < prevDepth) { // callBegin return                
                for (ReportStep s : callStack.pop()) {
                    prevStep.addCalled(s);
                }
            }
            if (callStack.isEmpty()) {
                steps.add(prevStep);
            } else {
                callStack.peek().add(prevStep);
            }
        }
        // just pass on to downstream
        formatter.step(step);
        reporter.match(match);
        reporter.result(result);
    }

    @Override
    public void result(Result result) {
        // only downstream
        reporter.result(result);
    }

    @Override
    public void startOfScenarioLifeCycle(Scenario scenario) {
        currentScenario++;
        steps = new ArrayList();
        prevStep = null;
        callStack = new Stack();
        Node scenarioDiv = div("scenario");
        append("/html/body/div", scenarioDiv);
        Node scenarioHeadingDiv = div("scenario-heading",
                node("span", "scenario-keyword", scenario.getKeyword() + ": "),
                node("span", "scenario-name", getScenarioName(scenario)));
        scenarioDiv.appendChild(scenarioHeadingDiv);
        formatter.startOfScenarioLifeCycle(scenario);
    }

    @Override
    public void examples(Examples examples) {
        exampleNumber = 0;
        formatter.examples(examples);
    }
    
    private void stepHtml(ReportStep reportStep, Node parent) {
        Step step = reportStep.getStep();
        Result result = reportStep.getResult();
        String extraClass = "";
        if ("failed".equals(result.getStatus())) {
            extraClass = " failed";
        } else if ("skipped".equals(result.getStatus())) {
            extraClass = " skipped";
        } else {
            extraClass = " passed";            
        }
        Node stepRow = div("step-row",
                div("step-cell" + extraClass, step.getKeyword() + step.getName()),
                div("time-cell" + extraClass, getDuration(result)));
        parent.appendChild(stepRow);
        if (step.getRows() != null) {
            Node table = node("table", null);
            parent.appendChild(table);
            for (DataTableRow row : step.getRows()) {
                Node tr = node("tr", null);
                table.appendChild(tr);
                for (String cell : row.getCells()) {
                    tr.appendChild(node("td", null, cell));
                }
            }
        }               
        if (reportStep.getCalled() != null) { // this is a 'call'
            for (ReportStep rs : reportStep.getCalled()) {
                Node calledStepsDiv = div("scenario-steps-nested");
                parent.appendChild(calledStepsDiv); 
                stepHtml(rs, calledStepsDiv);
            }            
        } else if (step.getDocString() != null) { // only for non-call, else un-synced stack traces may creep in
            DocString docString = step.getDocString();
            parent.appendChild(node("div", "preformatted", docString.getValue()));            
        }
        appendLog(parent, reportStep.getLog());
    }

    @Override
    public void endOfScenarioLifeCycle(Scenario scenario) {
        Node scenarioStepsDiv = div("scenario-steps");
        append("/html/body/div/div[" + currentScenario + "]", scenarioStepsDiv);
        int count = steps.size();
        for (int i = 0; i < count; i++) {
            ReportStep reportStep = steps.get(i);
            stepHtml(reportStep, scenarioStepsDiv);
        }
        formatter.endOfScenarioLifeCycle(scenario);
    }

    // as-is ===================================================================
    @Override
    public void feature(Feature f) {
        formatter.feature(f);
    }

    @Override
    public void done() {
        formatter.done();
    }

    @Override
    public void background(Background background) {
        formatter.background(background);
    }

    @Override
    public void scenario(Scenario scenario) {
        formatter.scenario(scenario);
    }

    @Override
    public void scenarioOutline(ScenarioOutline scenarioOutline) {
        formatter.scenarioOutline(scenarioOutline);
    }

    @Override
    public void match(Match match) {
        reporter.match(match);
    }

    @Override
    public void embedding(String mimeType, byte[] data) {
        reporter.embedding(mimeType, data);
    }

    @Override
    public void write(String text) {
        reporter.write(text);
    }

    @Override
    public void uri(String uri) {
        formatter.uri(uri);
    }

    @Override
    public void close() {
        formatter.close();
    }

    @Override
    public void eof() {
        formatter.eof();
    }

    @Override
    public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
        formatter.syntaxError(state, event, legalEvents, uri, line);
    }

    @Override
    public void before(Match match, Result result) {
        reporter.before(match, result);
    }

    @Override
    public void after(Match match, Result result) {
        reporter.after(match, result);
    }

}
