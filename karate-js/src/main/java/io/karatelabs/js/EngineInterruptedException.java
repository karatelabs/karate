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
package io.karatelabs.js;

/**
 * Thrown when the executing thread's interrupt flag is observed at a loop
 * back-edge. Lets hosts terminate long-running JS via
 * {@link Thread#interrupt()} or {@link java.util.concurrent.Future#cancel(boolean)}
 * with {@code mayInterruptIfRunning=true}. Extends {@link EngineException}
 * so existing {@code catch (EngineException)} sites continue to handle it.
 * <p>
 * JS {@code try/catch} cannot swallow this — {@link Interpreter#evalTryStmt}
 * special-cases it and re-throws. We intentionally do <b>not</b> mark this
 * as {@link FlowControlSignal} because karate-core callers
 * ({@code Markup}, {@code ServerRequestCycle}) treat {@code FlowControlSignal}
 * as "intentional redirect/switch — response state already set, return
 * normally," which would mask a host-initiated cancel as a successful run.
 */
public class EngineInterruptedException extends EngineException {

    public EngineInterruptedException() {
        super("interrupted", null);
    }

}
