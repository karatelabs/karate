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

import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Background;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public abstract class KarateBaseReporter implements Formatter, Reporter {
    
    @Override
    public void background(Background background) {
        
    }

    @Override
    public void scenario(Scenario scenario) {
        
    }

    @Override
    public void scenarioOutline(ScenarioOutline scenarioOutline) {

    }

    @Override
    public void match(Match match) {
        
    }

    @Override
    public void embedding(String mimeType, byte[] data) {
        
    }

    @Override
    public void write(String text) {
        
    }

    @Override
    public void uri(String uri) {
        
    }

    @Override
    public void close() {
        
    }

    @Override
    public void eof() {
        
    }
    
    @Override
    public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
        
    }

    @Override
    public void before(Match match, Result result) {
        
    }

    @Override
    public void after(Match match, Result result) {
        
    }    
    
}
