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

/**
 *
 * @author pthomas3
 */
public class Result extends HashMap<String, Object> {

    private static final String PASSED = "passed";
    private static final String FAILED = "failed";
    private static final String SKIPPED = "skipped";

    private final String status;
    private final long duration;
    private final boolean aborted;
    private final Throwable error;

    private Result(String status, long duration, Throwable error, boolean aborted) {
        this.status = status;
        this.duration = duration;
        this.error = error;
        this.aborted = aborted;
        put("status", status);
        put("duration", duration);
        if (error != null) {
            put("error_message", error.getClass().getName() + ": " + error.getMessage());
        }        
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

    public static Result failed(long duration, Throwable error, Scenario scenario, Step step) {
            StackTraceElement[] originalTrace = error.getStackTrace();            
            String featurePath = scenario.getFeature().getRelativePath();
            StackTraceElement[] newTrace = new StackTraceElement[]{
                new StackTraceElement("✽", step.getPrefix() + ' ' + step.getText(), featurePath, step.getLine()),
                originalTrace[0]
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
