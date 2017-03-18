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

import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.StepDefs;
import cucumber.api.java.ObjectFactory;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class KarateObjectFactory implements ObjectFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(KarateObjectFactory.class);
       
    private StepDefs stepDefs;
    private final ScriptEnv scriptEnv;
    private final ScriptContext parentContext;
    private final Map<String, Object> callArg;
    
    public KarateObjectFactory(ScriptEnv scriptEnv, ScriptContext parentContext, Map<String, Object> callArg) {
        this.scriptEnv = scriptEnv;
        this.parentContext = parentContext;
        this.callArg = callArg;
    }

    @Override
    public void start() {
        logger.trace("start");
    }

    @Override
    public void stop() {
        logger.trace("stop");
        stepDefs = null; // ensure re-build for multiple scenarios in the same feature
    }

    @Override
    public boolean addClass(Class<?> glueClass) {
        if (logger.isTraceEnabled()) {
            logger.trace("add class: {}", glueClass);
        }
        return true;
    }

    @Override
    public <T> T getInstance(Class<T> glueClass) {
        if (stepDefs == null) {
            // the lazy init gives users the chance to over-ride the env
            // for example using a JUnit @BeforeClass hook
            logger.trace("lazy init of step defs");
            stepDefs = new StepDefs(scriptEnv, parentContext, callArg);
        } else {
            logger.trace("step defs already instantiated, re-using instance");
        }
        return (T) stepDefs;
    }

    public StepDefs getStepDefs() {
        return stepDefs;
    }        
    
}
