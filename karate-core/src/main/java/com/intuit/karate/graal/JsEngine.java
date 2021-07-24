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
import com.intuit.karate.KarateException;
import com.intuit.karate.StringUtils;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
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
    private static final String JS_ECMASCRIPT_VERSION = "js.ecmascript-version";
    private static final String ENGINE_WARN_INTERPRETER_ONLY = "engine.WarnInterpreterOnly";
    private static final String V_2021 = "2021";
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    private static final ThreadLocal<JsEngine> GLOBAL_JS_ENGINE = new ThreadLocal<JsEngine>() {
        @Override
        protected JsEngine initialValue() {
            return new JsEngine(createContext(null));
        }
    };

    private static Context createContext(Engine engine) {
        if (engine == null) {
            engine = Engine.newBuilder()
                    .option(ENGINE_WARN_INTERPRETER_ONLY, FALSE)                    
                    .build();
        }
        return Context.newBuilder(JS)
                .allowExperimentalOptions(true)
                .allowAllAccess(true)
                .option(JS_NASHORN_COMPAT, TRUE)
                .option(JS_ECMASCRIPT_VERSION, V_2021)
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

    //==========================================================================
    //
    public final Context context;
    public final Value bindings;

    private JsEngine(Context context) {
        this.context = context;
        bindings = context.getBindings(JS);
    }

    public JsEngine copy() {
        JsEngine temp = local();
        for (String key : bindings.getMemberKeys()) {
            Value v = bindings.getMember(key);
            if (v.isHostObject()) {
                temp.bindings.putMember(key, v);
            } else if (v.canExecute()) {
                Value fun = temp.evalForValue("(" + v.getSourceLocation().getCharacters() + ")");
                temp.bindings.putMember(key, fun);
            } else {
                temp.bindings.putMember(key, JsValue.toJava(v));
            }
        }
        return temp;
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

    public void remove(String key) {
        bindings.removeMember(key);
    }

    public void putAll(Map<String, Object> map) {
        map.forEach((k, v) -> put(k, v));
    }

    public JsValue get(String key) {
        Value value = bindings.getMember(key);
        return new JsValue(value);
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

    public static Value execute(Value function, Object... args) {
        for (int i = 0; i < args.length; i++) {
            args[i] = JsValue.fromJava(args[i]);
        }
        return function.execute(args);
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

    @Override
    public String toString() {
        return context.toString();
    }

}
