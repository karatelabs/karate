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

import java.io.File;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class CucumberRunnerTest {
    
    private static final Logger logger = LoggerFactory.getLogger(CucumberRunnerTest.class);
    
    @Test 
    public void testScenario() {
        File file = new File("src/test/java/com/intuit/karate/cucumber/scenario.feature");
        CucumberRunner runner = new CucumberRunner(file);        
        KaratePrettyFormatter formatter = new KaratePrettyFormatter();
        runner.run(formatter);
        System.out.print(formatter.getBuffer());
        logger.debug("scenarios run: {}, failed: {}", formatter.getScenariosRun(), formatter.getScenariosFailed());
    }
    
    @Test 
    public void testScenarioOutline() {
        File file = new File("src/test/java/com/intuit/karate/cucumber/outline.feature");
        CucumberRunner runner = new CucumberRunner(file);        
        KaratePrettyFormatter formatter = new KaratePrettyFormatter();
        runner.run(formatter);
        System.out.print(formatter.getBuffer());        
        logger.debug("scenarios run: {}, failed: {}", formatter.getScenariosRun(), formatter.getScenariosFailed());
    }  
    
    @Test 
    public void testParallel() {
        CucumberRunner.parallel(getClass(), 1);
    }
    
}
