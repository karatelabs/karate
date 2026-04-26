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

import java.util.Date;

/**
 * JavaScript Date constructor function.
 * Provides static methods like Date.now, Date.parse, Date.UTC.
 */
class JsDateConstructor extends JsFunction {

    static final JsDateConstructor INSTANCE = new JsDateConstructor();

    private JsDateConstructor() {
        this.name = "Date";
        this.length = 7;
        registerForEngineReset();
    }

    private java.util.Map<String, JsBuiltinMethod> methodCache;

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
            case "now" -> method(name, 0, (JsInvokable) this::now);
            case "parse" -> method(name, 1, (ctx, args) -> parse(args));
            case "UTC" -> method(name, 7, (ctx, args) ->
                    utcWithContext(ctx instanceof CoreContext c ? c : null, args));
            case "prototype" -> JsDatePrototype.INSTANCE;
            default -> super.getMember(name);
        };
    }

    @Override
    public boolean hasOwnIntrinsic(String name) {
        return isDateMethod(name) || super.hasOwnIntrinsic(name);
    }

    @Override
    public byte getOwnAttrs(String name) {
        if (isDateMethod(name)) {
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

    private static boolean isDateMethod(String n) {
        return switch (n) {
            case "now", "parse", "UTC" -> true;
            default -> false;
        };
    }

    @Override
    public Object call(Context context, Object[] args) {
        // Check if called with 'new' keyword
        CallInfo callInfo = context.getCallInfo();
        boolean isNew = callInfo != null && callInfo.constructor;

        // ES6: Date() without 'new' returns string of current time, ignoring all arguments
        if (!isNew) {
            return new JsDate().toString();
        }

        // new Date()
        if (args.length == 0) {
            return new JsDate();
        }

        CoreContext cc = context instanceof CoreContext c ? c : null;

        // new Date(value) — single arg: Number, String, Date, or arbitrary object.
        // Spec: ToPrimitive(arg, default); if String → parse; else ToNumber.
        if (args.length == 1) {
            Object arg = args[0];
            if (arg instanceof JsDate jd) {
                return new JsDate(jd.getTimeValue());
            }
            if (arg instanceof Date d) {
                return new JsDate(d);
            }
            Object prim = arg;
            if (arg instanceof ObjectLike) {
                prim = Terms.toPrimitive(arg, "default", cc);
                if (cc != null && cc.isError()) {
                    return new JsDate(Double.NaN); // engine will surface error
                }
            } else if (arg instanceof JsValue jv) {
                prim = jv.getJsValue();
            }
            if (prim instanceof String s) {
                return new JsDate(JsDate.parseToTimeValue(s));
            }
            double v = Terms.objectToNumber(prim).doubleValue();
            return new JsDate(v);
        }

        // new Date(year, month [, date [, hours [, minutes [, seconds [, ms]]]]])
        // Each arg coerced via ToNumber (valueOf-aware). ANY NaN input → Invalid Date.
        double[] parts = coerceArgs(args, Math.min(args.length, 7), cc);
        if (parts == null) {
            return new JsDate(Double.NaN);
        }
        double year = parts[0];
        double month = parts[1];
        double date = parts.length > 2 ? parts[2] : 1;
        double hours = parts.length > 3 ? parts[3] : 0;
        double minutes = parts.length > 4 ? parts[4] : 0;
        double seconds = parts.length > 5 ? parts[5] : 0;
        double ms = parts.length > 6 ? parts[6] : 0;

        // Spec: years 0..99 → +1900 (legacy two-digit-year handling)
        if (!Double.isNaN(year)) {
            double iy = year < 0 ? Math.ceil(year) : Math.floor(year);
            if (iy >= 0 && iy <= 99) {
                year = 1900 + iy;
            }
        }

        double day = JsDate.makeDay(year, month, date);
        double time = JsDate.makeTime(hours, minutes, seconds, ms);
        double local = JsDate.makeDate(day, time);
        return new JsDate(JsDate.localToUtc(local));
    }

    /** Coerces N args via spec ToNumber (with valueOf dispatch). Null = a coercion threw. */
    private static double[] coerceArgs(Object[] args, int n, CoreContext cc) {
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            Object v = args[i];
            if (v instanceof ObjectLike && cc != null) {
                v = Terms.toPrimitive(v, "number", cc);
                if (cc.isError()) return null;
            } else if (v instanceof JsValue jv) {
                v = jv.getJsValue();
            }
            out[i] = Terms.objectToNumber(v).doubleValue();
        }
        return out;
    }

    // ---------- static methods ----------

    private Object now(Object[] args) {
        return System.currentTimeMillis();
    }

    private Object parse(Object[] args) {
        if (args.length == 0) {
            return Double.NaN;
        }
        Object arg = args[0];
        String s = (arg == null) ? "null" : String.valueOf(arg instanceof JsValue jv ? jv.getJsValue() : arg);
        double v = JsDate.parseToTimeValue(s);
        return Double.isNaN(v) ? Double.NaN : (long) v;
    }

    private Object utc(Object[] args) {
        return utcWithContext(null, args);
    }

    private Object utcWithContext(CoreContext cc, Object[] args) {
        if (args.length == 0) {
            return Double.NaN;
        }
        double[] parts = coerceArgs(args, Math.min(args.length, 7), cc);
        if (parts == null) {
            return Double.NaN;
        }
        double year = parts[0];
        double month = parts.length > 1 ? parts[1] : 0;
        double date = parts.length > 2 ? parts[2] : 1;
        double hours = parts.length > 3 ? parts[3] : 0;
        double minutes = parts.length > 4 ? parts[4] : 0;
        double seconds = parts.length > 5 ? parts[5] : 0;
        double ms = parts.length > 6 ? parts[6] : 0;

        if (!Double.isNaN(year)) {
            double iy = year < 0 ? Math.ceil(year) : Math.floor(year);
            if (iy >= 0 && iy <= 99) {
                year = 1900 + iy;
            }
        }

        double day = JsDate.makeDay(year, month, date);
        double time = JsDate.makeTime(hours, minutes, seconds, ms);
        double v = JsDate.timeClip(JsDate.makeDate(day, time));
        return Double.isNaN(v) ? Double.NaN : (long) v;
    }

}
