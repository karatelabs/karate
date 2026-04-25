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
import java.time.format.TextStyle;
import java.util.Date;
import java.util.Locale;

/**
 * JavaScript Date wrapper. Stores [[DateValue]] as a {@code double} (NaN means
 * "Invalid Date" per spec). Spec ToTime (TimeClip / MakeDay / MakeTime / MakeDate)
 * lives in static helpers so the Constructor and Prototype share them.
 */
non-sealed class JsDate extends JsObject implements JsDateValue {

    // Roundtrip parsing of toString / toUTCString output (used by Date.parse).
    static final DateTimeFormatter TO_STRING_PARSER =
            DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.ENGLISH);
    static final DateTimeFormatter UTC_STRING_PARSER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);

    /**
     * Pads a year to at least 4 digits, with explicit sign for negatives —
     * "-0001", "-1234", "0001", "9999" (no leading + for non-negative; only
     * the spec ISO ±YYYYYY form requires +).
     */
    static String formatYear4(int year) {
        return (year < 0 ? "-" : "") + String.format(Locale.ROOT, "%04d", Math.abs(year));
    }

    /** Spec ISO extended-year: ±YYYYYY for years outside 0..9999, plain YYYY otherwise. */
    static String formatYearIso(int year) {
        if (year >= 0 && year <= 9999) {
            return String.format(Locale.ROOT, "%04d", year);
        }
        return (year < 0 ? "-" : "+") + String.format(Locale.ROOT, "%06d", Math.abs(year));
    }

    /** Format a UTC offset (in seconds) as "GMT±HHMM". */
    static String formatGmtOffset(int offsetSeconds) {
        int totalMin = offsetSeconds / 60;
        char sign = totalMin >= 0 ? '+' : '-';
        int abs = Math.abs(totalMin);
        return String.format(Locale.ROOT, "GMT%c%02d%02d", sign, abs / 60, abs % 60);
    }

    static final double MS_PER_SECOND = 1000d;
    static final double MS_PER_MINUTE = 60_000d;
    static final double MS_PER_HOUR = 3_600_000d;
    static final double MS_PER_DAY = 86_400_000d;

    /** Spec time-clip range: ±8.64e15 ms. */
    static final double MAX_TIME = 8.64e15;

    /** [[DateValue]]. NaN means Invalid Date. Always represents the time in UTC. */
    private double timeValue;

    JsDate() {
        super(null, JsDatePrototype.INSTANCE);
        this.timeValue = System.currentTimeMillis();
    }

    JsDate(double timeValue) {
        super(null, JsDatePrototype.INSTANCE);
        this.timeValue = timeClip(timeValue);
    }

    JsDate(long timestamp) {
        this((double) timestamp);
    }

    JsDate(Date date) {
        this(date == null ? Double.NaN : (double) date.getTime());
    }

    JsDate(int year, int month, int date) {
        this(makeUtcFromLocalParts(year, month, date, 0, 0, 0, 0));
    }

    JsDate(int year, int month, int date, int hours, int minutes, int seconds) {
        this(makeUtcFromLocalParts(year, month, date, hours, minutes, seconds, 0));
    }

    JsDate(int year, int month, int date, int hours, int minutes, int seconds, int ms) {
        this(makeUtcFromLocalParts(year, month, date, hours, minutes, seconds, ms));
    }

    JsDate(Instant instant) {
        this(instant == null ? Double.NaN : (double) instant.toEpochMilli());
    }

    JsDate(LocalDateTime ldt) {
        this(ldt == null ? Double.NaN : (double) ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    JsDate(LocalDate ld) {
        this(ld == null ? Double.NaN : (double) ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    JsDate(ZonedDateTime zdt) {
        this(zdt == null ? Double.NaN : (double) zdt.toInstant().toEpochMilli());
    }

    JsDate(String text) {
        this(parseToTimeValue(text));
    }

    boolean isInvalid() {
        return Double.isNaN(timeValue);
    }

    /** [[DateValue]] in ms (UTC). NaN if Invalid Date. */
    double getTimeValue() {
        return timeValue;
    }

    /** Convenience: long value of the timestamp. Caller is expected to check {@link #isInvalid()} first. */
    long getTime() {
        return (long) timeValue;
    }

    void setTimeValue(double v) {
        this.timeValue = timeClip(v);
    }

    @Override
    public String toString() {
        if (isInvalid()) {
            return "Invalid Date";
        }
        ZonedDateTime z = ZonedDateTime.ofInstant(Instant.ofEpochMilli((long) timeValue), ZoneId.systemDefault());
        // Format manually to honour the spec's negative-year padding (-0001 not 0001 BC).
        String dayName = z.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        String monthName = z.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return String.format(Locale.ROOT, "%s %s %02d %s %02d:%02d:%02d %s",
                dayName, monthName, z.getDayOfMonth(), formatYear4(z.getYear()),
                z.getHour(), z.getMinute(), z.getSecond(),
                formatGmtOffset((int) (localTzaMs((long) timeValue) / 1000L)));
    }

    @Override
    public Object getJavaValue() {
        if (isInvalid()) {
            return null;
        }
        return new Date((long) timeValue);
    }

    @Override
    public Object getJsValue() {
        if (isInvalid()) {
            return Double.NaN;
        }
        return (long) timeValue;
    }

    // ---------- spec helpers ----------

    /**
     * Spec TimeClip (ECMAScript §21.4.1.31). Returns NaN for non-finite or
     * out-of-range values; otherwise truncates to integer milliseconds.
     */
    static double timeClip(double v) {
        if (!Double.isFinite(v) || Math.abs(v) > MAX_TIME) {
            return Double.NaN;
        }
        // ToInteger: truncate toward zero
        return (double) (long) v;
    }

    /**
     * Spec MakeTime (ECMAScript §21.4.1.13).
     * Returns ms-from-day-start; NaN if any input is non-finite.
     */
    static double makeTime(double h, double m, double s, double milli) {
        if (!Double.isFinite(h) || !Double.isFinite(m) || !Double.isFinite(s) || !Double.isFinite(milli)) {
            return Double.NaN;
        }
        return trunc(h) * MS_PER_HOUR + trunc(m) * MS_PER_MINUTE
                + trunc(s) * MS_PER_SECOND + trunc(milli);
    }

    /**
     * Spec MakeDay (ECMAScript §21.4.1.12). Returns days-from-epoch; NaN if any
     * input is non-finite or the resulting date overflows {@link LocalDate}.
     */
    static double makeDay(double year, double month, double date) {
        if (!Double.isFinite(year) || !Double.isFinite(month) || !Double.isFinite(date)) {
            return Double.NaN;
        }
        long y = (long) trunc(year);
        long m = (long) trunc(month);
        long dt = (long) trunc(date);
        long ym = y + Math.floorDiv(m, 12);
        long mn = Math.floorMod(m, 12) + 1; // LocalDate months are 1-12
        // LocalDate range guard: ±999_999_999
        if (ym > 999_999_999L || ym < -999_999_999L) {
            return Double.NaN;
        }
        try {
            LocalDate ld = LocalDate.of((int) ym, (int) mn, 1);
            return (double) ld.toEpochDay() + (double) (dt - 1);
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    /** Spec MakeDate (ECMAScript §21.4.1.11). day*MS_PER_DAY + time, or NaN. */
    static double makeDate(double day, double time) {
        if (!Double.isFinite(day) || !Double.isFinite(time)) {
            return Double.NaN;
        }
        double tv = day * MS_PER_DAY + time;
        if (!Double.isFinite(tv)) {
            return Double.NaN;
        }
        return tv;
    }

    /**
     * Spec UTC(t) — convert a local-time value to a UTC time value by subtracting
     * the local timezone offset. Approximates by querying the offset at the input
     * instant; matches Java's behavior. Returns NaN for non-finite t.
     * <p>
     * Offset is truncated to integer minutes so it agrees with the value reported
     * by {@code getTimezoneOffset()} (per spec, that getter returns integer minutes).
     * Without this clamp, historical zones with sub-minute offsets — e.g. Madras
     * Mean Time at +05:21:10 — produce time values that disagree with the
     * getter's integer-minute reading and break {@code assertRelativeDateMs}.
     */
    static double localToUtc(double localMs) {
        if (!Double.isFinite(localMs)) {
            return Double.NaN;
        }
        return localMs - localTzaMs((long) localMs);
    }

    /**
     * Spec LocalTime(t) — convert a UTC time value to a local-time value by
     * adding the local timezone offset. Returns NaN for non-finite t.
     */
    static double utcToLocal(double utcMs) {
        if (!Double.isFinite(utcMs)) {
            return Double.NaN;
        }
        return utcMs + localTzaMs((long) utcMs);
    }

    /** Local offset in ms, truncated to integer minutes (see {@link #localToUtc} note). */
    static long localTzaMs(long ms) {
        int totalSeconds = ZoneId.systemDefault().getRules()
                .getOffset(Instant.ofEpochMilli(ms)).getTotalSeconds();
        // Truncate toward zero to a whole minute count, then convert to ms.
        int minutes = totalSeconds / 60;
        return minutes * 60_000L;
    }

    private static double trunc(double v) {
        return v < 0 ? Math.ceil(v) : Math.floor(v);
    }

    /**
     * Build a UTC time value from local-time (year, month, day, h, m, s, ms) parts.
     * Used by the legacy int-arg constructors for Java interop.
     */
    private static double makeUtcFromLocalParts(int year, int month, int date,
                                                int hours, int minutes, int seconds, int ms) {
        double day = makeDay(year, month, date);
        double time = makeTime(hours, minutes, seconds, ms);
        double local = makeDate(day, time);
        return timeClip(localToUtc(local));
    }

    // ---------- string parser ----------

    /**
     * Returns a UTC time value for the given string, or NaN if unparseable.
     * Recognised forms (ES Date Time String Format, §21.4.1.18):
     * <ul>
     *   <li>YYYY · YYYY-MM · YYYY-MM-DD — date-only (interpreted as UTC midnight)</li>
     *   <li>...THH:mm · ...THH:mm:ss · ...THH:mm:ss.sss — date+time</li>
     *   <li>Trailing {@code Z} or {@code ±HH:mm} timezone offset (no offset on a
     *       date-time form ⇒ local time)</li>
     * </ul>
     */
    static double parseToTimeValue(String s) {
        if (s == null) {
            return Double.NaN;
        }
        String dateStr = s.trim();
        if (dateStr.isEmpty()) {
            return Double.NaN;
        }
        // Extended-year ISO form: ±YYYYYY-MM-DDTHH:mm:ss.sssZ — handled by hand
        // because Java's Instant.parse rejects the explicit + on the year.
        if (dateStr.length() > 7 && (dateStr.charAt(0) == '+' || dateStr.charAt(0) == '-')) {
            double v = parseExtendedYearForm(dateStr);
            if (!Double.isNaN(v)) {
                return timeClip(v);
            }
        }
        // Pure ISO instant: 2021-01-01T00:00:00.000Z (or with no millis)
        try {
            return Instant.parse(dateStr).toEpochMilli();
        } catch (Exception ignored) {
            // continue
        }
        // Date-only forms: YYYY, YYYY-MM, YYYY-MM-DD → midnight UTC.
        if (!dateStr.contains("T") && !dateStr.contains(" ")) {
            try {
                if (dateStr.matches("[+-]?\\d{4,6}")) {
                    int y = Integer.parseInt(dateStr);
                    return LocalDate.of(y, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                }
                if (dateStr.matches("[+-]?\\d{4,6}-\\d{2}")) {
                    int dash = dateStr.lastIndexOf('-');
                    int y = Integer.parseInt(dateStr.substring(0, dash));
                    int m = Integer.parseInt(dateStr.substring(dash + 1));
                    return LocalDate.of(y, m, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                }
                LocalDate ld = LocalDate.parse(dateStr);
                return ld.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            } catch (Exception ignored) {
                // continue
            }
        }
        // ISO date-time with optional Z suffix
        try {
            if (dateStr.contains("T")) {
                String trimmed = dateStr.endsWith("Z") ? dateStr.substring(0, dateStr.length() - 1) : dateStr;
                LocalDateTime ldt = LocalDateTime.parse(trimmed);
                if (dateStr.endsWith("Z")) {
                    return ldt.toInstant(ZoneOffset.UTC).toEpochMilli();
                }
                return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
        } catch (Exception ignored) {
            // continue
        }
        // Date.prototype.toString round-trip: "Thu Jan 01 1970 00:00:00 GMT+0000"
        try {
            return ZonedDateTime.parse(dateStr, TO_STRING_PARSER).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            // continue
        }
        // Date.prototype.toUTCString round-trip: "Thu, 01 Jan 1970 00:00:00 GMT"
        try {
            LocalDateTime ldt = LocalDateTime.parse(dateStr, UTC_STRING_PARSER);
            return ldt.toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (Exception ignored) {
            // continue
        }
        return Double.NaN;
    }

    /**
     * Parse the ES extended-year ISO form: {@code ±YYYYYY[-MM[-DD[Thh:mm[:ss[.sss]][Z|±HH:mm]]]]}.
     * Returns NaN when the input doesn't match.
     */
    private static double parseExtendedYearForm(String s) {
        char sign = s.charAt(0);
        int i = 1;
        // 6 digits expected for the year portion
        if (s.length() < 7 || !isDigits(s, 1, 7)) {
            return Double.NaN;
        }
        int year = Integer.parseInt(s.substring(1, 7));
        if (sign == '-') {
            if (year == 0) {
                // Spec: -000000 is not a valid extended year representation.
                return Double.NaN;
            }
            year = -year;
        }
        i = 7;
        int month = 1, day = 1, hour = 0, minute = 0, second = 0, millis = 0;
        long offsetMs = 0;
        if (i < s.length() && s.charAt(i) == '-') {
            if (i + 3 > s.length() || !isDigits(s, i + 1, i + 3)) return Double.NaN;
            month = Integer.parseInt(s.substring(i + 1, i + 3));
            i += 3;
            if (i < s.length() && s.charAt(i) == '-') {
                if (i + 3 > s.length() || !isDigits(s, i + 1, i + 3)) return Double.NaN;
                day = Integer.parseInt(s.substring(i + 1, i + 3));
                i += 3;
            }
        }
        if (i < s.length() && s.charAt(i) == 'T') {
            if (i + 6 > s.length() || !isDigits(s, i + 1, i + 3) || s.charAt(i + 3) != ':' || !isDigits(s, i + 4, i + 6)) {
                return Double.NaN;
            }
            hour = Integer.parseInt(s.substring(i + 1, i + 3));
            minute = Integer.parseInt(s.substring(i + 4, i + 6));
            i += 6;
            if (i < s.length() && s.charAt(i) == ':') {
                if (i + 3 > s.length() || !isDigits(s, i + 1, i + 3)) return Double.NaN;
                second = Integer.parseInt(s.substring(i + 1, i + 3));
                i += 3;
                if (i < s.length() && s.charAt(i) == '.') {
                    if (i + 4 > s.length() || !isDigits(s, i + 1, i + 4)) return Double.NaN;
                    millis = Integer.parseInt(s.substring(i + 1, i + 4));
                    i += 4;
                }
            }
            // Zone designator
            if (i < s.length()) {
                char z = s.charAt(i);
                if (z == 'Z') {
                    offsetMs = 0;
                    i++;
                } else if (z == '+' || z == '-') {
                    if (i + 6 > s.length() || !isDigits(s, i + 1, i + 3) || s.charAt(i + 3) != ':' || !isDigits(s, i + 4, i + 6)) {
                        return Double.NaN;
                    }
                    int oh = Integer.parseInt(s.substring(i + 1, i + 3));
                    int om = Integer.parseInt(s.substring(i + 4, i + 6));
                    offsetMs = (oh * 60L + om) * 60_000L;
                    if (z == '-') offsetMs = -offsetMs;
                    i += 6;
                }
            }
        }
        if (i != s.length()) return Double.NaN;
        double day0 = makeDay(year, month - 1, day);
        double time = makeTime(hour, minute, second, millis);
        double ts = makeDate(day0, time) - offsetMs;
        return ts;
    }

    private static boolean isDigits(String s, int from, int to) {
        if (to > s.length()) return false;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /** Backward-compat helper used by older Java tests. Returns a {@link Date} (epoch 0 if unparseable). */
    static Date parse(String dateStr) {
        double v = parseToTimeValue(dateStr);
        return new Date(Double.isNaN(v) ? 0L : (long) v);
    }
}
