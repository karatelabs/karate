/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.graal;

import com.intuit.karate.FileUtils;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class JsEngine {

    private static final Logger logger = LoggerFactory.getLogger(JsEngine.class);

    private static final String JS = "js";
    private static final String JS_EXPERIMENTAL_FOP = "js.experimental-foreign-object-prototype";
    private static final String JS_NASHORN_COMPAT = "js.nashorn-compat";
    private static final String TRUE = "true";

    private static final ThreadLocal<JsEngine> GLOBAL_JS_ENGINE = new ThreadLocal<JsEngine>() {
        @Override
        protected JsEngine initialValue() {
            return new JsEngine(createContext(null));
        }
    };

    private static Context createContext(Engine engine) {
        if (engine == null) {
            engine = Engine.newBuilder().build();
        }
        return Context.newBuilder(JS)
                .allowExperimentalOptions(true)
                .allowAllAccess(true)
                .option(JS_NASHORN_COMPAT, TRUE)
                .option(JS_EXPERIMENTAL_FOP, TRUE)
                .engine(engine).build();
    }

    public static JsValue evalGlobal(String src) {
        return global().eval(src);
    }

    public static JsValue evalGlobal(InputStream is) {
        return global().eval(is);
    }

    public static JsEngine global() {
        return GLOBAL_JS_ENGINE.get();
    }

    public static void remove() {
        GLOBAL_JS_ENGINE.remove();
    }

    public static JsEngine local() {
        Engine engine = GLOBAL_JS_ENGINE.get().context.getEngine();
        return new JsEngine(createContext(engine));
    }

    public static JsEngine local(JsEngine parent) {
        JsEngine je = local();
        Value bindings = parent.bindings;
        for (String key : bindings.getMemberKeys()) {
            je.putValue(key, bindings.getMember(key));
        }
        return je;
    }

    //==========================================================================
    //
    public final Context context;
    public final Value bindings;

    private JsEngine(Context context) {
        this.context = context;
        bindings = context.getBindings(JS);
    }

    public JsValue eval(InputStream is) {
        return eval(FileUtils.toString(is));
    }

    public JsValue eval(File file) {
        return eval(FileUtils.toString(file));
    }

    public JsValue eval(String exp) {
        return new JsValue(evalForValue(exp));
    }

    public Value evalForValue(String exp) {
        return context.eval(JS, exp);
    }

    public void put(String key, Object value) {
        bindings.putMember(key, JsValue.fromJava(value));
    }

    public void putAll(Map<String, Object> map) {
        map.forEach((k, v) -> put(k, v));
    }

    public JsValue get(String key) {
        Value value = bindings.getMember(key);
        return new JsValue(value);
    }

    public void putValue(String key, Value v) {
        if (v.isHostObject()) {
            bindings.putMember(key, v);
        } else if (v.canExecute()) {
            Value fun = evalForValue("(" + v.getSourceLocation().getCharacters() + ")");
            bindings.putMember(key, fun);
        } else {
            put(key, JsValue.toJava(v));
        }
    }

    public Value attachSource(CharSequence source) {
        Value value = evalForValue("(" + source + ")");
        return attach(value);
    }

    public Value attach(Value function) {
        try {
            return context.asValue(function);
        } catch (Exception e) {
            logger.trace("context switch: {}", e.getMessage());
            CharSequence source = function.getSourceLocation().getCharacters();
            return evalForValue("(" + source + ")");
        }
    }

    public JsValue execute(Value function, Object... args) {
        for (int i = 0; i < args.length; i++) {
            args[i] = JsValue.fromJava(args[i]);
        }
        Value result = function.execute(args);
        return new JsValue(result);
    }

    public Value evalWith(Value value, String src, boolean returnValue) {
        return evalWith(value.getMemberKeys(), value::getMember, src, returnValue);
    }

    public Value evalWith(Map<String, Object> variables, String src, boolean returnValue) {
        return evalWith(variables.keySet(), variables::get, src, returnValue);
    }

    public Value evalWith(Set<String> names, Function<String, Object> getVariable, String src, boolean returnValue) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function(x){ ");
        Map<String, Object> arg = new HashMap(names.size());
        for (String name : names) {
            sb.append("let ").append(name).append(" = x.").append(name).append("; ");
            arg.put(name, getVariable.apply(name));
        }
        if (returnValue) {
            sb.append("return ");
        }
        sb.append(src).append(" })");
        Value function = evalForValue(sb.toString());
        return function.execute(JsValue.fromJava(arg));
    }

    @Override
    public String toString() {
        return context.toString();
    }

}
