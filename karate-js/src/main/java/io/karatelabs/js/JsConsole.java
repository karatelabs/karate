/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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

class JsConsole implements SimpleObject {

    final ContextRoot root;
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final Map<String, Long> timers = new LinkedHashMap<>();

    JsConsole(ContextRoot root) {
        this.root = root;
    }

    private String argsToString(Context context, Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(Terms.toStringCoerce(args[i], (CoreContext) context));
        }
        return sb.toString();
    }

    private void output(String message) {
        if (root.onConsoleLog != null) {
            root.onConsoleLog.accept(message);
        } else {
            System.out.println(message);
        }
    }

    @Override
    public Object jsGet(String name) {
        return switch (name) {
            case "log", "info", "debug", "warn", "error" -> (JsCallable) (context, args) -> {
                output(argsToString(context, args));
                return null;
            };
            case "trace" -> (JsCallable) (context, args) -> {
                String message = args.length > 0 ? argsToString(context, args) : "Trace";
                output(message);
                return null;
            };
            case "dir", "dirxml" -> (JsCallable) (context, args) -> {
                if (args.length > 0) {
                    output(Terms.toStringCoerce(args[0], (CoreContext) context));
                }
                return null;
            };
            case "table" -> (JsCallable) (context, args) -> {
                if (args.length > 0) {
                    output(Terms.toStringCoerce(args[0], (CoreContext) context));
                }
                return null;
            };
            case "assert" -> (JsCallable) (context, args) -> {
                if (args.length == 0 || !Terms.isTruthy(args[0])) {
                    String message = "Assertion failed";
                    if (args.length > 1) {
                        Object[] rest = new Object[args.length - 1];
                        System.arraycopy(args, 1, rest, 0, rest.length);
                        message = message + ": " + argsToString(context, rest);
                    }
                    output(message);
                }
                return null;
            };
            case "count" -> (JsCallable) (context, args) -> {
                String label = args.length > 0 ? Terms.toStringCoerce(args[0], (CoreContext) context) : "default";
                int count = counts.merge(label, 1, Integer::sum);
                output(label + ": " + count);
                return null;
            };
            case "countReset" -> (JsCallable) (context, args) -> {
                String label = args.length > 0 ? Terms.toStringCoerce(args[0], (CoreContext) context) : "default";
                counts.remove(label);
                return null;
            };
            case "time" -> (JsCallable) (context, args) -> {
                String label = args.length > 0 ? Terms.toStringCoerce(args[0], (CoreContext) context) : "default";
                timers.put(label, System.currentTimeMillis());
                return null;
            };
            case "timeLog" -> (JsCallable) (context, args) -> {
                String label = args.length > 0 ? Terms.toStringCoerce(args[0], (CoreContext) context) : "default";
                Long start = timers.get(label);
                if (start != null) {
                    long elapsed = System.currentTimeMillis() - start;
                    output(label + ": " + elapsed + "ms");
                }
                return null;
            };
            case "timeEnd" -> (JsCallable) (context, args) -> {
                String label = args.length > 0 ? Terms.toStringCoerce(args[0], (CoreContext) context) : "default";
                Long start = timers.remove(label);
                if (start != null) {
                    long elapsed = System.currentTimeMillis() - start;
                    output(label + ": " + elapsed + "ms");
                }
                return null;
            };
            case "group", "groupCollapsed" -> (JsCallable) (context, args) -> {
                if (args.length > 0) {
                    output(argsToString(context, args));
                }
                return null;
            };
            case "groupEnd", "clear" -> (JsCallable) (context, args) -> null;
            default -> null;
        };
    }

}
