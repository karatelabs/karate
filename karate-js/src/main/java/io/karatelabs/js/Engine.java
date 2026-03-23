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
package io.karatelabs.js;

import io.karatelabs.common.Resource;
import io.karatelabs.parser.JsParser;
import io.karatelabs.parser.Node;
import io.karatelabs.parser.NodeType;

import java.io.File;
import java.util.Map;
import java.util.function.Consumer;

public class Engine {

    public static boolean DEBUG = false;

    private final ContextRoot root = new ContextRoot(this);

    private final Bindings bindings = new Bindings();

    public Object eval(Node program) {
        return evalInternal(program, null);
    }

    public Object eval(Resource resource) {
        return evalInternal(resource, null);
    }

    public Object eval(File file) {
        return evalInternal(Resource.from(file.toPath()), null);
    }

    public Object eval(String text) {
        return evalInternal(Resource.text(text), null);
    }

    public Object evalWith(Node program, Map<String, Object> vars) {
        return evalInternal(program, vars);
    }

    public Object evalWith(String text, Map<String, Object> vars) {
        return evalWith(Resource.text(text), vars);
    }

    public Object evalWith(Resource resource, Map<String, Object> vars) {
        return evalInternal(resource, vars);
    }

    public Object get(String name) {
        return toJava(bindings.getRaw(name));
    }

    public void put(String name, Object value) {
        bindings.putMember(name, value);
    }

    public void putRootBinding(String name, Object value) {
        root.put(name, value);
    }

    public void remove(String name) {
        bindings.remove(name);
    }

    public void setOnConsoleLog(Consumer<String> onConsoleLog) {
        root.setOnConsoleLog(onConsoleLog);
    }

    /**
     * Returns the bindings as an auto-unwrapping Map.
     * Values retrieved via get() are automatically converted (JsDate → Date, undefined → null, etc.).
     */
    public Map<String, Object> getBindings() {
        return bindings;
    }

    /**
     * Returns the raw bindings map without auto-unwrapping.
     * Use this for internal engine operations that need raw JS values.
     */
    public Map<String, Object> getRawBindings() {
        return bindings.getRawMap();
    }

    public Context getRootContext() {
        return root;
    }

    /**
     * Returns the root context bindings as an auto-unwrapping Map.
     */
    public Map<String, Object> getRootBindings() {
        if (root._bindings != null) {
            return root._bindings;
        }
        return java.util.Collections.emptyMap();
    }

    public void setListener(ContextListener listener) {
        root.listener = listener;
    }

    public void setExternalBridge(ExternalBridge bridge) {
        root.bridge = bridge;
    }

    public <T> void setDebugSupport(RunInterceptor<T> interceptor, DebugPointFactory<T> factory) {
        root.interceptor = interceptor;
        root.pointFactory = factory;
    }

    public RunInterceptor<?> getInterceptor() {
        return root.interceptor;
    }

    public DebugPointFactory<?> getPointFactory() {
        return root.pointFactory;
    }

    /**
     * Converts a JS value to its Java equivalent.
     * - JsValue types (including JsUndefined) are unwrapped via getJavaValue()
     * - JsFunction is wrapped to auto-convert return values
     */
    static Object toJava(Object value) {
        if (value instanceof JsValue jv) {
            return jv.getJavaValue();
        }
        if (value instanceof JsFunction fn) {
            return new JsFunctionWrapper(fn);
        }
        return value;
    }

    // For testing: returns raw JS result without toJava() conversion
    protected Object evalRaw(String text) {
        JsParser parser = new JsParser(Resource.text(text));
        Node program = parser.parse();
        CoreContext context = new CoreContext(root, root, 0, program, ContextScope.GLOBAL, bindings);
        return Interpreter.eval(program, context);
    }

    private Object evalInternal(Resource resource, Map<String, Object> localVars) {
        JsParser parser = new JsParser(resource);
        return evalInternal(parser.parse(), localVars);
    }

    private Object evalInternal(Node program, Map<String, Object> localVars) {
        try {
            root.evalId++;
            CoreContext context;
            if (localVars == null) {
                context = new CoreContext(root, root, 0, program, ContextScope.GLOBAL, bindings);
            } else {
                CoreContext parent = new CoreContext(root, null, -1, new Node(NodeType.ROOT), ContextScope.GLOBAL, bindings);
                context = new CoreContext(root, parent, 0, program, ContextScope.GLOBAL, new Bindings(localVars));
            }
            context.event(EventType.CONTEXT_ENTER, program);
            Object result = Interpreter.eval(program, context);
            context.event(EventType.CONTEXT_EXIT, program);
            return toJava(result);
        } catch (Throwable e) {
            String message = e.getMessage();
            if (message == null) {
                message = e + "";
            }
            Resource resource = program.getFirstToken().getResource();
            if (resource.isFile()) {
                message = message + "\n" + resource.getRelativePath();
            }
            throw new RuntimeException(message);
        }
    }

}
