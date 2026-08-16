/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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

import java.util.List;

/**
 * A unit of queued work owned by an {@link AsyncScope}. Every job holds exactly
 * one {@link AsyncToken}, assigned by the scope when the job is enqueued and
 * released in a {@code finally} after the job runs (a throwing job must never
 * strand the pump).
 * <p>
 * The two JS-relevant classes are microtask (promise reactions, resolutions,
 * thenable adoptions) and timer, and the split is what the pump schedules on:
 * {@link AsyncScope} keeps one FIFO queue per class and always empties the
 * microtask queue before it takes a timer, which is the HTML microtask
 * checkpoint. {@link Control} is engine plumbing, not JS work: it is the atomic
 * wake for a pump owner blocked on an empty queue when an activation requests
 * cancellation.
 */
abstract class AsyncJob {

    /** Assigned by {@link AsyncScope#publishSuccessor} at enqueue time. */
    AsyncToken token;

    /** Runs on the pump owner's thread, holding {@code jsLock}. */
    abstract void run(Engine engine);

    /** Which of the scope's two queues this job belongs in. Only a fired timer
     *  is a macrotask; a control job rides the microtask queue so a teardown
     *  request is not held up behind pending timer callbacks. */
    boolean macrotask() {
        return false;
    }

    /** Promise reactions, resolutions and thenable adoptions. */
    static final class Microtask extends AsyncJob {

        private final Runnable body;
        private final String label;

        Microtask(String label, Runnable body) {
            this.label = label;
            this.body = body;
        }

        @Override
        void run(Engine engine) {
            body.run();
        }

        @Override
        public String toString() {
            return "microtask[" + label + "]";
        }
    }

    /** A fired {@code setTimeout} callback. */
    static final class Timer extends AsyncJob {

        private final JsCallable callback;
        private final Object[] args;
        private final int id;

        Timer(int id, JsCallable callback, Object[] args) {
            this.id = id;
            this.callback = callback;
            this.args = args;
        }

        @Override
        void run(Engine engine) {
            AsyncSupport.invokeQueuedCallback(engine, callback, args);
        }

        @Override
        boolean macrotask() {
            return true;
        }

        @Override
        public String toString() {
            return "timer[" + id + "]";
        }
    }

    /**
     * Engine plumbing — carries an activation-initiated cancellation request to
     * the pump owner. The request flag and this enqueue are one atomic facade
     * operation, so an owner already blocked in the queue wait is woken, and one
     * that has not started waiting sees the flag on its next facade interaction.
     */
    static final class Control extends AsyncJob {

        final String reason;

        Control(String reason) {
            this.reason = reason;
        }

        @Override
        void run(Engine engine) {
            // no JS to run — the pump observes the scope's cancel request and tears down
        }

        @Override
        public String toString() {
            return "control[" + reason + "]";
        }
    }

    static List<AsyncJob> one(AsyncJob job) {
        return List.of(job);
    }

}
