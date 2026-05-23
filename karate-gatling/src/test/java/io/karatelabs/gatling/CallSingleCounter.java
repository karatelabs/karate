/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 */
package io.karatelabs.gatling;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test helper for observing how many times a Karate callSingle / callOnce target
 * actually executes — the static counter survives across virtual-user executions
 * because the JVM does.
 */
public final class CallSingleCounter {

    private static final AtomicInteger CALLS = new AtomicInteger();

    private CallSingleCounter() {
    }

    public static int incrementAndGet() {
        return CALLS.incrementAndGet();
    }

    public static int get() {
        return CALLS.get();
    }

    public static void reset() {
        CALLS.set(0);
    }
}
