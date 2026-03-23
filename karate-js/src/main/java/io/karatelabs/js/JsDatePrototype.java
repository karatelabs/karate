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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Singleton prototype for Date instances.
 * Contains instance methods like getTime, setTime, toISOString, etc.
 * Inherits from JsObjectPrototype.
 */
class JsDatePrototype extends Prototype {

    static final JsDatePrototype INSTANCE = new JsDatePrototype();

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter UTC_STRING_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").withZone(ZoneOffset.UTC);

    private JsDatePrototype() {
        super(JsObjectPrototype.INSTANCE);
    }

    @Override
    protected Object getBuiltinProperty(String name) {
        return switch (name) {
            case "getTime", "valueOf" -> (JsCallable) this::getTime;
            case "toString" -> (JsCallable) this::toStringMethod;
            case "toISOString" -> (JsCallable) this::toISOString;
            case "toUTCString" -> (JsCallable) this::toUTCString;
            case "getFullYear" -> (JsCallable) this::getFullYear;
            case "getMonth" -> (JsCallable) this::getMonth;
            case "getDate" -> (JsCallable) this::getDate;
            case "getDay" -> (JsCallable) this::getDay;
            case "getHours" -> (JsCallable) this::getHours;
            case "getMinutes" -> (JsCallable) this::getMinutes;
            case "getSeconds" -> (JsCallable) this::getSeconds;
            case "getMilliseconds" -> (JsCallable) this::getMilliseconds;
            case "setDate" -> (JsCallable) this::setDate;
            case "setMonth" -> (JsCallable) this::setMonth;
            case "setFullYear" -> (JsCallable) this::setFullYear;
            case "setHours" -> (JsCallable) this::setHours;
            case "setMinutes" -> (JsCallable) this::setMinutes;
            case "setSeconds" -> (JsCallable) this::setSeconds;
            case "setMilliseconds" -> (JsCallable) this::setMilliseconds;
            case "setTime" -> (JsCallable) this::setTime;
            default -> null;
        };
    }

    // Helper method to get JsDate from this context
    private static JsDate asDate(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsDate date) {
            return date;
        }
        return new JsDate();
    }

    private static ZonedDateTime toZonedDateTime(JsDate date) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
    }

    // Instance methods

    private Object getTime(Context context, Object[] args) {
        return asDate(context).getTime();
    }

    private Object toStringMethod(Context context, Object[] args) {
        return asDate(context).toString();
    }

    private Object toISOString(Context context, Object[] args) {
        return ISO_FORMATTER.format(Instant.ofEpochMilli(asDate(context).getTime()));
    }

    private Object toUTCString(Context context, Object[] args) {
        return UTC_STRING_FORMATTER.format(Instant.ofEpochMilli(asDate(context).getTime()));
    }

    private Object getFullYear(Context context, Object[] args) {
        return toZonedDateTime(asDate(context)).getYear();
    }

    private Object getMonth(Context context, Object[] args) {
        return toZonedDateTime(asDate(context)).getMonthValue() - 1; // 0-indexed
    }

    private Object getDate(Context context, Object[] args) {
        return toZonedDateTime(asDate(context)).getDayOfMonth();
    }

    private Object getDay(Context context, Object[] args) {
        return toZonedDateTime(asDate(context)).getDayOfWeek().getValue() % 7; // Sun=0
    }

    private Object getHours(Context context, Object[] args) {
        return toZonedDateTime(asDate(context)).getHour();
    }

    private Object getMinutes(Context context, Object[] args) {
        return toZonedDateTime(asDate(context)).getMinute();
    }

    private Object getSeconds(Context context, Object[] args) {
        return toZonedDateTime(asDate(context)).getSecond();
    }

    private Object getMilliseconds(Context context, Object[] args) {
        return toZonedDateTime(asDate(context)).getNano() / 1_000_000;
    }

    private Object setDate(Context context, Object[] args) {
        if (args.length == 0 || !(args[0] instanceof Number)) {
            return Double.NaN;
        }
        int day = ((Number) args[0]).intValue();
        JsDate jsDate = asDate(context);
        ZonedDateTime zdt = toZonedDateTime(jsDate);
        // Set to 1st of month, then add (day-1) to handle overflow
        zdt = zdt.withDayOfMonth(1).plusDays(day - 1);
        jsDate.setMillis(zdt.toInstant().toEpochMilli());
        return jsDate.getTime();
    }

    private Object setMonth(Context context, Object[] args) {
        if (args.length == 0 || !(args[0] instanceof Number)) {
            return Double.NaN;
        }
        int month = ((Number) args[0]).intValue();
        JsDate jsDate = asDate(context);
        ZonedDateTime zdt = toZonedDateTime(jsDate);
        int originalDay = zdt.getDayOfMonth();
        // Set to Jan 1st of current year, add months, then restore day
        zdt = zdt.withMonth(1).withDayOfMonth(1).plusMonths(month);
        int maxDay = zdt.toLocalDate().lengthOfMonth();
        zdt = zdt.withDayOfMonth(Math.min(originalDay, maxDay));
        if (args.length > 1 && args[1] instanceof Number) {
            int day = ((Number) args[1]).intValue();
            zdt = zdt.withDayOfMonth(1).plusDays(day - 1);
        }
        jsDate.setMillis(zdt.toInstant().toEpochMilli());
        return jsDate.getTime();
    }

    private Object setFullYear(Context context, Object[] args) {
        if (args.length == 0 || !(args[0] instanceof Number)) {
            return Double.NaN;
        }
        int year = ((Number) args[0]).intValue();
        JsDate jsDate = asDate(context);
        ZonedDateTime zdt = toZonedDateTime(jsDate);
        int originalDay = zdt.getDayOfMonth();
        zdt = zdt.withYear(year);
        // Handle Feb 29 -> Feb 28 for non-leap years
        int maxDay = zdt.toLocalDate().lengthOfMonth();
        if (originalDay > maxDay) {
            zdt = zdt.withDayOfMonth(maxDay);
        }
        if (args.length > 1 && args[1] instanceof Number) {
            int month = ((Number) args[1]).intValue();
            zdt = zdt.withMonth(1).withDayOfMonth(1).plusMonths(month);
            maxDay = zdt.toLocalDate().lengthOfMonth();
            zdt = zdt.withDayOfMonth(Math.min(originalDay, maxDay));
        }
        if (args.length > 2 && args[2] instanceof Number) {
            int day = ((Number) args[2]).intValue();
            zdt = zdt.withDayOfMonth(1).plusDays(day - 1);
        }
        jsDate.setMillis(zdt.toInstant().toEpochMilli());
        return jsDate.getTime();
    }

    private Object setHours(Context context, Object[] args) {
        if (args.length == 0 || !(args[0] instanceof Number)) {
            return Double.NaN;
        }
        int hours = ((Number) args[0]).intValue();
        JsDate jsDate = asDate(context);
        ZonedDateTime zdt = toZonedDateTime(jsDate)
                .withHour(0).plusHours(hours);
        if (args.length > 1 && args[1] instanceof Number) {
            int minutes = ((Number) args[1]).intValue();
            zdt = zdt.withMinute(0).plusMinutes(minutes);
        }
        if (args.length > 2 && args[2] instanceof Number) {
            int seconds = ((Number) args[2]).intValue();
            zdt = zdt.withSecond(0).plusSeconds(seconds);
        }
        if (args.length > 3 && args[3] instanceof Number) {
            int ms = ((Number) args[3]).intValue();
            zdt = zdt.withNano(0).plusNanos(ms * 1_000_000L);
        }
        jsDate.setMillis(zdt.toInstant().toEpochMilli());
        return jsDate.getTime();
    }

    private Object setMinutes(Context context, Object[] args) {
        if (args.length == 0 || !(args[0] instanceof Number)) {
            return Double.NaN;
        }
        int minutes = ((Number) args[0]).intValue();
        JsDate jsDate = asDate(context);
        ZonedDateTime zdt = toZonedDateTime(jsDate)
                .withMinute(0).plusMinutes(minutes);
        if (args.length > 1 && args[1] instanceof Number) {
            int seconds = ((Number) args[1]).intValue();
            zdt = zdt.withSecond(0).plusSeconds(seconds);
        }
        if (args.length > 2 && args[2] instanceof Number) {
            int ms = ((Number) args[2]).intValue();
            zdt = zdt.withNano(0).plusNanos(ms * 1_000_000L);
        }
        jsDate.setMillis(zdt.toInstant().toEpochMilli());
        return jsDate.getTime();
    }

    private Object setSeconds(Context context, Object[] args) {
        if (args.length == 0 || !(args[0] instanceof Number)) {
            return Double.NaN;
        }
        int seconds = ((Number) args[0]).intValue();
        JsDate jsDate = asDate(context);
        ZonedDateTime zdt = toZonedDateTime(jsDate)
                .withSecond(0).plusSeconds(seconds);
        if (args.length > 1 && args[1] instanceof Number) {
            int ms = ((Number) args[1]).intValue();
            zdt = zdt.withNano(0).plusNanos(ms * 1_000_000L);
        }
        jsDate.setMillis(zdt.toInstant().toEpochMilli());
        return jsDate.getTime();
    }

    private Object setMilliseconds(Context context, Object[] args) {
        if (args.length == 0 || !(args[0] instanceof Number)) {
            return Double.NaN;
        }
        int ms = ((Number) args[0]).intValue();
        JsDate jsDate = asDate(context);
        ZonedDateTime zdt = toZonedDateTime(jsDate)
                .withNano(0).plusNanos(ms * 1_000_000L);
        jsDate.setMillis(zdt.toInstant().toEpochMilli());
        return jsDate.getTime();
    }

    private Object setTime(Context context, Object[] args) {
        if (args.length == 0 || !(args[0] instanceof Number)) {
            return Double.NaN;
        }
        long timestamp = ((Number) args[0]).longValue();
        JsDate jsDate = asDate(context);
        jsDate.setMillis(timestamp);
        return timestamp;
    }

}
