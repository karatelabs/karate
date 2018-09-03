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
    private final long duration;
    private final boolean aborted;
    private final Throwable error;
    private final boolean skipped;
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap(error == null ? 2 : 3);
        map.put("status", status);
        map.put("duration", duration);
        if (error != null) {
            map.put("error_message", error.getClass().getName() + ": " + error.getMessage());
        }
        return map;
    }

    private Result(String status, long duration, Throwable error, boolean aborted) {
        this.status = status;
        this.duration = duration;
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

    public static Result passed(long duration) {
        return new Result(PASSED, duration, null, false);
    }

    public static Result failed(long duration, Throwable error, Step step) {
        String featureName = Engine.getFeatureName(step);
        StackTraceElement[] newTrace = new StackTraceElement[]{
            new StackTraceElement("✽", step.getPrefix() + ' ' + step.getText() + ' ', featureName, step.getLine())
        };
        error.setStackTrace(newTrace);
        return new Result(FAILED, duration, error, false);
    }

    public static Result skipped() {
        return new Result(SKIPPED, 0, null, false);
    }

    public static Result aborted(long duration) {
        return new Result(SKIPPED, duration, null, true);
    }

    public String getStatus() {
        return status;
    }

    public long getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return status;
    }

}
