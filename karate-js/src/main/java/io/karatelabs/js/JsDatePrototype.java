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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Singleton prototype for Date instances. Inherits from JsObjectPrototype.
 * <p>
 * All getters return {@code NaN} when [[DateValue]] is NaN (Invalid Date).
 * All setters propagate NaN — any NaN-valued component (or the existing date
 * being invalid for non-{@code setTime}/{@code setFullYear} setters) results
 * in the date becoming Invalid. {@code thisTimeValue} TypeErrors when {@code this}
 * is not a Date.
 */
class JsDatePrototype extends Prototype {

    static final JsDatePrototype INSTANCE = new JsDatePrototype();

    private static String formatIso(JsDate d) {
        // Spec ISO format with extended ±YYYYYY year for out-of-range values.
        ZonedDateTime z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(d.getTime()), ZoneOffset.UTC);
        return String.format(Locale.ROOT, "%s-%02d-%02dT%02d:%02d:%02d.%03dZ",
                JsDate.formatYearIso(z.getYear()), z.getMonthValue(), z.getDayOfMonth(),
                z.getHour(), z.getMinute(), z.getSecond(),
                (int) Math.floorMod(d.getTime(), 1000L));
    }

    private static String formatUtc(JsDate d) {
        ZonedDateTime z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(d.getTime()), ZoneOffset.UTC);
        String dayName = z.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        String monthName = z.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return String.format(Locale.ROOT, "%s, %02d %s %s %02d:%02d:%02d GMT",
                dayName, z.getDayOfMonth(), monthName, JsDate.formatYear4(z.getYear()),
                z.getHour(), z.getMinute(), z.getSecond());
    }

    private JsDatePrototype() {
        super(JsObjectPrototype.INSTANCE);
    }

    @Override
    protected Object getBuiltinProperty(String name) {
        // Spec lengths from §21.4.4: most getters and toFoo helpers take 0,
        // setters take their own arity (setMilliseconds=1, setMinutes=3, etc.).
        return switch (name) {
            case "constructor" -> JsDateConstructor.INSTANCE;
            case "getTime" -> method(name, 0, this::getTime);
            case "valueOf" -> method(name, 0, this::getTime);
            case "toString" -> method(name, 0, this::toStringMethod);
            case "toISOString" -> method(name, 0, this::toISOString);
            case "toUTCString" -> method(name, 0, this::toUTCString);
            case "toGMTString" -> method(name, 0, this::toUTCString);
            case "getFullYear" -> method(name, 0, this::getFullYear);
            case "getMonth" -> method(name, 0, this::getMonth);
            case "getDate" -> method(name, 0, this::getDate);
            case "getDay" -> method(name, 0, this::getDay);
            case "getHours" -> method(name, 0, this::getHours);
            case "getMinutes" -> method(name, 0, this::getMinutes);
            case "getSeconds" -> method(name, 0, this::getSeconds);
            case "getMilliseconds" -> method(name, 0, this::getMilliseconds);
            case "getYear" -> method(name, 0, this::getYear);
            case "setDate" -> method(name, 1, this::setDate);
            case "setMonth" -> method(name, 2, this::setMonth);
            case "setFullYear" -> method(name, 3, this::setFullYear);
            case "setHours" -> method(name, 4, this::setHours);
            case "setMinutes" -> method(name, 3, this::setMinutes);
            case "setSeconds" -> method(name, 2, this::setSeconds);
            case "setMilliseconds" -> method(name, 1, this::setMilliseconds);
            case "setTime" -> method(name, 1, this::setTime);
            case "setYear" -> method(name, 1, this::setYear);
            case "setUTCDate" -> method(name, 1, this::setUTCDate);
            case "setUTCMonth" -> method(name, 2, this::setUTCMonth);
            case "setUTCFullYear" -> method(name, 3, this::setUTCFullYear);
            case "setUTCHours" -> method(name, 4, this::setUTCHours);
            case "setUTCMinutes" -> method(name, 3, this::setUTCMinutes);
            case "setUTCSeconds" -> method(name, 2, this::setUTCSeconds);
            case "setUTCMilliseconds" -> method(name, 1, this::setUTCMilliseconds);
            case "toLocaleDateString" -> method(name, 0, this::toLocaleDateString);
            case "toLocaleTimeString" -> method(name, 0, this::toLocaleTimeString);
            case "toLocaleString" -> method(name, 0, this::toLocaleString);
            case "toDateString" -> method(name, 0, this::toDateString);
            case "toTimeString" -> method(name, 0, this::toTimeString);
            case "toJSON" -> method(name, 1, this::toJSON);
            case "getTimezoneOffset" -> method(name, 0, this::getTimezoneOffset);
            case "getUTCFullYear" -> method(name, 0, this::getUTCFullYear);
            case "getUTCMonth" -> method(name, 0, this::getUTCMonth);
            case "getUTCDate" -> method(name, 0, this::getUTCDate);
            case "getUTCDay" -> method(name, 0, this::getUTCDay);
            case "getUTCHours" -> method(name, 0, this::getUTCHours);
            case "getUTCMinutes" -> method(name, 0, this::getUTCMinutes);
            case "getUTCSeconds" -> method(name, 0, this::getUTCSeconds);
            case "getUTCMilliseconds" -> method(name, 0, this::getUTCMilliseconds);
            default -> null;
        };
    }

    /**
     * Spec thisTimeValue: returns the JsDate's [[DateValue]] or TypeErrors if
     * {@code this} is not a Date.
     */
    private static JsDate requireDate(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsDate d) {
            return d;
        }
        throw JsErrorException.typeError("this is not a Date object");
    }

    /** Number-valued time helper, returns NaN-Double for invalid dates, Long otherwise. */
    private static Object boxTime(double v) {
        if (Double.isNaN(v)) {
            return Double.NaN;
        }
        return (long) v;
    }

    private static ZonedDateTime localZdt(JsDate d) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(d.getTime()), ZoneId.systemDefault());
    }

    private static ZonedDateTime utcZdt(JsDate d) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(d.getTime()), ZoneOffset.UTC);
    }

    // ---------- getters ----------

    private Object getTime(Context context, Object[] args) {
        JsDate d = requireDate(context);
        return d.isInvalid() ? Double.NaN : (Object) d.getTime();
    }

    private Object toStringMethod(Context context, Object[] args) {
        return requireDate(context).toString();
    }

    private Object toISOString(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) {
            throw JsErrorException.rangeError("Invalid time value");
        }
        return formatIso(d);
    }

    private Object toUTCString(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) {
            return "Invalid Date";
        }
        return formatUtc(d);
    }

    /**
     * Spec Date.prototype.toJSON: works on ANY object (not just Date) per
     * §21.4.4.37. ToPrimitive(this, hint Number); if Number+non-finite return null;
     * else Invoke(O, "toISOString"). The unusual generic-this pattern is why
     * this method does NOT use {@link #requireDate}.
     */
    private Object toJSON(Context context, Object[] args) {
        Object o = context.getThisObject();
        if (o == null || o == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert null or undefined to object");
        }
        CoreContext cc = cc(context);
        Object tv = o;
        if (o instanceof ObjectLike && cc != null) {
            tv = Terms.toPrimitive(o, "number", cc);
            if (cc.isError()) return null;
        } else if (o instanceof JsValue jv) {
            tv = jv.getJsValue();
        }
        if (tv instanceof Number n && !Double.isFinite(n.doubleValue())) {
            return null;
        }
        // Direct shortcut for Date this — produce ISO string. Avoids the generic
        // Invoke(O, "toISOString") path doing a redundant getMember dispatch.
        if (o instanceof JsDate d) {
            return formatIso(d);
        }
        // Invoke O.toISOString()
        if (o instanceof ObjectLike ol) {
            Object iso = ol.getMember("toISOString");
            if (iso instanceof JsCallable jc) {
                CoreContext sub = (cc == null) ? null : new CoreContext(cc, null, null);
                if (sub != null) sub.thisObject = o;
                Object r = jc.call(sub == null ? context : sub, new Object[0]);
                if (sub != null && sub.isError() && cc != null) {
                    cc.updateFrom(sub);
                    return null;
                }
                return r;
            }
        }
        throw JsErrorException.typeError("toISOString is not a function");
    }

    private Object getFullYear(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return localZdt(d).getYear();
    }

    private Object getYear(Context context, Object[] args) {
        // Annex B: getYear returns getFullYear() - 1900
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return localZdt(d).getYear() - 1900;
    }

    private Object getMonth(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return localZdt(d).getMonthValue() - 1;
    }

    private Object getDate(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return localZdt(d).getDayOfMonth();
    }

    private Object getDay(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return localZdt(d).getDayOfWeek().getValue() % 7;
    }

    private Object getHours(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return localZdt(d).getHour();
    }

    private Object getMinutes(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return localZdt(d).getMinute();
    }

    private Object getSeconds(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return localZdt(d).getSecond();
    }

    private Object getMilliseconds(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return (int) (Math.floorMod(d.getTime(), 1000L));
    }

    private Object getTimezoneOffset(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        // Negate the LocalTZA (in minutes) — JS reports +5:30 as -330. Uses the
        // same minute-truncated offset as JsDate.localToUtc / utcToLocal so
        // assertRelativeDateMs round-trips for sub-minute historical zones.
        return -(int) (JsDate.localTzaMs(d.getTime()) / 60_000L);
    }

    private Object getUTCFullYear(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return utcZdt(d).getYear();
    }

    private Object getUTCMonth(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return utcZdt(d).getMonthValue() - 1;
    }

    private Object getUTCDate(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return utcZdt(d).getDayOfMonth();
    }

    private Object getUTCDay(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return utcZdt(d).getDayOfWeek().getValue() % 7;
    }

    private Object getUTCHours(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return utcZdt(d).getHour();
    }

    private Object getUTCMinutes(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return utcZdt(d).getMinute();
    }

    private Object getUTCSeconds(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return utcZdt(d).getSecond();
    }

    private Object getUTCMilliseconds(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return Double.NaN;
        return (int) (Math.floorMod(d.getTime(), 1000L));
    }

    // ---------- setters ----------
    //
    // Spec ordering for every setter (applies to setHours / setMinutes / setSeconds /
    // setMilliseconds / setDate / setMonth / setFullYear and their UTC variants):
    //
    //   1. Read t = thisTimeValue(this) — BEFORE coercing args.
    //   2. ToNumber each provided arg in source order. valueOf / toString on
    //      object args may throw or mutate `this` — neither short-circuits the
    //      remaining coercions per spec.
    //   3. If any coercion errored, propagate (the surrounding engine will throw).
    //   4. If t was NaN, return NaN WITHOUT touching [[DateValue]] (so a valueOf
    //      that mutated `this` back to a valid value is preserved).
    //   5. Else compute new t, TimeClip + UTC where applicable, store, return.

    /** Coerces N args to Number. Returns null if any coercion threw (caller bails). */
    private static double[] coerceArgs(Object[] args, int n, CoreContext cc) {
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            if (i >= args.length) {
                out[i] = Double.NaN; // sentinel for "not provided"
                continue;
            }
            Object v = args[i];
            if (v instanceof ObjectLike && cc != null) {
                v = Terms.toPrimitive(v, "number", cc);
                if (cc.isError()) {
                    return null;
                }
            } else if (v instanceof JsValue jv) {
                v = jv.getJsValue();
            }
            out[i] = Terms.objectToNumber(v).doubleValue();
        }
        return out;
    }

    private static CoreContext cc(Context context) {
        return context instanceof CoreContext c ? c : null;
    }

    private Object setTime(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double[] c = coerceArgs(args, 1, cc(context));
        if (c == null) return Double.NaN; // engine will surface error
        d.setTimeValue(c[0]);
        return boxTime(d.getTimeValue());
    }

    private Object setMilliseconds(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        double[] c = coerceArgs(args, 1, cc(context));
        if (c == null) return Double.NaN;
        if (Double.isNaN(t)) return Double.NaN;
        double local = JsDate.utcToLocal(t);
        ZonedDateTime z = localZdt(d);
        double time = JsDate.makeTime(z.getHour(), z.getMinute(), z.getSecond(), c[0]);
        double newLocal = JsDate.makeDate(Math.floor(local / JsDate.MS_PER_DAY), time);
        d.setTimeValue(JsDate.localToUtc(newLocal));
        return boxTime(d.getTimeValue());
    }

    private Object setSeconds(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        int n = Math.min(args.length, 2);
        if (n == 0) n = 1;
        double[] c = coerceArgs(args, n, cc(context));
        if (c == null) return Double.NaN;
        if (Double.isNaN(t)) return Double.NaN;
        ZonedDateTime z = localZdt(d);
        double local = JsDate.utcToLocal(t);
        double useMs = (n > 1) ? c[1] : Math.floorMod((long) t, 1000L);
        double time = JsDate.makeTime(z.getHour(), z.getMinute(), c[0], useMs);
        double newLocal = JsDate.makeDate(Math.floor(local / JsDate.MS_PER_DAY), time);
        d.setTimeValue(JsDate.localToUtc(newLocal));
        return boxTime(d.getTimeValue());
    }

    private Object setMinutes(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        int n = Math.min(args.length, 3);
        if (n == 0) n = 1;
        double[] c = coerceArgs(args, n, cc(context));
        if (c == null) return Double.NaN;
        if (Double.isNaN(t)) return Double.NaN;
        ZonedDateTime z = localZdt(d);
        double local = JsDate.utcToLocal(t);
        double useS = (n > 1) ? c[1] : z.getSecond();
        double useMs = (n > 2) ? c[2] : Math.floorMod((long) t, 1000L);
        double time = JsDate.makeTime(z.getHour(), c[0], useS, useMs);
        double newLocal = JsDate.makeDate(Math.floor(local / JsDate.MS_PER_DAY), time);
        d.setTimeValue(JsDate.localToUtc(newLocal));
        return boxTime(d.getTimeValue());
    }

    private Object setHours(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        int n = Math.min(args.length, 4);
        if (n == 0) n = 1;
        double[] c = coerceArgs(args, n, cc(context));
        if (c == null) return Double.NaN;
        if (Double.isNaN(t)) return Double.NaN;
        ZonedDateTime z = localZdt(d);
        double local = JsDate.utcToLocal(t);
        double useM = (n > 1) ? c[1] : z.getMinute();
        double useS = (n > 2) ? c[2] : z.getSecond();
        double useMs = (n > 3) ? c[3] : Math.floorMod((long) t, 1000L);
        double time = JsDate.makeTime(c[0], useM, useS, useMs);
        double newLocal = JsDate.makeDate(Math.floor(local / JsDate.MS_PER_DAY), time);
        d.setTimeValue(JsDate.localToUtc(newLocal));
        return boxTime(d.getTimeValue());
    }

    private Object setDate(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        double[] c = coerceArgs(args, 1, cc(context));
        if (c == null) return Double.NaN;
        if (Double.isNaN(t)) return Double.NaN;
        ZonedDateTime z = localZdt(d);
        double local = JsDate.utcToLocal(t);
        double timeOfDay = local - Math.floor(local / JsDate.MS_PER_DAY) * JsDate.MS_PER_DAY;
        double day = JsDate.makeDay(z.getYear(), z.getMonthValue() - 1, c[0]);
        double newLocal = JsDate.makeDate(day, timeOfDay);
        d.setTimeValue(JsDate.localToUtc(newLocal));
        return boxTime(d.getTimeValue());
    }

    private Object setMonth(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        int n = Math.min(args.length, 2);
        if (n == 0) n = 1;
        double[] c = coerceArgs(args, n, cc(context));
        if (c == null) return Double.NaN;
        if (Double.isNaN(t)) return Double.NaN;
        ZonedDateTime z = localZdt(d);
        double local = JsDate.utcToLocal(t);
        double timeOfDay = local - Math.floor(local / JsDate.MS_PER_DAY) * JsDate.MS_PER_DAY;
        double useDt = (n > 1) ? c[1] : z.getDayOfMonth();
        double day = JsDate.makeDay(z.getYear(), c[0], useDt);
        double newLocal = JsDate.makeDate(day, timeOfDay);
        d.setTimeValue(JsDate.localToUtc(newLocal));
        return boxTime(d.getTimeValue());
    }

    private Object setFullYear(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        int n = Math.min(args.length, 3);
        if (n == 0) n = 1;
        double[] c = coerceArgs(args, n, cc(context));
        if (c == null) return Double.NaN;
        // Spec: if Invalid Date, treat t as +0 (use local epoch)
        ZonedDateTime z;
        double timeOfDay;
        if (Double.isNaN(t)) {
            z = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
            timeOfDay = 0;
        } else {
            z = localZdt(d);
            double local = JsDate.utcToLocal(t);
            timeOfDay = local - Math.floor(local / JsDate.MS_PER_DAY) * JsDate.MS_PER_DAY;
        }
        double useMo = (n > 1) ? c[1] : (z.getMonthValue() - 1);
        double useDt = (n > 2) ? c[2] : z.getDayOfMonth();
        double day = JsDate.makeDay(c[0], useMo, useDt);
        double newLocal = JsDate.makeDate(day, timeOfDay);
        d.setTimeValue(JsDate.localToUtc(newLocal));
        return boxTime(d.getTimeValue());
    }

    private Object setYear(Context context, Object[] args) {
        // Annex B: setYear(y); y in [0,99] → +1900; NaN → Invalid
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        double[] c = coerceArgs(args, 1, cc(context));
        if (c == null) return Double.NaN;
        double y = c[0];
        if (Double.isNaN(y)) {
            d.setTimeValue(Double.NaN);
            return Double.NaN;
        }
        double iy = y < 0 ? Math.ceil(y) : Math.floor(y);
        if (iy >= 0 && iy <= 99) {
            iy = 1900 + iy;
        }
        ZonedDateTime z;
        double timeOfDay;
        if (Double.isNaN(t)) {
            z = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
            timeOfDay = 0;
        } else {
            z = localZdt(d);
            double local = JsDate.utcToLocal(t);
            timeOfDay = local - Math.floor(local / JsDate.MS_PER_DAY) * JsDate.MS_PER_DAY;
        }
        double day = JsDate.makeDay(iy, z.getMonthValue() - 1, z.getDayOfMonth());
        double newLocal = JsDate.makeDate(day, timeOfDay);
        d.setTimeValue(JsDate.localToUtc(newLocal));
        return boxTime(d.getTimeValue());
    }

    // ---------- UTC setters (no LocalTime/UTC conversion) ----------

    private Object setUTCMilliseconds(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        double[] c = coerceArgs(args, 1, cc(context));
        if (c == null) return Double.NaN;
        if (Double.isNaN(t)) return Double.NaN;
        ZonedDateTime z = utcZdt(d);
        double time = JsDate.makeTime(z.getHour(), z.getMinute(), z.getSecond(), c[0]);
        double v = JsDate.makeDate(Math.floor(t / JsDate.MS_PER_DAY), time);
        d.setTimeValue(v);
        return boxTime(d.getTimeValue());
    }

    private Object setUTCSeconds(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        int n = Math.min(args.length, 2);
        if (n == 0) n = 1;
        double[] c = coerceArgs(args, n, cc(context));
        if (c == null) return Double.NaN;
        if (Double.isNaN(t)) return Double.NaN;
        ZonedDateTime z = utcZdt(d);
        double useMs = (n > 1) ? c[1] : Math.floorMod((long) t, 1000L);
        double time = JsDate.makeTime(z.getHour(), z.getMinute(), c[0], useMs);
        double v = JsDate.makeDate(Math.floor(t / JsDate.MS_PER_DAY), time);
        d.setTimeValue(v);
        return boxTime(d.getTimeValue());
    }

    private Object setUTCMinutes(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        int n = Math.min(args.length, 3);
        if (n == 0) n = 1;
        double[] c = coerceArgs(args, n, cc(context));
        if (c == null) return Double.NaN;
        if (Double.isNaN(t)) return Double.NaN;
        ZonedDateTime z = utcZdt(d);
        double useS = (n > 1) ? c[1] : z.getSecond();
        double useMs = (n > 2) ? c[2] : Math.floorMod((long) t, 1000L);
        double time = JsDate.makeTime(z.getHour(), c[0], useS, useMs);
        double v = JsDate.makeDate(Math.floor(t / JsDate.MS_PER_DAY), time);
        d.setTimeValue(v);
        return boxTime(d.getTimeValue());
    }

    private Object setUTCHours(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        int n = Math.min(args.length, 4);
        if (n == 0) n = 1;
        double[] c = coerceArgs(args, n, cc(context));
        if (c == null) return Double.NaN;
        if (Double.isNaN(t)) return Double.NaN;
        ZonedDateTime z = utcZdt(d);
        double useM = (n > 1) ? c[1] : z.getMinute();
        double useS = (n > 2) ? c[2] : z.getSecond();
        double useMs = (n > 3) ? c[3] : Math.floorMod((long) t, 1000L);
        double time = JsDate.makeTime(c[0], useM, useS, useMs);
        double v = JsDate.makeDate(Math.floor(t / JsDate.MS_PER_DAY), time);
        d.setTimeValue(v);
        return boxTime(d.getTimeValue());
    }

    private Object setUTCDate(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        double[] c = coerceArgs(args, 1, cc(context));
        if (c == null) return Double.NaN;
        if (Double.isNaN(t)) return Double.NaN;
        ZonedDateTime z = utcZdt(d);
        double timeOfDay = t - Math.floor(t / JsDate.MS_PER_DAY) * JsDate.MS_PER_DAY;
        double day = JsDate.makeDay(z.getYear(), z.getMonthValue() - 1, c[0]);
        double v = JsDate.makeDate(day, timeOfDay);
        d.setTimeValue(v);
        return boxTime(d.getTimeValue());
    }

    private Object setUTCMonth(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        int n = Math.min(args.length, 2);
        if (n == 0) n = 1;
        double[] c = coerceArgs(args, n, cc(context));
        if (c == null) return Double.NaN;
        if (Double.isNaN(t)) return Double.NaN;
        ZonedDateTime z = utcZdt(d);
        double timeOfDay = t - Math.floor(t / JsDate.MS_PER_DAY) * JsDate.MS_PER_DAY;
        double useDt = (n > 1) ? c[1] : z.getDayOfMonth();
        double day = JsDate.makeDay(z.getYear(), c[0], useDt);
        double v = JsDate.makeDate(day, timeOfDay);
        d.setTimeValue(v);
        return boxTime(d.getTimeValue());
    }

    private Object setUTCFullYear(Context context, Object[] args) {
        JsDate d = requireDate(context);
        double t = d.getTimeValue();
        int n = Math.min(args.length, 3);
        if (n == 0) n = 1;
        double[] c = coerceArgs(args, n, cc(context));
        if (c == null) return Double.NaN;
        ZonedDateTime z;
        double timeOfDay;
        if (Double.isNaN(t)) {
            z = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
            timeOfDay = 0;
        } else {
            z = utcZdt(d);
            timeOfDay = t - Math.floor(t / JsDate.MS_PER_DAY) * JsDate.MS_PER_DAY;
        }
        double useMo = (n > 1) ? c[1] : (z.getMonthValue() - 1);
        double useDt = (n > 2) ? c[2] : z.getDayOfMonth();
        double day = JsDate.makeDay(c[0], useMo, useDt);
        double v = JsDate.makeDate(day, timeOfDay);
        d.setTimeValue(v);
        return boxTime(d.getTimeValue());
    }

    // ---------- locale / display ----------

    private static Locale parseLocale(Object[] args) {
        if (args.length > 0 && args[0] instanceof String tag) {
            return Locale.forLanguageTag(tag);
        }
        return Locale.getDefault();
    }

    private Object toLocaleDateString(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return "Invalid Date";
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(parseLocale(args));
        return localZdt(d).format(formatter);
    }

    private Object toLocaleTimeString(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return "Invalid Date";
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
                .withLocale(parseLocale(args));
        return localZdt(d).format(formatter);
    }

    private Object toLocaleString(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return "Invalid Date";
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM)
                .withLocale(parseLocale(args));
        return localZdt(d).format(formatter);
    }

    private Object toDateString(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return "Invalid Date";
        ZonedDateTime z = localZdt(d);
        String dayName = z.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        String monthName = z.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return String.format(Locale.ROOT, "%s %s %02d %s",
                dayName, monthName, z.getDayOfMonth(), JsDate.formatYear4(z.getYear()));
    }

    private Object toTimeString(Context context, Object[] args) {
        JsDate d = requireDate(context);
        if (d.isInvalid()) return "Invalid Date";
        ZonedDateTime z = localZdt(d);
        // "HH:mm:ss GMT±HHMM" — must match the test262 regex
        // /^[0-9]{2}:[0-9]{2}:[0-9]{2} GMT[+-][0-9]{4}( \(.+\))?$/.
        return String.format(Locale.ROOT, "%02d:%02d:%02d %s",
                z.getHour(), z.getMinute(), z.getSecond(),
                JsDate.formatGmtOffset((int) (JsDate.localTzaMs(d.getTime()) / 1000L)));
    }

    // Suppresses an unused warning in IDEs — kept for symmetry with the local form
    @SuppressWarnings("unused")
    private static LocalDate utcLocalDate(JsDate d) {
        return utcZdt(d).toLocalDate();
    }
}
