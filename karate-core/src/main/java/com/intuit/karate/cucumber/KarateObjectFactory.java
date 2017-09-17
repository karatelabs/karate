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

import com.intuit.karate.CallContext;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.StepDefs;
import cucumber.api.java.ObjectFactory;

/**
 *
 * @author pthomas3
 */
public class KarateObjectFactory implements ObjectFactory {    
       
    private StepDefs stepDefs;
    private ScriptEnv scriptEnv;
    private final CallContext callContext;
    
    public KarateObjectFactory(ScriptEnv scriptEnv, CallContext callContext) {
        this.scriptEnv = scriptEnv;
        this.callContext = callContext;
    }
    
    public StepDefs reset(String envString) {
        scriptEnv = scriptEnv.refresh(envString);
        stop(); // clear step defs
        return getInstance(null);
    }

    public ScriptEnv getEnv() {
        if (stepDefs != null) { // get the latest, just in case it was clobbered
            return stepDefs.getContext().getEnv();
        } else {
            return scriptEnv;
        }
    }
    
    @Override
    public void start() {
        
    }

    @Override
    public void stop() {
        stepDefs = null; // ensure re-build for multiple scenarios in the same feature
    }

    @Override
    public boolean addClass(Class<?> glueClass) {
        return true;
    }

    @Override
    public <T> T getInstance(Class<T> glueClass) {
        if (stepDefs == null) {
            // the lazy init gives users the chance to over-ride the env
            // for example using a JUnit @BeforeClass hook
            stepDefs = new StepDefs(scriptEnv, callContext);
        }
        return (T) stepDefs;
    }

    public StepDefs getStepDefs() {
        return stepDefs;
    }        
    
}
