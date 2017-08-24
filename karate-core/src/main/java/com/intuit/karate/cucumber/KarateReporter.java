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

import com.intuit.karate.JsonUtils;
import cucumber.runtime.formatter.CucumberJSONFormatter;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Background;
import gherkin.formatter.model.DocString;
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
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class KarateReporter implements Formatter, Reporter {

    private final KarateJunitFormatter junit;
    private final CucumberJSONFormatter json;
    private final ReporterLogAppender logAppender;

    public static final Object DUMMY_OBJECT = new Object();

    public static Result passed(long time) {
        return new Result(Result.PASSED, time, null, DUMMY_OBJECT);
    }

    public static Result failed(long time, Throwable t) {
        return new Result(Result.FAILED, null, t, DUMMY_OBJECT);
    }

    public void call(FeatureWrapper feature, int index, Map<String, Object> arg) {
        DocString docString = null;
        if (arg != null) {
            String json = JsonUtils.toPrettyJsonString(JsonUtils.toJsonDoc(arg));
            docString = new DocString("", json, 0);
        }
        String suffix = index == -1 ? " " : "[" + index + "] ";
        Step step = new Step(null, "* ", "call" + suffix + feature.getPath(), 0, null, docString);
        karateStep(step);
        match(Match.UNDEFINED);
        result(passed(0L));
    }

    // see the step() method for an explanation of this hack
    public void karateStep(Step step) {
        if (step.getDocString() == null) {
            String log = logAppender.collect();
            DocString docString = log.isEmpty() ? null : new DocString("", log, step.getLine());
            step = new Step(step.getComments(), step.getKeyword(), step.getName(), step.getLine(), step.getRows(), docString);
        }
        junit.step(step);
        json.step(step);
    }

    public KarateJunitFormatter getJunitFormatter() {
        return junit;
    }

    public KarateReporter(String featurePath, String reportPath) throws IOException {
        junit = new KarateJunitFormatter(featurePath, reportPath);
        String jsonReportPath = reportPath.replaceFirst("\\.xml$", ".json");
        FileWriter fileWriter = new FileWriter(jsonReportPath);
        json = new CucumberJSONFormatter(fileWriter);
        logAppender = new ReporterLogAppender();
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
        // hack alert !
        // normally the cucumber formatter iterates over all steps before execution begins
        // we don't, and this actually speeds up things considerably, see also CucumberUtils.runStep()
        // now we can 'in-line' called feature steps in the final report, plus time stats - see StepWrapper.run()
        // the downside is that on failure, we don't show skipped steps
        // but really, this should not be a big concern for karate users
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
