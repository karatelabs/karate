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

import cucumber.runtime.formatter.CucumberJSONFormatter;
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
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class KarateReporter implements Formatter, Reporter {
    
    private final KarateJunitFormatter junit;
    private final CucumberJSONFormatter json;
    
    private static final Object DUMMY_OBJECT = new Object();
    public static final Result PASSED = new Result(Result.PASSED, 0L, null, DUMMY_OBJECT);

    public static Result failed(Throwable t) {
        return new Result(Result.FAILED, 0L, t, DUMMY_OBJECT);
    }

    public void step(String text) {
        Step step = new Step(null, "* ", text, 0, null, null);
        step(step);
        result(PASSED);
    }
    
    public KarateJunitFormatter getJunitFormatter() {
        return junit;
    }
    
    public KarateReporter(String featurePath, String reportPath) throws IOException {
        junit = new KarateJunitFormatter(featurePath, reportPath);
        String jsonReportPath = reportPath.replaceFirst("\\.xml$", ".json");
        FileWriter fileWriter = new FileWriter(jsonReportPath);
        json = new CucumberJSONFormatter(fileWriter);
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
    }

    @Override
    public void feature(Feature feature) {
        junit.feature(feature);
        json.feature(feature);
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
    public void step(Step step) {
        junit.step(step);
        json.step(step);
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
