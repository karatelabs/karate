/*
 * The MIT License
 *
 * Copyright 2024 Karate Labs Inc.
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
package com.intuit.karate.js;

import com.intuit.karate.FileUtils;
import com.intuit.karate.KarateException;
import com.intuit.karate.StringUtils;
import io.karatelabs.js.Engine;
import io.karatelabs.js.Invokable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class JsEngine {

    public final Engine engine;

    private JsEngine(Engine engine) {
        this.engine = engine;
    }

    public JsEngine() {
        this(new Engine());
    }

    private static final ThreadLocal<JsEngine> GLOBAL_JS_ENGINE = new ThreadLocal<JsEngine>() {
        @Override
        protected JsEngine initialValue() {
            return new JsEngine();
        }
    };

    public static Object evalGlobal(InputStream is) {
        String js = FileUtils.toString(is);
        return global().eval(js);
    }

    public static Object evalGlobal(String js) {
        return global().eval(js);
    }

    public static JsEngine global() {
        return GLOBAL_JS_ENGINE.get();
    }

    public static void remove() {
        GLOBAL_JS_ENGINE.remove();
    }

    //==================================================================================================================
    //
    public Object eval(String js) {
        Object result = engine.eval(js);
        return Engine.isUndefined(result) ? null : result;
    }

    public Object evalRaw(String js) {
        Object result = engine.eval(js);
        if (Engine.isUndefined(result)) {
            throw new RuntimeException("result is " + result);
        }
        return result;
    }

    public Object eval(InputStream is) {
        return eval(FileUtils.toString(is));
    }

    public void put(String name, Object value) {
        engine.context.declare(name, value);
    }

    public boolean has(String name) {
        return engine.context.hasKey(name);
    }

    public void remove(String name) {
        engine.context.remove(name);
    }

    public Object get(String name) {
        return engine.context.get(name);
    }

    public JsEngine copy() {
        return new JsEngine(engine.copy());
    }

    public Object evalWith(Map<String, Object> variables, String src, boolean returnValue) {
        return evalWith(variables.keySet(), variables::get, src, returnValue);
    }

    public static Object invoke(Invokable invokable, Object... args) {
        Object result = invokable.invoke(args);
        return Engine.isUndefined(result) ? null : result;
    }

    public Object evalWith(Set<String> names, Function<String, Object> getVariable, String src, boolean returnValue) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function($){ ");
        Map<String, Object> arg = new HashMap<>(names.size());
        for (String name : names) {
            sb.append("let ").append(name).append(" = $.").append(name).append("; ");
            arg.put(name, getVariable.apply(name));
        }
        if (returnValue) {
            sb.append("return ");
        }
        sb.append(src).append(" })");
        Invokable function = (Invokable) eval(sb.toString());
        return invoke(function, arg);
    }

    public static KarateException fromJsEvalException(String js, Exception e, String message) {
        // do our best to make js error traces informative, else thrown exception seems to
        // get swallowed by the java reflection based method invoke flow
        StackTraceElement[] stack = e.getStackTrace();
        StringBuilder sb = new StringBuilder();
        if (message != null) {
            sb.append(message).append('\n');
        }
        sb.append("js failed:\n>>>>\n");
        List<String> lines = StringUtils.toStringLines(js);
        int index = 0;
        for (String line : lines) {
            sb.append(String.format("%02d", ++index)).append(": ").append(line).append('\n');
        }
        sb.append("<<<<\n");
        sb.append(e.toString()).append('\n');
        for (int i = 0; i < stack.length; i++) {
            String line = stack[i].toString();
            sb.append("- ").append(line).append('\n');
            if (line.startsWith("<js>") || i > 5) {
                break;
            }
        }
        return new KarateException(sb.toString());
    }

}
