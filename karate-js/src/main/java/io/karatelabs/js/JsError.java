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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JavaScript Error wrapper that provides Error prototype methods.
 */
class JsError extends JsObject {

    private final String message;
    private final String name;
    private final Throwable cause;
    // ref to the global constructor this instance was produced from
    // (Error, TypeError, RangeError, ...) — null for the global instances themselves
    private JsError constructor;

    public JsError(String message) {
        this(message, "Error", null);
    }

    public JsError(String message, Throwable cause) {
        this(message, "Error", cause);
    }

    public JsError(String message, String name, Throwable cause) {
        super(null, JsObjectPrototype.INSTANCE);
        this.message = message;
        this.name = name;
        this.cause = cause;
    }

    public String getName() {
        return name;
    }

    String getMessageString() {
        return message;
    }

    JsError getConstructor() {
        return constructor;
    }

    /**
     * Wire the {@code .constructor} property so JS-side identity checks
     * ({@code e.constructor === TypeError}, {@code thrown.constructor.name})
     * match the registered global. Called at the JS-catch boundary for errors
     * that originate outside {@link #call} (e.g., engine-thrown
     * {@link JsErrorException}).
     */
    void setConstructor(JsError constructor) {
        this.constructor = constructor;
    }

    public Throwable getCause() {
        return cause;
    }

    @Override
    boolean isJsFunction() {
        // Registered globals (Error, TypeError, RangeError, ...) have
        // constructor == null; thrown instances point their .constructor at
        // the registered global. Only the former should report "function".
        return constructor == null;
    }

    @Override
    public boolean isConstructable() {
        // Same rule as isJsFunction(): only the registered global instances
        // are constructors; thrown instances are plain error objects.
        return constructor == null;
    }

    @Override
    public Object getMember(String key) {
        // Check own properties first (super walks _map then __proto__ chain)
        Object own = super.getMember(key);
        if (own != null) {
            // Spec: Error.prototype.toString shadows Object.prototype.toString.
            // Now matters because Terms.toPrimitive (and binary + via Terms.add) routes
            // through getMember("toString"); previously masked by Java string concat.
            if ("toString".equals(key) && own == JsObjectPrototype.DEFAULT_TO_STRING) {
                return (JsCallable) (ctx, args) -> toString();
            }
            return own;
        }
        return switch (key) {
            case "message" -> message;
            case "name" -> name;
            case "constructor" -> constructor;
            default -> null;
        };
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("message", message);
        // include any user-set own properties last
        Map<String, Object> own = super.toMap();
        if (own != null) {
            result.putAll(own);
        }
        return result;
    }

    @Override
    public String toString() {
        // matches JS: '' + new Error('foo') === 'Error: foo'
        if (message == null || message.isEmpty()) {
            return name;
        }
        // Avoid doubling the name prefix when the message already carries it.
        // Common when the engine has injected the "<Name>: " prefix at
        // Interpreter.evalProgram, or when sta.js's Test262Error.prototype.toString
        // has already been called on the receiver.
        String prefix = name + ": ";
        if (message.startsWith(prefix)) {
            return message;
        }
        return prefix + message;
    }

    @Override
    public Object call(Context context, Object[] args) {
        // ES6: Error('foo') and new Error('foo') both return an Error instance.
        // Preserve the constructor's name so subclasses (TypeError, etc.) carry it through.
        String msg = (args.length > 0 && args[0] != null && args[0] != Terms.UNDEFINED) ? args[0] + "" : null;
        JsError instance = new JsError(msg, this.name, null);
        instance.constructor = this;
        return instance;
    }

}
