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
    // Spec `length` per §10.2.4 — number of expected arguments. Subclasses set
    // their own arity in the ctor (Date=7, Array=1, etc.); user-defined
    // JsFunctionNode sets argCount; built-in non-constructor methods carry
    // their declared length via JsBuiltinMethod. Default 0 matches "no args".
    int length;
    private JsObject functionPrototype;

    protected JsFunction() {
        super(null, JsFunctionPrototype.INSTANCE);
    }

    @Override
    public boolean isExternal() {
        return false; // mark this as JS native while allowing Java code to cast to public JavaCallable
    }

    @Override
    public boolean isConstructable() {
        // Subclasses that aren't constructable (e.g., arrow JsFunctionNode)
        // override to return false. Built-in non-constructor callables are
        // not JsFunction at all — they're raw (JsCallable) lambdas.
        return true;
    }

    /**
     * Returns the source code of this function, or null if not available.
     * User-defined functions (JsFunctionNode) override to return their original source.
     * Built-in functions return null by default.
     */
    public String getSource() {
        return null;
    }

    @Override
    public String toString() {
        String source = getSource();
        if (source != null) {
            return source;
        }
        String fnName = name != null ? name : "";
        return "function " + fnName + "() { [native code] }";
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
    boolean isJsFunction() {
        return true;
    }

    /**
     * Spec attribute defaults for the four intrinsics every function carries.
     * Per ECMA-262 §10.2.4 / §10.2.5:
     * <ul>
     *   <li>{@code length} / {@code name}: {@code {writable: false,
     *       enumerable: false, configurable: true}}.</li>
     *   <li>{@code prototype} on a <em>user-defined</em> function:
     *       {@code {writable: true, enumerable: false, configurable: false}}
     *       — this is the default returned here, since most idiomatic JS
     *       does {@code Sub.prototype = Object.create(Super.prototype)}.
     *       Built-in constructors (Array, Date, Number, etc.) override to
     *       all-false (non-writable, non-enumerable, non-configurable).</li>
     *   <li>{@code constructor} on a function's prototype is
     *       {@code {writable: true, enumerable: false, configurable: true}}.</li>
     * </ul>
     * Returning these directly is cheaper than allocating a {@code Slot} per
     * intrinsic key on every built-in function in the JVM.
     */
    @Override
    public byte getOwnAttrs(String name) {
        return switch (name) {
            case "length", "name" -> CONFIGURABLE;
            case "prototype" -> WRITABLE;
            case "constructor" -> WRITABLE | CONFIGURABLE;
            default -> super.getOwnAttrs(name);
        };
    }

    @Override
    public boolean hasOwnIntrinsic(String name) {
        // Every function exposes prototype, name, length, and constructor as own.
        return "prototype".equals(name) || "name".equals(name)
                || "length".equals(name) || "constructor".equals(name);
    }

    @Override
    protected Object resolveOwnIntrinsic(String name) {
        // `prototype` is special: an explicit own data slot (set via
        // `Foo.prototype = X`) wins over the auto-allocated prototype object —
        // but the JsObject.getMember caller already returned that own-slot
        // value before consulting this hook. So at this point, no explicit
        // assignment exists; surface the auto-allocated prototype.
        return switch (name) {
            case "prototype" -> getFunctionPrototype();
            case "name" -> this.name;
            case "length" -> this.length;
            // `constructor` is inherited from Function.prototype, not an own
            // intrinsic — fall through to the proto chain.
            default -> null;
        };
    }

}
