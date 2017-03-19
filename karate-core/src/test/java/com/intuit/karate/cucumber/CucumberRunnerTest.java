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

import cucumber.api.CucumberOptions;
import java.io.File;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(tags = {"~@ignore"})
public class CucumberRunnerTest {
    
    private static final Logger logger = LoggerFactory.getLogger(CucumberRunnerTest.class);
    
    private boolean contains(String reportPath, String textToFind) {
        try {
            String contents = FileUtils.readFileToString(new File(reportPath), "utf-8");
            return contents.contains(textToFind);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Test 
    public void testScenario() throws Exception {
        String reportPath = "target/scenario.xml";
        File file = new File("src/test/java/com/intuit/karate/cucumber/scenario.feature");
        CucumberRunner runner = new CucumberRunner(file);        
        KarateJunitFormatter formatter = new KarateJunitFormatter(file.getPath(), reportPath);
        runner.run(formatter);
        formatter.done();
        assertTrue(contains(reportPath, "Then match b == { foo: 'bar'}"));
    }
    
    @Test 
    public void testScenarioOutline() throws Exception {
        String reportPath = "target/outline.xml";
        File file = new File("src/test/java/com/intuit/karate/cucumber/outline.feature");
        CucumberRunner runner = new CucumberRunner(file);        
        KarateJunitFormatter formatter = new KarateJunitFormatter(file.getPath(), reportPath);
        runner.run(formatter);
        formatter.done();
        assertTrue(contains(reportPath, "When def a = 55"));
    }  
    
    @Test 
    public void testParallel() {
        CucumberRunner.parallel(getClass(), 1);
        String pathBase = "target/surefire-reports/TEST-com.intuit.karate.cucumber.";
        assertTrue(contains(pathBase + "scenario.xml", "Then match b == { foo: 'bar'}"));
        assertTrue(contains(pathBase + "outline.xml", "Then assert a == 55"));
        assertTrue(contains(pathBase + "multi-scenario.xml", "Then assert a != 2"));
    }
    
}
