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

import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.Tag;
import io.karatelabs.output.LogContext;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages locks for parallel scenario execution.
 * <p>
 * Supports two types of locks via the @lock tag:
 * <ul>
 *   <li>{@code @lock=<name>} - Named lock for mutual exclusion. Scenarios with the same
 *       lock name will run sequentially, but scenarios with different lock names can
 *       run concurrently.</li>
 *   <li>{@code @lock=*} - Exclusive lock. The scenario runs exclusively - no other
 *       scenarios can run concurrently, even those with different lock names.</li>
 * </ul>
 * <p>
 * Implementation uses a read-write lock pattern:
 * <ul>
 *   <li>Named locks acquire the global read lock (allows concurrent execution with other named locks)</li>
 *   <li>Exclusive locks acquire the global write lock (blocks all other scenarios)</li>
 * </ul>
 */
public class ScenarioLockManager {

    private static final Logger logger = LogContext.RUNTIME_LOGGER;

    /**
     * The special lock name that indicates exclusive execution.
     */
    public static final String EXCLUSIVE_LOCK = "*";

    /**
     * Named locks - scenarios with same lock name share the same ReentrantLock.
     */
    private final ConcurrentHashMap<String, ReentrantLock> namedLocks = new ConcurrentHashMap<>();

    /**
     * Global read-write lock for exclusive (@lock=*) support.
     * - Normal named locks acquire read lock (can run concurrently)
     * - Exclusive lock acquires write lock (blocks everything)
     */
    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();

    /**
     * Acquire locks for a scenario based on its @lock tags.
     * Returns a handle that must be used to release the locks.
     * <p>
     * All scenarios acquire at least the global read lock to ensure @lock=*
     * scenarios can truly run exclusively. The overhead is minimal (~100ns)
     * for uncontended read locks.
     *
     * @param scenario the scenario to acquire locks for
     * @return a LockHandle to release the locks (never null)
     */
    public LockHandle acquire(Scenario scenario) {
        List<String> lockNames = getLockNames(scenario);
        String scenarioName = scenario.getName();
        boolean exclusive = lockNames.contains(EXCLUSIVE_LOCK);

        if (exclusive) {
            // Exclusive lock - acquire write lock to block all other scenarios
            logger.info("Acquiring exclusive lock for scenario: {}", scenarioName);
            globalLock.writeLock().lock();
            logger.info("Acquired exclusive lock for scenario: {}", scenarioName);
            return new LockHandle(scenarioName, Collections.emptyList(), true);
        } else {
            // All non-exclusive scenarios acquire the global read lock
            // This ensures @lock=* scenarios truly run exclusively
            globalLock.readLock().lock();

            if (lockNames.isEmpty()) {
                // No named locks, just the global read lock
                return new LockHandle(scenarioName, Collections.emptyList(), false);
            }

            // Sort lock names to prevent deadlocks when multiple locks are acquired
            List<String> sortedNames = new ArrayList<>(lockNames);
            Collections.sort(sortedNames);

            List<ReentrantLock> acquiredLocks = new ArrayList<>();
            for (String name : sortedNames) {
                ReentrantLock lock = namedLocks.computeIfAbsent(name, k -> new ReentrantLock());
                logger.info("Acquiring lock '{}' for scenario: {}", name, scenarioName);
                lock.lock();
                logger.info("Acquired lock '{}' for scenario: {}", name, scenarioName);
                acquiredLocks.add(lock);
            }

            return new LockHandle(scenarioName, acquiredLocks, false);
        }
    }

    /**
     * Release locks previously acquired for a scenario.
     *
     * @param handle the lock handle returned by acquire()
     */
    public void release(LockHandle handle) {
        if (handle == null) {
            return;
        }

        if (handle.exclusive) {
            logger.info("Releasing exclusive lock for scenario: {}", handle.scenarioName);
            globalLock.writeLock().unlock();
        } else {
            // Release named locks in reverse order
            List<ReentrantLock> locks = handle.locks;
            for (int i = locks.size() - 1; i >= 0; i--) {
                locks.get(i).unlock();
            }
            logger.debug("Released lock(s) for scenario: {}", handle.scenarioName);

            // Release global read lock
            globalLock.readLock().unlock();
        }
    }

    /**
     * Extract lock names from scenario's @lock tags.
     * Optimized to avoid allocations when no lock tags exist.
     */
    private List<String> getLockNames(Scenario scenario) {
        List<Tag> tags = scenario.getTagsEffective();
        // Fast path: scan without allocation first
        boolean hasLockTag = false;
        for (Tag tag : tags) {
            if (Tag.LOCK.equals(tag.getName())) {
                hasLockTag = true;
                break;
            }
        }
        if (!hasLockTag) {
            return Collections.emptyList();
        }
        // Slow path: collect lock values
        List<String> lockNames = new ArrayList<>();
        for (Tag tag : tags) {
            if (Tag.LOCK.equals(tag.getName())) {
                List<String> values = tag.getValues();
                if (!values.isEmpty()) {
                    lockNames.addAll(values);
                }
            }
        }
        return lockNames;
    }

    /**
     * Handle for releasing acquired locks.
     */
    public static class LockHandle {
        private final String scenarioName;
        private final List<ReentrantLock> locks;
        private final boolean exclusive;

        LockHandle(String scenarioName, List<ReentrantLock> locks, boolean exclusive) {
            this.scenarioName = scenarioName;
            this.locks = locks;
            this.exclusive = exclusive;
        }
    }

}
