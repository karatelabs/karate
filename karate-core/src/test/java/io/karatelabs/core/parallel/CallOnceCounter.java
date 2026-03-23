/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 */
package io.karatelabs.core.parallel;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper class for testing callOnce feature isolation.
 * Tracks how many times a feature is called across all threads.
 */
public class CallOnceCounter {

    private static final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Increment and return the new count. Called from feature files.
     */
    public static int incrementAndGet() {
        return counter.incrementAndGet();
    }

    /**
     * Get current count without incrementing.
     */
    public static int get() {
        return counter.get();
    }

    /**
     * Reset the counter. Call before each test run.
     */
    public static void reset() {
        counter.set(0);
    }

}
