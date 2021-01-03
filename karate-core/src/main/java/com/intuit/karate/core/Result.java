/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.core;

import com.intuit.karate.report.ReportUtils;
import com.intuit.karate.KarateException;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Result {

    private static final String PASSED = "passed";
    private static final String FAILED = "failed";
    private static final String SKIPPED = "skipped";

    private final String status;
    private final long durationNanos;
    private final boolean aborted;
    private final Throwable error;
    private final boolean skipped;

    public Map<String, Object> toCucumberJson() {
        Map<String, Object> map = new HashMap(error == null ? 2 : 3);
        map.put("status", status);
        map.put("duration", durationNanos);
        if (error != null) {
            map.put("error_message", error.getMessage());
        }
        return map;
    }

    public static Result fromKarateJson(Map<String, Object> map) {
        String status = (String) map.get("status");
        Number num = (Number) map.get("nanos");
        long durationNanos = num == null ? 0 : num.longValue();
        String errorMessage = (String) map.get("errorMessage");
        Throwable error = errorMessage == null ? null : new KarateException(errorMessage);
        Boolean aborted = (Boolean) map.get("aborted");
        if (aborted == null) {
            aborted = false;
        }
        return new Result(status, durationNanos, error, aborted);
    }

    public Map<String, Object> toKarateJson() {
        Map<String, Object> map = new HashMap();
        map.put("status", status);
        map.put("millis", getDurationMillis()); // not used in fromKarateJson()
        map.put("nanos", durationNanos);
        if (error != null) {
            map.put("errorMessage", error.getMessage());
        }
        if (aborted) {
            map.put("aborted", true);
        }
        return map;
    }

    private Result(String status, long nanos, Throwable error, boolean aborted) {
        this.status = status;
        this.durationNanos = nanos;
        this.error = error;
        this.aborted = aborted;
        skipped = SKIPPED.equals(status);
    }

    public boolean isSkipped() {
        return skipped;
    }

    public boolean isFailed() {
        return error != null;
    }

    public boolean isAborted() {
        return aborted;
    }

    public Throwable getError() {
        return error;
    }

    public static Result passed(long nanos) {
        return new Result(PASSED, nanos, null, false);
    }

    public static Result failed(long nanos, Throwable error, Step step) {
        String message = error.getMessage();
        if (message == null) {
            message = error + ""; // make sure we show something meaningful
        }
        error = new KarateException(message + "\n" + step.getDebugInfo());
        StackTraceElement[] newTrace = new StackTraceElement[]{
            new StackTraceElement("<feature>", ": " + step.getPrefix() + " " + step.getText() + " ", step.getDebugInfo(), step.getLine())
        };
        error.setStackTrace(newTrace);
        return new Result(FAILED, nanos, error, false);
    }

    public static Result skipped() {
        return new Result(SKIPPED, 0, null, false);
    }

    public static Result aborted(long nanos) {
        return new Result(PASSED, nanos, null, true);
    }

    public String getStatus() {
        return status;
    }

    public long getDurationNanos() {
        return durationNanos;
    }

    public double getDurationMillis() {
        return ReportUtils.nanosToMillis(durationNanos);
    }

    @Override
    public String toString() {
        return status;
    }

}
