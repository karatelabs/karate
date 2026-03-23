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

/**
 * Base class for JavaScript function objects.
 * <p>
 * Implements JavaCallable so JS functions can be passed to Java code
 * that expects a callable (e.g., predicate functions, callbacks).
 */
public abstract class JsFunction extends JsObject implements JavaCallable {

    String name;
    private JsObject functionPrototype;

    protected JsFunction() {
        super(null, JsFunctionPrototype.INSTANCE);
    }

    @Override
    public boolean isExternal() {
        return false; // mark this as JS native while allowing Java code to cast to public JavaCallable
    }

    /**
     * Returns the source code of this function, or null if not available.
     * User-defined functions (JsFunctionNode) override to return their original source.
     * Built-in functions return null by default.
     */
    public String getSource() {
        return null;
    }

    /**
     * Returns the function's prototype object (used for instance creation).
     * This is the object that will be set as [[Prototype]] of instances created with 'new'.
     * Lazily created on first access.
     */
    JsObject getFunctionPrototype() {
        if (functionPrototype == null) {
            functionPrototype = new JsObject();
            // ES6: prototype.constructor points back to the function
            functionPrototype.putMember("constructor", this);
        }
        return functionPrototype;
    }

    @Override
    public Object getMember(String name) {
        // For functions, "prototype" returns the function's prototype object
        // Check _map first to allow "Foo.prototype = ..." assignments
        if ("prototype".equals(name)) {
            Object fromSuper = super.getMember(name);
            // If explicitly set in _map (not inherited from prototype chain), use that value
            // Note: Can't use `!(fromSuper instanceof JsCallable)` because JsObject implements JsCallable
            if (fromSuper != null && !(fromSuper instanceof JsFunction)) {
                // Prototype was explicitly set via assignment
                return fromSuper;
            }
            return getFunctionPrototype();
        }
        // Special case: name property
        if ("name".equals(name)) {
            return this.name;
        }
        // Special case: constructor property
        if ("constructor".equals(name)) {
            return this;
        }
        return super.getMember(name);
    }

}
