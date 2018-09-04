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
package com.intuit.karate.ui;

import com.intuit.karate.CallContext;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.core.ExecutionContext;
import com.intuit.karate.core.FeatureSection;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioExecutionUnit;
import com.intuit.karate.core.ScenarioOutline;
import java.io.File;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class AppSessionTest {
    
    private static final Logger logger = LoggerFactory.getLogger(AppSessionTest.class);
    
    @Test
    public void testRunning() {
        File tempFile = new File("src/test/java/com/intuit/karate/ui/test.feature");
        AppSession session = new AppSession(tempFile, null, true);
        ExecutionContext ec = new ExecutionContext(session.getFeature(), session.getEnv(), new CallContext(null, true), null);
        for (FeatureSection section : session.getFeature().getSections()) {
            if (section.isOutline()) {
                ScenarioOutline outline = section.getScenarioOutline();
                for (Scenario scenario : outline.getScenarios()) {
                    call(scenario, session, ec);
                }
            } else {
                call(section.getScenario(), session, ec);
            }
        }        
    }
    
    private static void call(Scenario scenario, AppSession session, ExecutionContext ec) {
        ScenarioExecutionUnit exec = new ScenarioExecutionUnit(scenario, session.getActions(), ec);
        exec.submit(() -> {});
    }    
    
}
