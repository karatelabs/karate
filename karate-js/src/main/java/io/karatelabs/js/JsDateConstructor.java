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
 * Provides static methods like Date.now, Date.parse.
 */
class JsDateConstructor extends JsFunction {

    static final JsDateConstructor INSTANCE = new JsDateConstructor();

    private JsDateConstructor() {
        this.name = "Date";
    }

    @Override
    public Object getMember(String name) {
        return switch (name) {
            case "now" -> (JsInvokable) this::now;
            case "parse" -> (JsInvokable) this::parse;
            case "prototype" -> JsDatePrototype.INSTANCE;
            default -> super.getMember(name);
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

        // new Date(...) - process arguments and return JsDate object
        if (args.length == 0) {
            return new JsDate();
        } else if (args.length == 1) {
            Object arg = args[0];
            if (arg instanceof Number n) {
                return new JsDate(n.longValue());
            } else if (arg instanceof String s) {
                return new JsDate(s);
            } else if (arg instanceof JsDate date) {
                return new JsDate(date.getTime());
            } else if (arg instanceof Date date) {
                return new JsDate(date);
            } else {
                return new JsDate();
            }
        } else if (args.length >= 3) {
            int year = args[0] instanceof Number ? ((Number) args[0]).intValue() : 0;
            int month = args[1] instanceof Number ? ((Number) args[1]).intValue() : 0;
            int day = args[2] instanceof Number ? ((Number) args[2]).intValue() : 1;
            if (args.length >= 6) {
                int hours = args[3] instanceof Number ? ((Number) args[3]).intValue() : 0;
                int minutes = args[4] instanceof Number ? ((Number) args[4]).intValue() : 0;
                int seconds = args[5] instanceof Number ? ((Number) args[5]).intValue() : 0;
                if (args.length >= 7) {
                    int ms = args[6] instanceof Number ? ((Number) args[6]).intValue() : 0;
                    return new JsDate(year, month, day, hours, minutes, seconds, ms);
                } else {
                    return new JsDate(year, month, day, hours, minutes, seconds);
                }
            } else {
                return new JsDate(year, month, day);
            }
        } else {
            return new JsDate();
        }
    }

    // Static methods

    private Object now(Object[] args) {
        return System.currentTimeMillis();
    }

    private Object parse(Object[] args) {
        if (args.length == 0 || args[0] == null) {
            return Double.NaN;
        }
        try {
            String dateStr = args[0].toString();
            return JsDate.parse(dateStr).getTime();
        } catch (Exception e) {
            return Double.NaN;
        }
    }

}
