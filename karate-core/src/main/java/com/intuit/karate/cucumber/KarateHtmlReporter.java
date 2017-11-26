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
    private List<Step> steps;
    private List<Result> results;
    private List<String> logs;
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
    
    private Node get(String path) {
        return XmlUtils.getNodeByPath(doc, path, false);
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
        String css = "body { font-family: monospace, monospace; font-size: small; }"
                + " table { border-collapse: collapse; }"
                + " table td { border: 1px solid gray; padding: 0.1em 0.2em; }"
                + " .scenario-heading { background-color: #F5F28F; padding: 0.2em 0.5em; border: 1px solid gray; }"
                + " .scenario-name { font-weight: bold; }"
                + " .step-row { margin: 0.2em 0; }"
                + " .step-cell { background-color: #92DD96; display: inline-block; width: 85%; padding: 0.2em 0.5em; }"
                + " .time-cell { background-color: #92DD96; display: inline-block; width: 10%; padding: 0.2em 0.5em; }"
                + " .failed { background-color: #F2928C; }"
                + " .skipped { background-color: #8AF; }";
        set("/html/head/style", css);
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
            Node pre = node("pre", null);
            pre.setTextContent(log);
            parent.appendChild(pre);
        }        
    }

    //==========================================================================
    
    @Override
    public void karateStepDelegate(Step step, boolean called, Match match, Result result) { 
        // step should be first
        steps.add(step);
        formatter.step(step);
        // match
        match(match);
        // result
        results.add(result);
        logs.add(logAppender.collect());
        // result downstream
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
        results = new ArrayList();
        logs = new ArrayList();        
        Node scenarioDiv = node("div", "scenario-div");
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

    @Override
    public void endOfScenarioLifeCycle(Scenario scenario) {
        Node scenarioDiv = get("/html/body/div/div[" + currentScenario + "]");
        int count = steps.size();
        for (int i = 0; i < count; i++) {
            Step step = steps.get(i);
            Result result = results.get(i);
            String extraClass = "";
            if ("failed".equals(result.getStatus())) {
                extraClass = " failed";
            } else if ("skipped".equals(result.getStatus())) {
                extraClass = " skipped";
            }
            Node stepRow = div("step-row",
                    div("step-cell" + extraClass, step.getKeyword() + step.getName()),
                    div("time-cell" + extraClass, getDuration(result)));
            scenarioDiv.appendChild(stepRow);
            if (step.getDocString() != null) {
                DocString docString = step.getDocString();
                scenarioDiv.appendChild(node("pre", null, docString.getValue()));
            }
            if (step.getRows() != null) {
                Node table = node("table", null);
                scenarioDiv.appendChild(table);
                for (DataTableRow row : step.getRows()) {
                    Node tr = node("tr", null);
                    table.appendChild(tr);
                    for (String cell : row.getCells()) {
                        tr.appendChild(node("td", null, cell));
                    }
                }
            }
            appendLog(scenarioDiv, logs.get(i));
        }
        scenarioDiv.appendChild(node("br", null));        
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
