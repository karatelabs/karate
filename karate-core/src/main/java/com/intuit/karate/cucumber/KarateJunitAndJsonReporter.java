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

import com.intuit.karate.StringUtils;
import cucumber.runtime.formatter.CucumberJSONFormatter;
import gherkin.formatter.model.Background;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class KarateJunitAndJsonReporter extends KarateReporterBase {

    private final KarateJunitFormatter junit;
    private final CucumberJSONFormatter json;
    
    private Exception failureReason;

    public void setFailureReason(Exception failureReason) {
        this.failureReason = failureReason;
    }

    public Exception getFailureReason() {
        return failureReason;
    }        

    public KarateJunitFormatter getJunitFormatter() {
        return junit;
    }

    public KarateJunitAndJsonReporter(String featurePath, String reportPath) throws IOException {
        junit = new KarateJunitFormatter(featurePath, reportPath);
        String jsonReportPath = reportPath.replaceFirst("\\.xml$", ".json");
        FileWriter fileWriter = new FileWriter(jsonReportPath);
        json = new CucumberJSONFormatter(fileWriter);
    }        

    @Override
    public void karateStepDelegate(Step step, boolean called, Match match, Result result) {
        junit.step(step);
        json.step(step);
        // step has to happen first !
        match(match);
        result(result);        
    }        

    @Override
    public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
        junit.syntaxError(state, event, legalEvents, uri, line);
        json.syntaxError(state, event, legalEvents, uri, line);
    }

    @Override
    public void uri(String uri) {
        junit.uri(uri);
        json.uri(uri);
        this.uri = uri;
    }

    private String uri;

    private Feature rename(Feature f) {
        String name = uri; // swap
        String description = f.getName();
        String extra = StringUtils.trimToNull(f.getDescription());
        if (extra != null) {
            description = description + '\n' + extra;
        }
        return new Feature(f.getComments(), f.getTags(), f.getKeyword(), name, description, f.getLine(), f.getId());
    }

    @Override
    public void feature(Feature feature) {
        junit.feature(feature);
        json.feature(rename(feature)); // use the uri as the name
    }

    @Override
    public void scenarioOutline(ScenarioOutline scenarioOutline) {
        junit.scenarioOutline(scenarioOutline);
        json.scenarioOutline(scenarioOutline);
    }

    @Override
    public void examples(Examples examples) {
        junit.examples(examples);
        json.examples(examples);
    }

    @Override
    public void startOfScenarioLifeCycle(Scenario scenario) {
        junit.startOfScenarioLifeCycle(scenario);
        json.startOfScenarioLifeCycle(scenario);
    }

    @Override
    public void background(Background background) {
        junit.background(background);
        json.background(background);
    }

    @Override
    public void scenario(Scenario scenario) {
        junit.scenario(scenario);
        json.scenario(scenario);
    }

    @Override
    public void endOfScenarioLifeCycle(Scenario scenario) {
        junit.endOfScenarioLifeCycle(scenario);
        json.endOfScenarioLifeCycle(scenario);
    }

    @Override
    public void done() {
        junit.done();
        json.done();
    }

    @Override
    public void close() {
        junit.close();
        json.close();
    }

    @Override
    public void eof() {
        junit.eof();
        json.eof();
    }

    @Override
    public void before(Match match, Result result) {
        junit.before(match, result);
        json.before(match, result);
    }

    @Override
    public void result(Result result) {
        junit.result(result);
        json.result(result);
    }

    @Override
    public void after(Match match, Result result) {
        junit.after(match, result);
        json.after(match, result);
    }

    @Override
    public void match(Match match) {
        junit.match(match);
        json.match(match);
    }

    @Override
    public void embedding(String mimeType, byte[] data) {
        junit.embedding(mimeType, data);
        json.embedding(mimeType, data);
    }

    @Override
    public void write(String text) {
        junit.write(text);
        json.write(text);
    }

}
