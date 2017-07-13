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
package com.intuit.karate;

import com.intuit.karate.cucumber.FeatureWrapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.Properties;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

/**
 *
 * @author pthomas3
 */
public class ScriptBridge {               
    
    private final ScriptContext context;
    
    public ScriptBridge(ScriptContext context) {
        this.context = context;       
    }

    public ScriptContext getContext() {
        return context;
    }        
    
    public void configure(String key, Object o) {
        context.configure(key, new ScriptValue(o));
    }
    
    public Object read(String fileName) {
        ScriptValue sv = FileUtils.readFile(fileName, context);
        return sv.getValue();
    }
    
    public void set(String name, Object o) {
        context.vars.put(name, o);
    }
    
    // this makes sense for xml / xpath manipulation from within js
    public void set(String name, String path, String expr) {
        Script.setValueByPath(name, path, expr, context);
    }
    
    // this makes sense for xml / xpath manipulation from within js
    public void remove(String name, String path) {
        Script.removeValueByPath(name, path, context);
    }    
    
    public Object get(String exp) {
        ScriptValue sv;
        try {
            sv = Script.eval(exp, context); // even json path expressions will work
        } catch (Exception e) {
            context.logger.warn("karate.get failed for expression: '{}': {}", exp, e.getMessage());
            return null;
        }
        if (sv != null) {
            return sv.getAfterConvertingFromJsonOrXmlIfNeeded();
        } else {
            return null;
        }
    }
    
    public Object jsonPath(Object o, String exp) {
        DocumentContext doc = JsonPath.parse(o);
        return doc.read(exp);
    }
    
    public Object call(String fileName) {
        return call(fileName, null);
    }

    public Object call(String fileName, Object arg) {
        ScriptValue sv = FileUtils.readFile(fileName, context);
        switch(sv.getType()) {
            case FEATURE_WRAPPER:
                FeatureWrapper feature = sv.getValue(FeatureWrapper.class);
                return Script.evalFeatureCall(feature, arg, context).getValue();
            case JS_FUNCTION:
                ScriptObjectMirror som = sv.getValue(ScriptObjectMirror.class);
                return Script.evalFunctionCall(som, arg, context).getValue();
            default:
                context.logger.warn("not a js function or feature file: {} - {}", fileName, sv);
                return null;
        }        
    }
    
    public String getEnv() {
        return context.env.env;
    }
    
    public Properties getProperties() {
        return System.getProperties();
    }
    
    public void log(Object ... objects) {
        context.logger.info("{}", new LogWrapper(objects));
    }        
    
    // make sure toString() is lazy
    static class LogWrapper {
        
        private final Object[] objects;
        
        LogWrapper(Object ... objects) {
            this.objects = objects;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Object o : objects) {
                sb.append(o).append(' ');                
            }
            return sb.toString();
        }
                
    }
    
}
