/*
 * The MIT License
 *
 * Copyright 2024 Karate Labs Inc.
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

import io.karatelabs.common.Resource;
import io.karatelabs.parser.JsParser;
import io.karatelabs.parser.Node;
import io.karatelabs.parser.NodeType;
import io.karatelabs.parser.ParserException;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class Engine {

    public static boolean DEBUG = false;

    /**
     * The Engine currently executing JS on this thread. Established with
     * save/restore nesting by {@link #evalInternal} / {@link #evalRaw}, and by
     * {@code JsFunctionNode.call} when a host invokes a function directly
     * (possibly with a null caller context) — the function body then runs
     * under its declaring Engine. Lets the JVM-wide built-in prototype
     * singletons resolve their per-Engine mutable state (user-added
     * properties, {@code constructor} cross-references) without threading a
     * context through every property seam — including the ctx-less raw seams
     * like the 1-arg {@code getMember}.
     */
    private static final ThreadLocal<Engine> CURRENT = new ThreadLocal<>();

    /** The Engine currently executing JS on this thread, or null outside JS execution. */
    static Engine current() {
        return CURRENT.get();
    }

    /** Make {@code engine} current for this thread; returns the previous
     *  value, which the caller MUST restore via {@link #exit} (try/finally). */
    static Engine enter(Engine engine) {
        Engine prev = CURRENT.get();
        CURRENT.set(engine);
        return prev;
    }

    static void exit(Engine prev) {
        if (prev == null) {
            // remove (not set-null) so pooled threads don't pin a stale entry
            CURRENT.remove();
        } else {
            CURRENT.set(prev);
        }
    }

    // Single global store. Holds user-visible bindings (Engine.put / top-level
    // var / implicit globals) AND hidden entries (putRootBinding-injected
    // resources, lazy-cached built-ins from initGlobal). The hidden flag on
    // Slot distinguishes them; Engine.getBindings() filters hidden out for
    // host inspection. Used as the script-level bindings (passed in via
    // evalInternal) and as the root's binding store — same instance.
    // Package-private so ContextRoot/JsGlobalThis can read/write directly.
    final BindingsStore bindings = new BindingsStore();

    // Cached host-facing wrapper. Returned by getBindings() — same instance per
    // Engine so identity comparisons and external caching work.
    private final Bindings bindingsView = new Bindings(bindings);

    private final ContextRoot root = new ContextRoot(this);

    // Per-Engine user-added properties on the JVM-wide built-in prototype
    // singletons (e.g. Array.prototype.foo = ...). Keyed by prototype
    // identity, lazily allocated — almost no script mutates built-in
    // prototypes. Dies with this Engine, so prototype pollution can neither
    // leak into another Engine nor be destroyed by one (the historical
    // clear-on-construct reset wiped this state JVM-wide, which corrupted
    // engines mid-evaluation on other threads).
    private Map<Prototype, Map<String, PropertySlot>> protoUserProps;

    // True iff user code installed a canonical numeric key on a built-in
    // prototype in this Engine session. See Prototype.isNumericPropPolluted.
    boolean numericPropPolluted;

    /** Per-Engine user-property overlay for {@code proto} — null when absent
     *  and {@code create} is false. Engine instances are single-threaded at a
     *  time (same posture as {@link BindingsStore}), so plain maps suffice. */
    Map<String, PropertySlot> protoUserProps(Prototype proto, boolean create) {
        if (protoUserProps == null) {
            if (!create) {
                return null;
            }
            protoUserProps = new HashMap<>();
        }
        Map<String, PropertySlot> map = protoUserProps.get(proto);
        if (map == null && create) {
            map = new LinkedHashMap<>();
            protoUserProps.put(proto, map);
        }
        return map;
    }

    /** This Engine's instance of a built-in constructor global ("Array",
     *  "TypeError", …) — created lazily, stable identity per Engine. */
    JsFunction builtinConstructor(String name) {
        return root.builtinConstructor(name);
    }

    //=== async scope + engine lock ====================================================================================
    //
    // Iron rule 1: JS executes only under jsLock. Exactly one thread runs JS at
    // any moment; every locked execution span establishes/restores
    // Engine.current() around itself. Fair, so a resuming activation cannot be
    // starved by an eval thread that keeps re-locking.

    final ReentrantLock jsLock = new ReentrantLock(true);

    /** Guards scope ownership — a different concern from jsLock, which is
     *  released and reacquired many times inside a single open scope. */
    private final Object scopeGate = new Object();
    private Thread scopeOwner;
    private int evalDepth;
    private AsyncScope asyncScope;

    /** Non-null once a teardown deadline expired with an activation still stuck
     *  in non-interruptible host code. Permanent: the engine can never be
     *  trusted again, and embedders must not return it to a reuse pool. */
    private volatile String poisoned;

    /** Drain cap — null (default) leaves cooperative interrupt as the only
     *  outer bound. */
    private Duration asyncDrainTimeout;

    private boolean asyncRejectionWarnOnly;

    /** How long a scope teardown waits for each live activation to terminate
     *  before poisoning the engine. */
    long asyncTeardownMillis = 5000;

    ContextRoot root() {
        return root;
    }

    AsyncScope currentScope() {
        synchronized (scopeGate) {
            return asyncScope;
        }
    }

    Duration asyncDrainTimeout() {
        return asyncDrainTimeout;
    }

    boolean asyncRejectionWarnOnly() {
        return asyncRejectionWarnOnly;
    }

    /**
     * How long {@code Engine.eval} may keep draining async work before it gives
     * up, cancels the scope and throws {@link EngineTimeoutException}. Off by
     * default — cooperative interrupt (and karate-ext's {@code Sandbox.TimeBox})
     * remain the outer bound.
     */
    public void setAsyncDrainTimeout(Duration timeout) {
        this.asyncDrainTimeout = timeout;
    }

    /**
     * Downgrade unhandled promise rejections from "fail the eval" (the default)
     * to a warning on the {@code console} consumer and SLF4J. Covers the eval's
     * own result too: a script whose completion value is a rejected promise
     * returns the rejection reason instead of throwing.
     */
    public void setAsyncRejectionWarnOnly(boolean warnOnly) {
        this.asyncRejectionWarnOnly = warnOnly;
    }

    /** True once this engine has been poisoned — see {@link #poisoned}. */
    public boolean isPoisoned() {
        return poisoned != null;
    }

    void poison(String detail) {
        if (poisoned == null) {
            poisoned = detail;
        }
    }

    /** Checked before waiting for scope ownership or touching jsLock, so a
     *  poisoned engine fails fast rather than blocking on a lock its stuck
     *  activation may still hold. */
    void checkPoisoned() {
        String detail = poisoned;
        if (detail != null) {
            throw new EngineException("engine is poisoned: " + detail, null);
        }
    }

    /** Fully release jsLock regardless of re-entrant hold count, returning the
     *  count so the caller can restore it exactly. */
    int releaseJsLock() {
        int holds = jsLock.getHoldCount();
        for (int i = 0; i < holds; i++) {
            jsLock.unlock();
        }
        return holds;
    }

    void reacquireJsLock(int holds) {
        for (int i = 0; i < holds; i++) {
            jsLock.lock();
        }
    }

    /**
     * Take (or share) the engine's async scope. Outer evals are serialized: a
     * second host thread blocks until the current scope has fully closed,
     * including its drain and teardown. A nested eval on the same thread does
     * not open a new scope — it shares the outer one and its pump owner,
     * tracked by the eval-depth counter.
     *
     * @return true when this call opened the scope (and therefore owns the
     *         end-of-eval drain and the teardown)
     */
    boolean enterEvalScope() {
        synchronized (scopeGate) {
            if (scopeOwner == Thread.currentThread()) {
                evalDepth++;
                return false;
            }
            // An async-activation or generator vthread executing inside the
            // currently open scope is logically part of that eval, even though
            // it is a different Thread — its nested eval() must share the
            // scope, not wait for it to close (which deadlocked: the scope
            // owner is parked waiting for this very thread).
            AsyncScope current = asyncScope;
            if (current != null && runningInsideScope(current)) {
                evalDepth++;
                return false;
            }
            while (scopeOwner != null) {
                try {
                    scopeGate.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new EngineInterruptedException();
                }
            }
            scopeOwner = Thread.currentThread();
            evalDepth = 1;
            asyncScope = new AsyncScope(this);
            return true;
        }
    }

    /** True when the current thread is an activation (async or generator)
     *  whose work belongs to {@code scope}. */
    private static boolean runningInsideScope(AsyncScope scope) {
        AsyncActivation act = AsyncActivation.current();
        if (act != null && act.scope == scope) {
            return true;
        }
        GeneratorActivation gen = GeneratorActivation.current();
        return gen != null && gen.currentStepScope() == scope;
    }

    void exitEvalScope(boolean outermost) {
        synchronized (scopeGate) {
            evalDepth--;
            if (outermost) {
                scopeOwner = null;
                asyncScope = null;
                scopeGate.notifyAll();
            }
        }
    }

    /** Teardown before ownership is handed on: no subsequent outer eval may
     *  start until it completes, so a cancelled activation's preserved stack can
     *  never mutate a later eval's state. */
    void closeScope(AsyncScope scope, String reason) {
        List<Object> stuck = scope.close(reason, asyncTeardownMillis);
        if (!stuck.isEmpty()) {
            poison("activation did not terminate at teardown: " + stuck.get(0));
        }
    }

    public Object eval(Node program) {
        return evalInternal(program, null);
    }

    public Object eval(Resource resource) {
        return evalInternal(resource, null);
    }

    public Object eval(File file) {
        return evalInternal(Resource.from(file.toPath()), null);
    }

    public Object eval(String text) {
        return evalInternal(Resource.text(text), null);
    }

    /**
     * Parses JS source text into an AST {@link Node} without evaluating it.
     * Exposed for tools (linters, formatters, AST patchers) that need source
     * positions and structure but not execution.
     */
    public static Node parse(String text) {
        return new JsParser(Resource.text(text)).parse();
    }

    public Object evalWith(Node program, Map<String, Object> vars) {
        return evalInternal(program, vars);
    }

    public Object evalWith(String text, Map<String, Object> vars) {
        return evalWith(Resource.text(text), vars);
    }

    public Object evalWith(Resource resource, Map<String, Object> vars) {
        return evalInternal(resource, vars);
    }

    public Object get(String name) {
        return toJava(bindings.getMember(name));
    }

    public void put(String name, Object value) {
        bindings.putMember(name, value);
    }

    public void putRootBinding(String name, Object value) {
        bindings.putHidden(name, value);
    }

    public void remove(String name) {
        bindings.remove(name);
    }

    public void setOnConsoleLog(Consumer<String> onConsoleLog) {
        root.setOnConsoleLog(onConsoleLog);
    }

    /**
     * Returns the user-visible bindings as an auto-unwrapping Map.
     * Values retrieved via get() are automatically converted (JsDate → Date, undefined → null, etc.).
     * Hidden entries (putRootBinding-injected, lazy built-ins) are filtered out.
     */
    public Map<String, Object> getBindings() {
        return bindingsView;
    }

    /**
     * Returns the raw user-visible bindings map without auto-unwrapping.
     * Hidden entries are filtered out. Used for internal engine operations
     * that need raw JS values.
     */
    public Map<String, Object> getRawBindings() {
        return bindings.getRawMap(false);
    }

    public Context getRootContext() {
        return root;
    }

    /**
     * Returns the hidden ("root") bindings — the entries injected via
     * {@link #putRootBinding} plus the lazy built-in cache. Used by hosts
     * (e.g. Karate's ScenarioRuntime) to inherit hidden state into a callee.
     */
    public Map<String, Object> getRootBindings() {
        return bindings.getRawMap(true);
    }

    public void setListener(ContextListener listener) {
        root.listener = listener;
    }

    public void setExternalBridge(ExternalBridge bridge) {
        root.bridge = bridge;
    }

    public <T> void setDebugSupport(RunInterceptor<T> interceptor, DebugPointFactory<T> factory) {
        root.interceptor = interceptor;
        root.pointFactory = factory;
    }

    public RunInterceptor<?> getInterceptor() {
        return root.interceptor;
    }

    public DebugPointFactory<?> getPointFactory() {
        return root.pointFactory;
    }

    /**
     * Converts a JS value to its Java equivalent.
     * - JsValue types (including JsUndefined) are unwrapped via getJavaValue()
     * - JsFunction is wrapped to auto-convert return values
     */
    static Object toJava(Object value) {
        if (value instanceof JsValue jv) {
            return jv.getJavaValue();
        }
        if (value instanceof JsFunction fn) {
            return new JsFunctionWrapper(fn);
        }
        return value;
    }

    // For testing: returns raw JS result without toJava() conversion
    protected Object evalRaw(String text) {
        JsParser parser = new JsParser(Resource.text(text));
        Node program = parser.parse();
        checkPoisoned();
        boolean outermost = enterEvalScope();
        AsyncScope scope = currentScope();
        try {
            CoreContext context = new CoreContext(root, null, 0, program, ContextScope.GLOBAL, bindings);
            Object result = runLocked(program, context);
            return outermost ? AsyncSupport.finishScope(this, scope, result) : result;
        } finally {
            if (outermost) {
                closeScope(scope, "eval complete");
            }
            exitEvalScope(outermost);
        }
    }

    /** One locked execution span: acquire jsLock, establish {@code current()},
     *  run, restore and release. */
    private Object runLocked(Node program, CoreContext context) {
        jsLock.lock();
        Engine prevEngine = enter(this);
        try {
            context.event(EventType.CONTEXT_ENTER, program);
            Object result = Interpreter.eval(program, context);
            context.event(EventType.CONTEXT_EXIT, program);
            return result;
        } finally {
            exit(prevEngine);
            // An inner frame (an activation handshake whose outcome-wait was
            // interrupted, a pump that gave up) may have released the lock and
            // unwound without reacquiring — the caller's unwind must never
            // execute an unmatched unlock().
            if (jsLock.isHeldByCurrentThread()) {
                jsLock.unlock();
            }
        }
    }

    private Object evalInternal(Resource resource, Map<String, Object> localVars) {
        JsParser parser = new JsParser(resource);
        return evalInternal(parser.parse(), localVars);
    }

    private Object evalInternal(Node program, Map<String, Object> localVars) {
        checkPoisoned();
        boolean outermost = enterEvalScope();
        AsyncScope scope = currentScope();
        try {
            return evalScoped(program, localVars, scope, outermost);
        } finally {
            if (outermost) {
                closeScope(scope, "eval complete");
            }
            exitEvalScope(outermost);
        }
    }

    private Object evalScoped(Node program, Map<String, Object> localVars, AsyncScope scope, boolean outermost) {
        try {
            root.evalId++;
            CoreContext context;
            if (localVars == null) {
                context = new CoreContext(root, null, 0, program, ContextScope.GLOBAL, bindings);
            } else {
                CoreContext parent = new CoreContext(root, null, -1, new Node(NodeType.ROOT), ContextScope.GLOBAL, bindings);
                context = new CoreContext(root, parent, 0, program, ContextScope.GLOBAL, new BindingsStore(localVars));
            }
            Object result = runLocked(program, context);
            // the end-of-eval drain runs outside jsLock — it re-acquires per job
            if (outermost) {
                result = AsyncSupport.finishScope(this, scope, result);
            }
            return toJava(result);
        } catch (Throwable e) {
            if (e instanceof FlowControlSignal) {
                // flow-control signal from a host function — pass through unchanged
                throw (RuntimeException) e;
            }
            if (e instanceof EngineInterruptedException eie) {
                // host-initiated cancel — pass through unwrapped so callers can
                // distinguish a cancelled run from a JS-origin error
                throw eie;
            }
            if (e instanceof ParserException pe) {
                // preserve parser-failure type so callers can distinguish parse phase
                throw pe;
            }
            String message = e.getMessage();
            if (message == null) {
                message = e + "";
            }
            Resource resource = program.getFirstToken().getResource();
            String relPath = resource.isFile() ? resource.getRelativePath() : null;
            if (relPath != null && !message.endsWith(relPath)) {
                message = message + "\n" + relPath;
            }
            // Preserve structured jsErrorName + jsMessage when we already have an
            // EngineException (e.g., from Interpreter.evalProgram for uncaught JS
            // throws). The outer host-facing message gets relPath appended for logs;
            // the JS-side message stays unframed so callers don't have to parse.
            String jsErrorName = e instanceof EngineException ee ? ee.getJsErrorName() : null;
            String jsMessage = e instanceof EngineException ee ? ee.getJsMessage() : null;
            throw new EngineException(message, e, jsErrorName, jsMessage);
        }
    }

}
