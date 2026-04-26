/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
 * The global {@code Function} constructor.
 * <p>
 * {@code new Function('a', 'b', 'return a+b')} compiles a function from
 * source strings — params are all-but-last; the body is the last argument.
 * Implementation defers to {@link Engine#evalRaw} after wrapping the args
 * as a {@code (function anonymous(...) { ... })} expression.
 * <p>
 * {@code Function.prototype} returns the {@link JsFunctionPrototype#INSTANCE}
 * singleton — the same object that {@code Object.getPrototypeOf(someFn)}
 * resolves to for any user or built-in function, satisfying spec identity.
 */
class JsFunctionConstructor extends JsFunction {

    static final JsFunctionConstructor INSTANCE = new JsFunctionConstructor();

    private JsFunctionConstructor() {
        this.name = "Function";
        this.length = 1;
        registerForEngineReset();
    }

    @Override
    public Object getMember(String name) {
        return switch (name) {
            case "prototype" -> JsFunctionPrototype.INSTANCE;
            default -> super.getMember(name);
        };
    }

    @Override
    public boolean hasOwnIntrinsic(String name) {
        // Function.prototype / .length / .name are all own intrinsics
        return super.hasOwnIntrinsic(name);
    }

    @Override
    public Object call(Context context, Object[] args) {
        StringBuilder src = new StringBuilder("(function anonymous(");
        if (args.length == 0) {
            src.append(") {\n})");
        } else {
            for (int i = 0; i < args.length - 1; i++) {
                if (i > 0) src.append(',');
                src.append(argToString(args[i]));
            }
            src.append("\n) {\n");
            src.append(argToString(args[args.length - 1]));
            src.append("\n})");
        }
        Engine engine = context.getEngine();
        if (engine == null) {
            throw JsErrorException.typeError("Function constructor unavailable: no engine");
        }
        return engine.evalRaw(src.toString());
    }

    private static String argToString(Object arg) {
        if (arg == null || arg == Terms.UNDEFINED) return "";
        return arg.toString();
    }

}
