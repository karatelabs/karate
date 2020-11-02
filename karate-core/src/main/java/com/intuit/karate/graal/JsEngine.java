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
import java.util.Map;
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
    private static final String JSON_STRINGIFY = "JSON.stringify";
    private static final String JS_EXPERIMENTAL_FOP = "js.experimental-foreign-object-prototype";
    private static final String JS_NASHORN_COMPAT = "js.nashorn-compat";
    private static final String TRUE = "true";

    private static class JsContext {

        final Context context;
        final Value bindings;

        JsContext(Engine engine) {
            if (engine == null) {
                engine = Engine.newBuilder().build();
            }
            context = Context.newBuilder(JS)
                    .allowExperimentalOptions(true)
                    .allowAllAccess(true)
                    .option(JS_NASHORN_COMPAT, TRUE)
                    .option(JS_EXPERIMENTAL_FOP, TRUE)
                    .engine(engine).build();
            bindings = context.getBindings(JS);
        }

        Value eval(String exp) {
            return context.eval(JS, exp);
        }

    }

    public static JsValue evalGlobal(String src) {
        return global().eval(src);
    }

    public static JsValue evalGlobal(InputStream is) {
        return global().eval(is);
    }

    public static JsEngine global() {
        return new JsEngine(GLOBAL_JS_CONTEXT.get());
    }

    public static void remove() {
        GLOBAL_JS_CONTEXT.remove();
    }

    public static JsEngine local() {
        Engine engine = GLOBAL_JS_CONTEXT.get().context.getEngine();
        return new JsEngine(new JsContext(engine));
    }

    public static JsEngine localWithGlobalBindings() {
        JsEngine je = local();
        Value bindings = global().jc.bindings;
        for (String key : bindings.getMemberKeys()) {
            je.putValue(key, bindings.getMember(key));
        }
        return je;
    }

    private static final ThreadLocal<JsContext> GLOBAL_JS_CONTEXT = new ThreadLocal<JsContext>() {
        @Override
        protected JsContext initialValue() {
            return new JsContext(null);
        }
    };

    private final JsContext jc;
    private Value stringify;

    private JsEngine(JsContext sc) {
        this.jc = sc;
    }

    public Value bindings() {
        return jc.bindings;
    }

    public JsValue eval(InputStream is) {
        return eval(FileUtils.toString(is));
    }

    public JsValue eval(File file) {
        return eval(FileUtils.toString(file));
    }

    public JsValue eval(String exp) {
        return new JsValue(jc.eval(exp));
    }

    public Value evalForValue(String exp) {
        return jc.eval(exp);
    }

    public void put(String key, Object value) {
        jc.bindings.putMember(key, JsValue.fromJava(value));
    }

    public void putAll(Map<String, Object> map) {
        map.forEach((k, v) -> put(k, v));
    }

    public boolean hasMember(String key) {
        return jc.bindings.hasMember(key);
    }

    public JsValue get(String key) {
        Value value = jc.bindings.getMember(key);
        return new JsValue(value);
    }

    public String toJson(Value v) {
        if (stringify == null) {
            stringify = evalForValue(JSON_STRINGIFY);
        }
        Value json = stringify.execute(v);
        return json.asString();
    }

    public String toJson(JsValue jv) {
        return toJson(jv.getOriginal());
    }

    public void putValue(String key, Value v) {
        if (v.isHostObject()) {
            jc.bindings.putMember(key, v);
        } else if (v.canExecute()) {
            Value fun = evalForValue("(" + v.toString() + ")");
            jc.bindings.putMember(key, fun);
        } else {
            put(key, JsValue.toJava(v));
        }
    }

    public Value attachToContext(Object o) {
        try {
            Value old = Value.asValue(o);
            Context context = old.getContext();
            if (context != null && !context.equals(jc.context)) {
                String temp = "(" + old.toString() + ")";
                return evalForValue(temp);
            } else {
                return old;
            }
        } catch (Exception e) {
            logger.warn("*** js attach failed: {}", e.getMessage());
            String temp = "(" + o.toString() + ")";
            return evalForValue(temp);
        }
    }

    public String toString() {
        return jc.context.toString();
    }

}
