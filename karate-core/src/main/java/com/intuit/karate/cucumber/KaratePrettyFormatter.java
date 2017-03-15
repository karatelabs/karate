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

import cucumber.runtime.formatter.ColorAware;
import gherkin.formatter.PrettyFormatter;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class KaratePrettyFormatter extends PrettyFormatter implements ColorAware {
    
    private static final Logger logger = LoggerFactory.getLogger(KaratePrettyFormatter.class);
    
    private final StringBuilder buffer;
    private int scenariosRun;
    private int scenariosFailed;
    
    public KaratePrettyFormatter() {
        this(new StringBuilder());
    }
    
    public KaratePrettyFormatter(StringBuilder buffer) {
        super(buffer, false, true);  
        this.buffer = buffer;
    }
    
    @Override
    public void setMonochrome(boolean monochrome) {
        super.setMonochrome(monochrome);
    }

    public StringBuilder getBuffer() {
        return buffer;
    }

    @Override
    public void scenario(Scenario scenario) {
        super.scenario(scenario);
        scenariosRun++;
    }    

    @Override
    public void result(Result result) {
        super.result(result);
        if (result.getError() != null) {
            scenariosFailed++;
            logger.error(result.getErrorMessage(), result.getError());            
        }
    }

    public int getScenariosRun() {
        return scenariosRun;
    }

    public int getScenariosFailed() {
        return scenariosFailed;
    }       
    
}
