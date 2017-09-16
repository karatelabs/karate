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
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeatureReuseTest {
    
    private static final Logger logger = LoggerFactory.getLogger(FeatureReuseTest.class);
    
    @Test
    public void testFailureInCalledShouldFailTest() throws Exception {
        String reportPath = "target/fail.xml";
        File file = new File("src/test/java/com/intuit/karate/cucumber/caller.feature");
        KarateReporter reporter = CucumberRunnerTest.run(file, reportPath);
        assertEquals(1, reporter.getJunitFormatter().getFailCount());
        String contents = FileUtils.toString(new File(reportPath));
        assertTrue(contents.contains("assert evaluated to false: input != 4"));
    }
    
    @Test
    public void testArgumentsPassedForSharedScope() throws Exception {
        String reportPath = "target/pass.xml";
        File file = new File("src/test/java/com/intuit/karate/cucumber/caller-shared.feature");
        KarateReporter reporter = CucumberRunnerTest.run(file, reportPath);
        assertEquals(0, reporter.getJunitFormatter().getFailCount());
        String contents = FileUtils.toString(new File(reportPath));
        assertTrue(contents.contains("passed"));
    }    
    
    @Test
    public void testCallerTwo() throws Exception {
        String reportPath = "target/pass2.xml";
        File file = new File("src/test/java/com/intuit/karate/cucumber/caller_2.feature");
        KarateReporter reporter = CucumberRunnerTest.run(file, reportPath);
        assertEquals(0, reporter.getJunitFormatter().getFailCount());
        String contents = FileUtils.toString(new File(reportPath));
        assertTrue(contents.contains("passed"));
    } 
    
}
