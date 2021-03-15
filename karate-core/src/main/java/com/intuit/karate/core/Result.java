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

import com.intuit.karate.Constants;
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

    private static final boolean INCLUDE_METHOD_KARATE_JSON = Boolean.parseBoolean(System.getProperty(Constants.KARATE_CONFIG_INCL_RESULT_METHOD));

    private final String status;
    private final long durationNanos;
    private final boolean aborted;
    private final Throwable error;
    private final boolean skipped;
    private final StepRuntime.MethodMatch matchingMethod;

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
        StepRuntime.MethodMatch matchingMethod = null;
        if (INCLUDE_METHOD_KARATE_JSON) {
            String jsonMatchingMethod = (String) map.get("matchingMethod");
            if (jsonMatchingMethod != null) {
                matchingMethod = StepRuntime.MethodMatch.getBySignatureAndArgs(jsonMatchingMethod);
            }
        }
        return new Result(status, durationNanos, error, aborted, matchingMethod);
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
        if (INCLUDE_METHOD_KARATE_JSON && matchingMethod != null) {
            map.put("matchingMethod", matchingMethod.toString());
        }
        return map;
    }

    private Result(String status, long nanos, Throwable error, boolean aborted, StepRuntime.MethodMatch matchingMethod) {
        this.status = status;
        this.durationNanos = nanos;
        this.error = error;
        this.aborted = aborted;
        this.matchingMethod = matchingMethod;
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
    
    public String getErrorMessage() {
        return error == null ? null : error.getMessage();
    }

    public static Result passed(long nanos) {
        return passed(nanos, null);
    }
    public static Result passed(long nanos, StepRuntime.MethodMatch matchingMethod) {
        return new Result(PASSED, nanos, null, false, matchingMethod);
    }

    public static Result failed(long nanos, Throwable error, Step step) {
        return failed(nanos, error, step, null);
    }

    public static Result failed(long nanos, Throwable error, Step step, StepRuntime.MethodMatch matchingMethod) {
        String message = error.getMessage();
        if (message == null) {
            message = error + ""; // make sure we show something meaningful
        }
        error = new KarateException(message + "\n" + step.getDebugInfo());
        StackTraceElement[] newTrace = new StackTraceElement[]{
            new StackTraceElement("<feature>", ": " + step.getPrefix() + " " + step.getText() + " ", step.getDebugInfo(), step.getLine())
        };
        error.setStackTrace(newTrace);
        return new Result(FAILED, nanos, error, false, matchingMethod);
    }

    public static Result skipped() {
        return new Result(SKIPPED, 0, null, false, null);
    }

    public static Result aborted(long nanos) {
        return aborted(nanos, null);
    }

    public static Result aborted(long nanos, StepRuntime.MethodMatch matchingMethod) {
        return new Result(PASSED, nanos, null, true, matchingMethod);
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

    public StepRuntime.MethodMatch getMatchingMethod() {
        return matchingMethod;
    }

    @Override
    public String toString() {
        return status;
    }

}
