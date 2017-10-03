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
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;

/**
 *
 * @author pthomas3
 */
public class KarateHtmlReporter implements Reporter, Formatter {
    
    private final Reporter reporter;
    private final Formatter formatter;
    
    private CucumberFeature feature;
    private Document doc;
    private List<Step> steps;
    private List<Result> results;
    private int currentScenario;
    private int exampleNumber;
    
    public KarateHtmlReporter(Reporter reporter, Formatter formatter) {
        this.reporter = reporter;
        this.formatter = formatter;
    }        

    public void startKarateFeature(CucumberFeature feature) {
        currentScenario = 0;       
        this.feature = feature;
        doc = XmlUtils.toXmlDoc("<html/>");
        XmlUtils.setByPath(doc, "/html/head/title", feature.getPath());
    }    

    public void endKarateFeature() {        
        String xml = "<!DOCTYPE html>\n" + XmlUtils.toString(doc);
        String packageName = FileUtils.toPackageQualifiedName(feature.getPath());
        File file = new File("target/surefire-reports/TEST-" + packageName + ".html");
        FileUtils.writeToFile(file, xml);
        System.out.println("html report:\n" + file.toURI());
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

    @Override
    public void startOfScenarioLifeCycle(Scenario scenario) {
        steps = new ArrayList();
        results = new ArrayList();
        currentScenario++;
        XmlUtils.setByPath(doc, "/html/body/div[" + currentScenario + "]/div[1]", getScenarioName(scenario));
        formatter.startOfScenarioLifeCycle(scenario);
    }
    
    @Override
    public void examples(Examples examples) {
        exampleNumber = 0;
        formatter.examples(examples);
    }    
    
    @Override
    public void endOfScenarioLifeCycle(Scenario scenario) {
        int count = steps.size();
        for (int i = 0; i < count; i++) {
            Step step = steps.get(i);
            Result result = results.get(i);
            String text = step.getName() + " " + result.getStatus();
            XmlUtils.setByPath(doc, "/html/body/div[" + currentScenario + "]/div[2]/div[" + i + 1 + "]", text);
        }
        formatter.endOfScenarioLifeCycle(scenario);
    }    

    @Override
    public void step(Step step) {
        if (steps == null) {
            steps = new ArrayList();
            results = new ArrayList();            
        }
        steps.add(step);
        formatter.step(step);
    }

    @Override
    public void result(Result result) {
        results.add(result);
        reporter.result(result);
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
