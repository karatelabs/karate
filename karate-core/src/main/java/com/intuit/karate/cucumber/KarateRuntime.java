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

import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.io.ResourceLoader;
import gherkin.I18n;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Step;
import java.util.Collections;

/**
 *
 * @author pthomas3
 */
public class KarateRuntime extends Runtime {  
    
    private final KarateBackend backend;
    private boolean hasError;
    
    public KarateRuntime(ResourceLoader resourceLoader, ClassLoader classLoader, KarateBackend backend,
                   RuntimeOptions runtimeOptions, RuntimeGlue glue) { 
        super(resourceLoader, classLoader, Collections.singletonList(backend), runtimeOptions, glue);
        this.backend = backend;
    }

    @Override
    public void runStep(String featurePath, Step step, Reporter reporter, I18n i18n) {
        if (hasError) { // specifc to JUnit or TestNG execution - stop on first error
            return;
        }
        StepResult result = CucumberUtils.runStep(featurePath, step, reporter, i18n, backend, false);
        if (!result.isPass()) {
            hasError = true;            
        }
    }        
    
}
