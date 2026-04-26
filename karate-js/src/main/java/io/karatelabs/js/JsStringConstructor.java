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
 * JavaScript String constructor function.
 * <p>
 * Static methods are wrapped in {@link JsBuiltinMethod} (per the JsMath /
 * JsNumberConstructor template) so they expose spec {@code length} and
 * {@code name}; method instances are cached per-Engine in {@code methodCache}.
 * {@link #hasOwnIntrinsic} / {@link #getOwnAttrs} declare each method plus
 * the {@code prototype} slot per spec.
 */
class JsStringConstructor extends JsFunction {

    static final JsStringConstructor INSTANCE = new JsStringConstructor();

    private java.util.Map<String, JsBuiltinMethod> methodCache;

    private JsStringConstructor() {
        this.name = "String";
        this.length = 1;
        registerForEngineReset();
    }

    @Override
    public Object getMember(String name) {
        if (isTombstoned(name) || ownContainsKey(name)) {
            return super.getMember(name);
        }
        if (methodCache != null) {
            JsBuiltinMethod cached = methodCache.get(name);
            if (cached != null) return cached;
        }
        Object result = resolveMember(name);
        if (result instanceof JsBuiltinMethod jbm) {
            if (methodCache == null) {
                methodCache = new java.util.HashMap<>();
            }
            methodCache.put(name, jbm);
        }
        return result;
    }

    private Object resolveMember(String name) {
        return switch (name) {
            case "fromCharCode" -> method(name, 1, (JsInvokable) this::fromCharCode);
            case "fromCodePoint" -> method(name, 1, (JsInvokable) this::fromCodePoint);
            case "prototype" -> JsStringPrototype.INSTANCE;
            default -> super.getMember(name);
        };
    }

    @Override
    public boolean hasOwnIntrinsic(String name) {
        return isStringMethod(name) || super.hasOwnIntrinsic(name);
    }

    @Override
    public byte getOwnAttrs(String name) {
        if (isStringMethod(name)) {
            return WRITABLE | CONFIGURABLE;
        }
        if ("prototype".equals(name)) {
            return 0;
        }
        return super.getOwnAttrs(name);
    }

    @Override
    protected void clearEngineState() {
        super.clearEngineState();
        if (methodCache != null) methodCache.clear();
    }

    private static boolean isStringMethod(String n) {
        return switch (n) {
            case "fromCharCode", "fromCodePoint" -> true;
            default -> false;
        };
    }

    @Override
    public Object call(Context context, Object[] args) {
        return JsString.getObject(context, args);
    }

    // Static methods

    private Object fromCharCode(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg instanceof Number num) {
                sb.append((char) num.intValue());
            }
        }
        return sb.toString();
    }

    private Object fromCodePoint(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg instanceof Number num) {
                int n = num.intValue();
                if (n < 0 || n > 0x10FFFF) {
                    throw JsErrorException.rangeError("Invalid code point " + num);
                }
                sb.appendCodePoint(n);
            }
        }
        return sb.toString();
    }

}
