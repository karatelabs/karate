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

    private static final String[] KNOWN_NAMES = {
            "TypeError", "ReferenceError", "RangeError", "SyntaxError",
            "URIError", "EvalError", "Error"
    };

    /**
     * Parse a leading "<ErrorName>: " prefix off a raw engine message.
     *
     * @return {@code [name, stripped-message]} when a recognized prefix is present,
     *         or {@code null} otherwise
     */
    static String[] parsePrefix(String msg) {
        if (msg == null) return null;
        for (String name : KNOWN_NAMES) {
            String prefix = name + ": ";
            if (msg.startsWith(prefix)) {
                return new String[]{name, msg.substring(prefix.length())};
            }
        }
        return null;
    }

    /**
     * Build a {@link JsError} from a Java exception's message, recognizing a
     * "<ErrorName>: " prefix to assign the proper JS error name.
     */
    static JsError fromJavaCause(String msg, Throwable cause) {
        String[] parsed = parsePrefix(msg);
        if (parsed != null) {
            return new JsError(parsed[1], parsed[0], cause);
        }
        return new JsError(msg, cause);
    }

    public static JsError typeError(String message) {
        return new JsError(message, "TypeError", null);
    }

    public static JsError referenceError(String message) {
        return new JsError(message, "ReferenceError", null);
    }

    public static JsError rangeError(String message) {
        return new JsError(message, "RangeError", null);
    }

    public static JsError syntaxError(String message) {
        return new JsError(message, "SyntaxError", null);
    }

    public String getName() {
        return name;
    }

    /**
     * Set the {@code .constructor} property so that JS-side identity checks
     * ({@code e.constructor === TypeError}, {@code thrown.constructor.name}) match
     * the registered global. Used when the engine constructs a JsError directly
     * (e.g., from a Java-level RuntimeException caught by a JS try/catch) rather
     * than via {@link #call}.
     */
    void setConstructor(JsError constructor) {
        this.constructor = constructor;
    }

    public Throwable getCause() {
        return cause;
    }

    @Override
    public Object getMember(String key) {
        // Check own properties first
        Object own = super.getMember(key);
        if (own != null) {
            return own;
        }
        return switch (key) {
            case "message" -> message;
            case "name" -> name;
            case "constructor" -> constructor;
            case "toString" -> (JsCallable) (ctx, args) -> toString();
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
        return name + ": " + message;
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
