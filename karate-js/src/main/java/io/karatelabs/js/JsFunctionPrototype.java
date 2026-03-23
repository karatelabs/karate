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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Singleton prototype for Function instances.
 * Contains instance methods like call, apply, bind.
 * Inherits from JsObjectPrototype.
 */
class JsFunctionPrototype extends Prototype {

    static final JsFunctionPrototype INSTANCE = new JsFunctionPrototype();

    private JsFunctionPrototype() {
        super(JsObjectPrototype.INSTANCE);
    }

    @Override
    protected Object getBuiltinProperty(String name) {
        return switch (name) {
            case "call" -> (JsCallable) this::callMethod;
            case "apply" -> (JsCallable) this::applyMethod;
            case "toString" -> (JsCallable) this::toStringMethod;
            // TODO: bind
            default -> null;
        };
    }

    // Helper to get JsCallable from this context (accepts both JsFunction and JsCallable)
    private static JsCallable asCallable(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsCallable callable) {
            return callable;
        }
        return null;
    }

    private static class ThisArgs {
        final Object thisObject;
        final List<Object> args;

        @SuppressWarnings("unchecked")
        ThisArgs(Object[] args, boolean apply) {
            List<Object> list = new ArrayList<>(Arrays.asList(args));
            if (!list.isEmpty()) {
                thisObject = list.removeFirst();
            } else {
                thisObject = Terms.UNDEFINED;
            }
            if (apply) {
                if (list.isEmpty()) {
                    this.args = list;
                } else {
                    Object first = list.getFirst();
                    if (first instanceof List) {
                        this.args = ((List<Object>) first);
                    } else {
                        this.args = new ArrayList<>();
                    }
                }
            } else {
                this.args = list;
            }
        }
    }

    private Object callMethod(Context context, Object[] args) {
        JsCallable callable = asCallable(context);
        if (callable == null) {
            throw new RuntimeException("call() requires a function");
        }
        ThisArgs thisArgs = new ThisArgs(args, false);
        if (context instanceof CoreContext cc) {
            cc.thisObject = thisArgs.thisObject;
        }
        return callable.call(context, thisArgs.args.toArray(new Object[0]));
    }

    private Object applyMethod(Context context, Object[] args) {
        JsCallable callable = asCallable(context);
        if (callable == null) {
            throw new RuntimeException("apply() requires a function");
        }
        ThisArgs thisArgs = new ThisArgs(args, true);
        if (context instanceof CoreContext cc) {
            cc.thisObject = thisArgs.thisObject;
        }
        return callable.call(context, thisArgs.args.toArray(new Object[0]));
    }

    private Object toStringMethod(Context context, Object[] args) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsFunctionNode fn) {
            // User-defined function: return actual source
            return fn.node.getTextIncludingWhitespace();
        } else if (thisObj instanceof JsFunction fn) {
            // Built-in function: return [native code]
            String fnName = fn.name != null ? fn.name : "";
            return "function " + fnName + "() { [native code] }";
        }
        return "[object Function]";
    }

}
