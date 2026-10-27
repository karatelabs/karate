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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Date;

/**
 * A JsDate over a raw Java date ({@link HostMirror.Kind#UTIL_DATE} or
 * {@link HostMirror.Kind#TEMPORAL}), built per access. It keeps the raw value as
 * the bridge-fallback receiver ({@code zdt.format(f)}) and the round-trip result
 * ({@code engine.put(zdt)} then get returns the same ZonedDateTime).
 */
final class JsDateView extends JsDate {

    private final Object javaSource;

    JsDateView(Object javaSource) {
        super(timeValueOf(javaSource));
        this.javaSource = javaSource;
    }

    static double timeValueOf(Object raw) {
        return timeClip(switch (raw) {
            case Date d -> d.getTime();
            case Instant i -> epochMs(i);
            case LocalDateTime ldt -> epochMs(ldt.atZone(ZoneId.systemDefault()).toInstant());
            case LocalDate ld -> epochMs(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
            case ZonedDateTime zdt -> epochMs(zdt.toInstant());
            case OffsetDateTime odt -> epochMs(odt.toInstant());
            default -> Double.NaN;
        });
    }

    /** Epoch ms, or NaN (Invalid Date) past what a long holds — Instant.MAX would
     *  otherwise leak an ArithmeticException; TimeClip rejects the rest of the range. */
    private static double epochMs(Instant instant) {
        try {
            return instant.toEpochMilli();
        } catch (ArithmeticException e) {
            return Double.NaN;
        }
    }

    /** A view of a java.util.Date reads the Date itself, so every view of it sees a
     *  write made through any one. */
    @Override
    double getTimeValue() {
        return javaSource instanceof Date d ? timeClip(d.getTime()) : super.getTimeValue();
    }

    @Override
    void setTimeValue(double v) {
        // A write that stayed on a per-access view would be silently lost. java.time
        // values are immutable: refuse rather than drop the write. A java.util.Date
        // is mutable and is the JS Date's own Java mirror: write through, so
        // `d.setHours(0)` on a Date from Java code changes that Date.
        if (javaSource instanceof Temporal) {
            throw JsErrorException.typeError("cannot modify an immutable "
                    + javaSource.getClass().getName() + " - use its Java methods, e.g. plusDays() or withYear()");
        }
        double clipped = timeClip(v);
        if (Double.isNaN(clipped)) {
            throw JsErrorException.typeError("cannot set a java.util.Date to Invalid Date");
        }
        ((Date) javaSource).setTime((long) clipped);
        super.setTimeValue(clipped);
    }

    /** The java.time value when this is a view of one (host-first), else null. */
    Temporal hostFirstSource() {
        return javaSource instanceof Temporal t ? t : null;
    }

    @Override
    public Object getOriginalJavaValue() {
        return javaSource;
    }

    @Override
    public Object getJavaValue() {
        // a java.util.Date carries any write made through the view, so put-then-get
        // yields the same Java object the caller put
        return isInvalid() ? null : javaSource;
    }

}
