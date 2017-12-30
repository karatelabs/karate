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
import com.intuit.karate.Script;
import com.intuit.karate.ScriptBindings;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptValueMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class FeatureProvider {
    
    private final FeatureWrapper feature;
    private final KarateBackend backend;
    
    public FeatureProvider(FeatureWrapper feature) {
        this(feature, null);
    }
    
    public final ScriptContext getContext() {
        return backend.getStepDefs().getContext();
    }
    
    private static void putBinding(String name, ScriptContext context) {
        String function = "function(s){ return " + ScriptBindings.KARATE  + "." + name + "(s) }";
        context.getVars().put(name, Script.evalJsExpression(function, context));
    }
    
    public FeatureProvider(FeatureWrapper feature, Map<String, Object> args) {
        this.feature = feature;
        CallContext callContext = new CallContext(null, 0, null, -1, false, false, null);
        backend = CucumberUtils.getBackendWithGlue(feature.getEnv(), callContext);
        ScriptContext context = getContext();
        putBinding(ScriptBindings.PATH_MATCHES, context);
        putBinding(ScriptBindings.METHOD_IS, context);
        putBinding(ScriptBindings.PARAM_VALUE, context);
        putBinding(ScriptBindings.TYPE_CONTAINS, context);
        putBinding(ScriptBindings.ACCEPT_CONTAINS, context);
        if (args != null) {            
            ScriptValueMap vars = backend.getVars();
            args.forEach((k, v) -> vars.put(k, v));
        } 
        CucumberUtils.call(feature, backend, CallType.BACKGROUND_ONLY);
    }        
    
    public ScriptValueMap handle(ScriptValueMap vars) {
        backend.getVars().putAll(vars);
        CucumberUtils.call(feature, backend, CallType.SCENARIO_ONLY);
        return getContext().getVars();
    }
    
}
