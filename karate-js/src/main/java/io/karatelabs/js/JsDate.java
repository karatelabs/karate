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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * JavaScript Date wrapper that provides Date prototype methods.
 */
non-sealed class JsDate extends JsObject implements JsDateValue {

    // Browser-style toString format: "Fri Jan 01 2021 00:00:00 GMT+0000"
    private static final DateTimeFormatter TO_STRING_FORMATTER =
            DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", java.util.Locale.ENGLISH);

    private long millis;

    JsDate() {
        super(null, JsDatePrototype.INSTANCE);
        this.millis = System.currentTimeMillis();
    }

    JsDate(long timestamp) {
        super(null, JsDatePrototype.INSTANCE);
        this.millis = timestamp;
    }

    JsDate(Date date) {
        super(null, JsDatePrototype.INSTANCE);
        this.millis = date.getTime();
    }

    JsDate(int year, int month, int date) {
        super(null, JsDatePrototype.INSTANCE);
        ZonedDateTime zdt = ZonedDateTime.of(year, month + 1, date, 0, 0, 0, 0, ZoneId.systemDefault());
        this.millis = zdt.toInstant().toEpochMilli();
    }

    JsDate(int year, int month, int date, int hours, int minutes, int seconds) {
        super(null, JsDatePrototype.INSTANCE);
        ZonedDateTime zdt = ZonedDateTime.of(year, month + 1, date, hours, minutes, seconds, 0, ZoneId.systemDefault());
        this.millis = zdt.toInstant().toEpochMilli();
    }

    JsDate(int year, int month, int date, int hours, int minutes, int seconds, int ms) {
        super(null, JsDatePrototype.INSTANCE);
        ZonedDateTime zdt = ZonedDateTime.of(year, month + 1, date, hours, minutes, seconds, ms * 1_000_000, ZoneId.systemDefault());
        this.millis = zdt.toInstant().toEpochMilli();
    }

    JsDate(Instant instant) {
        super(null, JsDatePrototype.INSTANCE);
        this.millis = instant.toEpochMilli();
    }

    JsDate(LocalDateTime ldt) {
        super(null, JsDatePrototype.INSTANCE);
        this.millis = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    JsDate(LocalDate ld) {
        super(null, JsDatePrototype.INSTANCE);
        this.millis = ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    JsDate(ZonedDateTime zdt) {
        super(null, JsDatePrototype.INSTANCE);
        this.millis = zdt.toInstant().toEpochMilli();
    }

    JsDate(String text) {
        super(null, JsDatePrototype.INSTANCE);
        this.millis = parseToMillis(text);
    }

    private ZonedDateTime toZonedDateTime() {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    long getTime() {
        return millis;
    }

    void setMillis(long millis) {
        this.millis = millis;
    }

    private static long parseToMillis(String dateStr) {
        // Try ISO format with milliseconds: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
        try {
            return Instant.parse(dateStr.endsWith("Z") ? dateStr : dateStr + "Z").toEpochMilli();
        } catch (Exception e) {
            // continue
        }
        // Try ISO format: yyyy-MM-dd'T'HH:mm:ss
        try {
            if (dateStr.contains("T")) {
                LocalDateTime ldt = LocalDateTime.parse(dateStr.replace("Z", ""));
                return ldt.atZone(dateStr.endsWith("Z") ? ZoneOffset.UTC : ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
            }
        } catch (Exception e) {
            // continue
        }
        // Try date only: yyyy-MM-dd
        try {
            LocalDate ld = LocalDate.parse(dateStr);
            return ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            // continue
        }
        // if parsing fails, return current time
        return System.currentTimeMillis();
    }

    // Public parse method returns Date for backward compatibility
    static Date parse(String dateStr) {
        return new Date(parseToMillis(dateStr));
    }

    @Override
    public String toString() {
        return TO_STRING_FORMATTER.format(toZonedDateTime());
    }

    @Override
    public Object getJavaValue() {
        return new Date(millis);
    }

    @Override
    public Object getJsValue() {
        return millis;
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
                return new JsDate(date.millis);
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
}
