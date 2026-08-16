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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * The coroutine engine behind one {@link JsGenerator}: the generator body runs
 * on its own virtual thread, strictly alternating with the <b>driver</b> (the
 * JS thread calling {@code next}/{@code return}/{@code throw}) under the
 * engine's fair {@code jsLock} — the same foundation as
 * {@link AsyncActivation}, but a synchronous ping-pong instead of a
 * promise-returning spawn.
 * <p>
 * Lifecycle contract (externally reviewed, 3 rounds): a RUNNING step is
 * scope-owned — the driver registers it with its current {@link AsyncScope}
 * exactly once per step (atomic register-only-if-open), and the gen side only
 * ever DEregisters (at yield publication, and in its outer finally). A
 * SUSPENDED generator is deliberately NOT scope-owned: it is inert work,
 * holds no {@link AsyncToken}, survives eval-scope close, and is resumable
 * from a later eval on the same engine. Abandoned suspended generators are
 * cleaned up explicitly via {@code return()} / for-of close; GC reclaim of a
 * parked vthread whose generator became unreachable is a best-effort
 * backstop, not a contract.
 */
final class GeneratorActivation implements Runnable {

    private static final AtomicLong IDS = new AtomicLong();

    /** JVM-wide, monotonic, never reset — same shape as
     *  {@link AsyncActivation#anyAsync}: while false, the interpreter's
     *  yield dispatch can skip the ThreadLocal lookup. */
    static volatile boolean anyGenerators;

    private static final ThreadLocal<GeneratorActivation> CURRENT = new ThreadLocal<>();

    enum State {NOT_STARTED, RUNNING, SUSPENDED, DONE}

    enum ResumeKind {NEXT, THROW, RETURN}

    enum OutcomeKind {YIELDED, RETURNED, THREW, HOST_CANCELLED}

    static final class StepOutcome {
        final OutcomeKind kind;
        final Object value;

        StepOutcome(OutcomeKind kind, Object value) {
            this.kind = kind;
            this.value = value;
        }
    }

    /** What the driver deposited for the generator to act on. */
    static final class ResumeSignal {
        final ResumeKind kind;
        final Object value;

        ResumeSignal(ResumeKind kind, Object value) {
            this.kind = kind;
            this.value = value;
        }
    }

    final Engine engine;
    private final JsFunctionNode function;
    private final CoreContext functionContext;
    private final Object[] args;

    final AtomicReference<State> state = new AtomicReference<>(State.NOT_STARTED);
    private final CountDownLatch finished = new CountDownLatch(1);

    // Per-step handoff state. All plain fields are written by the driver under
    // jsLock before the gen is unparked, and read by the gen after it
    // reacquires jsLock — visibility is carried by the lock. The step cell and
    // resumeReady flag are the park/unpark signals and are individually safe.
    private volatile AtomicReference<StepOutcome> stepCell;
    private ResumeKind resumeKind;
    private Object resumeValue;
    /** The scope the driver registered this step with; gen-side deregistration
     *  reads it. Written under jsLock by the driver. */
    private AsyncScope stepScope;
    private volatile boolean resumeReady;
    private volatile Thread thread;
    private volatile Thread driver;

    GeneratorActivation(Engine engine, JsFunctionNode function, CoreContext functionContext, Object[] args) {
        this.engine = engine;
        this.function = function;
        this.functionContext = functionContext;
        this.args = args;
    }

    /** The activation whose body is running on this thread, or null. */
    static GeneratorActivation current() {
        return anyGenerators ? CURRENT.get() : null;
    }

    boolean isDone() {
        return state.get() == State.DONE;
    }

    boolean isRunning() {
        return state.get() == State.RUNNING;
    }

    boolean isStarted() {
        return state.get() != State.NOT_STARTED;
    }

    /** {@code return(v)} / {@code throw(e)} on a NOT_STARTED or DONE generator
     *  runs no body code — just retire it. */
    void retire() {
        state.set(State.DONE);
    }

    /**
     * One driver step: deposit the resume input, hand the coroutine the lock,
     * park until it yields / returns / throws, and return the outcome. Caller
     * (JsGenerator) has already done brand/state shortcuts; this method owns
     * the RUNNING transition.
     */
    StepOutcome step(ResumeKind kind, Object value) {
        anyGenerators = true;
        State s = state.get();
        if (s == State.RUNNING) {
            throw JsErrorException.typeError("Generator is already running");
        }
        if (s == State.DONE) {
            // raced with a concurrent completion — treat as done
            return new StepOutcome(OutcomeKind.RETURNED, Terms.UNDEFINED);
        }
        if (!state.compareAndSet(s, State.RUNNING)) {
            // a concurrent host caller won the step; spec answer is the same
            // "already running" TypeError
            throw JsErrorException.typeError("Generator is already running");
        }
        AtomicReference<StepOutcome> cell = new AtomicReference<>();
        stepCell = cell;
        resumeKind = kind;
        resumeValue = value;
        driver = Thread.currentThread();
        // register-only-if-open: a RUNNING step is scope-owned, cancellable
        // work; a closed scope must not accept it
        AsyncScope scope = engine.currentScope();
        if (scope != null && !scope.addGenerator(this)) {
            state.set(State.DONE);
            throw new EngineInterruptedException();
        }
        stepScope = scope;
        boolean firstStep = s == State.NOT_STARTED;
        if (firstStep) {
            Thread vthread;
            try {
                vthread = Thread.ofVirtual().name("js-gen-", IDS.incrementAndGet()).unstarted(this);
                thread = vthread;
                vthread.start();
            } catch (Throwable e) {
                // start failure: the driver still holds jsLock, nothing is
                // stranded — deregister, retire, rethrow
                deregister();
                state.set(State.DONE);
                finished.countDown();
                throw e;
            }
        }
        int holds = engine.releaseJsLock();
        if (!firstStep) {
            resumeReady = true;
            LockSupport.unpark(thread);
        }
        try {
            while (cell.get() == null) {
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    // hand cancellation to the running step, then unwind
                    // WITHOUT reacquiring jsLock (ownership belongs to the gen
                    // or nobody; enclosing frames unlock conditionally)
                    cancel();
                    throw new EngineInterruptedException();
                }
                LockSupport.park();
            }
        } finally {
            driver = null;
        }
        engine.reacquireJsLock(holds);
        return cell.get();
    }

    /**
     * Gen side: publish {@code YIELDED(value)}, park until the next driver
     * step deposits a resume input, and return that input. Called from
     * {@link Interpreter} yield evaluation on the generator thread, holding
     * {@code jsLock}.
     */
    ResumeSignal yieldAndReceive(Object value) {
        // the step is complete the moment the value is published — deregister
        // FIRST so a suspended generator is never scope-owned
        deregister();
        state.set(State.SUSPENDED);
        publish(OutcomeKind.YIELDED, value);
        int holds = engine.releaseJsLock();
        try {
            while (!resumeReady) {
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    throw new EngineInterruptedException();
                }
                LockSupport.park();
            }
        } finally {
            resumeReady = false;
        }
        engine.reacquireJsLock(holds);
        // gate check: the driver registered this step with its scope before
        // unparking us; if that scope died in between, do not run JS
        AsyncScope scope = stepScope;
        if (scope != null && !scope.isOpen()) {
            throw new EngineInterruptedException();
        }
        return new ResumeSignal(resumeKind, resumeValue);
    }

    private void publish(OutcomeKind kind, Object value) {
        AtomicReference<StepOutcome> cell = stepCell;
        if (cell != null && cell.compareAndSet(null, new StepOutcome(kind, value))) {
            Thread d = driver;
            if (d != null) {
                LockSupport.unpark(d);
            }
        }
    }

    /** The scope the current step is registered with (null when the driver ran
     *  outside any eval scope) — used by {@link Engine#enterEvalScope} to
     *  recognize a nested eval() on this vthread as part of that eval. */
    AsyncScope currentStepScope() {
        return stepScope;
    }

    private void deregister() {
        AsyncScope scope = stepScope;
        if (scope != null) {
            scope.removeGenerator(this);
        }
    }

    @Override
    public void run() {
        CURRENT.set(this);
        try {
            runBody();
        } finally {
            CURRENT.remove();
            state.set(State.DONE);
            deregister();
            // last-resort publication so a driver can never park forever
            publish(OutcomeKind.HOST_CANCELLED, null);
            finished.countDown();
            if (engine.jsLock.isHeldByCurrentThread()) {
                engine.releaseJsLock();
            }
        }
    }

    private void runBody() {
        try {
            engine.jsLock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return; // never started; the outer finally publishes HOST_CANCELLED
        }
        Engine previous = Engine.enter(engine);
        try {
            AsyncScope scope = stepScope;
            if (scope != null && !scope.isOpen()) {
                return;
            }
            // the first resume input is consumed here: its kind is NEXT by
            // construction (return/throw on NOT_STARTED never start the body)
            // and its value is discarded per spec — the body starts at the
            // top, not at a yield
            Object result = function.executeBody(functionContext, functionContext, args);
            if (functionContext.isError()) {
                Object reason = functionContext.getErrorThrown();
                functionContext.reset();
                publish(OutcomeKind.THREW, reason);
            } else {
                publish(OutcomeKind.RETURNED, result);
            }
        } catch (StackOverflowError e) {
            publish(OutcomeKind.THREW,
                    JsErrorException.rangeError("Maximum call stack size exceeded").payload);
        } catch (Throwable e) {
            if (e instanceof FlowControlSignal || AsyncSupport.isHostCancellation(e)) {
                // host cancellation never becomes a catchable JS error
                publish(OutcomeKind.HOST_CANCELLED, null);
            } else if (e instanceof Error err) {
                publish(OutcomeKind.HOST_CANCELLED, null);
                throw err;
            } else {
                publish(OutcomeKind.THREW, AsyncSupport.reasonOf(e));
            }
        } finally {
            Engine.exit(previous);
            // a suspension that unwound abruptly already released the lock;
            // only unlock what this thread still holds
            if (engine.jsLock.isHeldByCurrentThread()) {
                engine.releaseJsLock();
            }
        }
    }

    /** Shut this step down: interrupt + unpark, same as
     *  {@link AsyncActivation#cancel()}. The gen exits at its next poll or
     *  park point. */
    void cancel() {
        Thread t = thread;
        if (t != null) {
            t.interrupt();
            LockSupport.unpark(t);
        }
    }

    /** Bounded, uninterruptible join — see
     *  {@link AsyncActivation#awaitTermination(long)} for the rationale. */
    boolean awaitTermination(long millis) {
        long deadline = System.nanoTime() + millis * 1_000_000L;
        boolean interrupted = false;
        try {
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return finished.getCount() == 0;
                }
                try {
                    return finished.await(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public String toString() {
        Thread t = thread;
        return "generator[" + (function.name == null ? "anonymous" : function.name)
                + "," + (t == null ? "unstarted" : t.getName()) + "," + state.get() + "]";
    }

}
