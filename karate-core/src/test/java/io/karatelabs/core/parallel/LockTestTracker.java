/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 */
package io.karatelabs.core.parallel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper class for testing the @lock tag feature.
 * Tracks concurrent execution of scenarios to verify mutual exclusion.
 */
public class LockTestTracker {

    // Track maximum concurrent executions per lock name
    private static final Map<String, AtomicInteger> currentConcurrent = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> maxConcurrent = new ConcurrentHashMap<>();

    // Track execution order for verification
    private static final List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

    // Track exclusive lock violations
    private static final AtomicInteger totalConcurrent = new AtomicInteger(0);
    private static final AtomicInteger maxTotalConcurrent = new AtomicInteger(0);

    /**
     * Reset all tracking data. Call before each test.
     */
    public static void reset() {
        currentConcurrent.clear();
        maxConcurrent.clear();
        executionOrder.clear();
        totalConcurrent.set(0);
        maxTotalConcurrent.set(0);
    }

    /**
     * Called when a scenario starts execution.
     * Returns the current concurrent count for the given lock name.
     */
    public static int enter(String lockName, String scenarioName) {
        // Track total concurrent
        int total = totalConcurrent.incrementAndGet();
        updateMax(maxTotalConcurrent, total);

        // Track per-lock concurrent
        AtomicInteger current = currentConcurrent.computeIfAbsent(lockName, k -> new AtomicInteger(0));
        int count = current.incrementAndGet();

        // Update max
        AtomicInteger max = maxConcurrent.computeIfAbsent(lockName, k -> new AtomicInteger(0));
        updateMax(max, count);

        // Record execution order
        executionOrder.add("START:" + lockName + ":" + scenarioName);

        return count;
    }

    /**
     * Called when a scenario ends execution.
     */
    public static void exit(String lockName, String scenarioName) {
        // Track total concurrent
        totalConcurrent.decrementAndGet();

        // Track per-lock concurrent
        AtomicInteger current = currentConcurrent.get(lockName);
        if (current != null) {
            current.decrementAndGet();
        }

        // Record execution order
        executionOrder.add("END:" + lockName + ":" + scenarioName);
    }

    /**
     * Get the maximum concurrent executions observed for a lock name.
     * For proper @lock behavior, this should be 1.
     */
    public static int getMaxConcurrent(String lockName) {
        AtomicInteger max = maxConcurrent.get(lockName);
        return max != null ? max.get() : 0;
    }

    /**
     * Get the maximum total concurrent executions observed.
     * For @lock=* scenarios, this should be 1 during their execution.
     */
    public static int getMaxTotalConcurrent() {
        return maxTotalConcurrent.get();
    }

    /**
     * Get the execution order for debugging.
     */
    public static List<String> getExecutionOrder() {
        return new ArrayList<>(executionOrder);
    }

    private static void updateMax(AtomicInteger max, int value) {
        int current;
        do {
            current = max.get();
            if (value <= current) {
                return;
            }
        } while (!max.compareAndSet(current, value));
    }

}
