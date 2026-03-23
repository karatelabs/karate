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
package io.karatelabs.core;

/**
 * Represents a performance event for Gatling integration.
 * <p>
 * Events are created after HTTP requests complete, with timing information
 * captured from the request/response cycle. The {@code failed} and {@code message}
 * fields are mutable so that assertion failures can be attributed to the
 * preceding HTTP request.
 */
public class PerfEvent {

    private final String name;
    private final long startTime;
    private final long endTime;
    private final int statusCode;

    private boolean failed;
    private String message;

    /**
     * Create a new performance event.
     *
     * @param startTime  request start time in epoch milliseconds
     * @param endTime    request end time in epoch milliseconds
     * @param name       the request name for reporting
     * @param statusCode the HTTP status code (0 if request failed before response)
     */
    public PerfEvent(long startTime, long endTime, String name, int statusCode) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.statusCode = statusCode;
    }

    public String getName() {
        return name;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "PerfEvent{" +
                "name='" + name + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", statusCode=" + statusCode +
                ", failed=" + failed +
                ", message='" + message + '\'' +
                '}';
    }

}
