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

import io.karatelabs.parser.Node;

import java.util.*;

/**
 * Execution context. Two outward links per ECMAScript 8.3:
 * <ul>
 *   <li>{@code outer} — lexical environment chain (the function's definition
 *       site for function contexts; mirrors {@code LexicalEnvironment.outer}).
 *       Threaded by every name lookup ({@link #resolve}).</li>
 *   <li>{@code parent} — dynamic call / control chain (the caller's context).
 *       Used by {@link #getParent()} for host introspection and by
 *       {@link #updateFrom} for return / break / throw propagation up the
 *       call stack.</li>
 * </ul>
 * For non-function child contexts (block, loop) {@code outer == parent}.
 * For function-call contexts, {@code outer = function.declaredContext},
 * {@code parent = invokingContext}, and they generally differ.
 */
class CoreContext implements Context {

    ContextRoot root;

    Object thisObject = Terms.UNDEFINED;
    CallInfo callInfo;
    // The user-defined function whose body is currently executing in this
    // frame — used to resolve `super` (its [[HomeObject]] and the parent
    // constructor). Set per non-arrow function/constructor call; inherited by
    // nested block scopes and arrow frames so `super` in an arrow inside a
    // method still resolves to the enclosing method's home object.
    JsFunctionNode activeFunction;

    // The class private names visible here (null in code that uses none). A block
    // scope inherits it; a function call frame takes it from the CALLEE's captured
    // environment, never from the dynamic caller — see JsFunctionNode.privateEnv.
    PrivateEnv privateEnv;

    // Strict-mode flag (ES "use strict"). Set per function-call context from
    // the callee {@link JsFunctionNode#strict}, and per script context from a
    // top-level directive prologue (see {@link Interpreter#evalProgram}).
    // Block / loop / catch sub-scopes run on the same CoreContext (enterScope,
    // not a fresh context), so they inherit it for free. Built-in call frames
    // are left non-strict so internal [[Set]]s stay lenient. Flips: implicit
    // global assignment (below), `this` substitution, and property-write /
    // delete failures routed through {@link PropertyAccess}.
    boolean strict;

    BindingsStore bindings;

    // Function context fields (non-null indicates this is a function context)
    final Object[] callArgs;
    final CoreContext outer;
    // True for an arrow-function call frame (set by Interpreter.bindArrowThis):
    // an arrow has no `arguments` of its own (§10.2.1.3), so the identifier
    // resolves lexically through the declaring chain — see argumentsForRead().
    boolean arrowFrame;
    // Lazily allocated `arguments` object — one per call frame so successive
    // references share identity (`arguments === arguments`) and writes
    // (`arguments.x = …`) survive within the call.
    private JsArray argumentsObject;

    // Scope management (level-keyed bindings)
    int currentLevel = 0;
    List<ScopeEntry> scopeStack; // Lazy - created on first enterScope

    // Receiver (`this`) for the callable most recently resolved by
    // PropertyAccess.getCallable — replaces a per-call Object[2] tuple.
    // Contract: written by getCallable's projection sites AFTER their
    // getByName/getByIndex/PrivateAccess.get returns (so a getter running a
    // nested same-context call cannot leave a stale receiver behind), nulled
    // explicitly on every no-receiver return, and consumed by the caller
    // immediately after getCallable returns, before any further evaluation.
    Object callReceiver;

    // Captured bindings for closures (references to Slots from function creation time).
    // Stored as an immutable BindingsStore so resolve() walks one chain shape; structural
    // mutation through the captured handle is a no-op (sibling closures still see value
    // updates through the existing-key fast path).
    final BindingsStore capturedBindings;

    // Slot frame (function contexts only, flag-gated): dense storage for the
    // locals SlotTable proved safe. Attached by JsFunctionNode.bindArgsAndExecute.
    // Frame locals never enter `bindings`, and resolve() deliberately does not
    // see them — anything a nested scope could reference was classified STORE
    // by the analyzer. The routing in get/update/declare/hasKey makes the
    // frame indistinguishable from store bindings for code running on THIS
    // context (host introspection, destructuring, for-of bindings included).
    Object[] frame;
    SlotTable frameTable;


    CoreContext(ContextRoot root, CoreContext parent, int depth, Node node, ContextScope scope, BindingsStore bindings) {
        this.root = root;
        this.parent = parent;
        this.depth = depth;
        this.node = node;
        this.scope = scope;
        this.callArgs = null;
        this.outer = null;
        this.capturedBindings = null;
        this.bindings = bindings;
        // Inherit `this` from the parent CoreContext, or from the root when
        // we're a script-level (or evalWith-ghost) context with no parent.
        if (parent != null) {
            thisObject = parent.thisObject;
            strict = parent.strict;
            activeFunction = parent.activeFunction;
            privateEnv = parent.privateEnv;
        } else if (root != null) {
            thisObject = root.thisObject;
        }
    }

    // Unified constructor for child contexts (function calls)
    CoreContext(CoreContext parent, Node node, Object[] functionArgs,
                CoreContext outer, BindingsStore captured) {
        this.root = parent.root;
        this.parent = parent;
        this.depth = parent.depth + 1;
        this.node = node;
        this.scope = ContextScope.FUNCTION;
        this.callArgs = functionArgs;
        this.outer = outer;
        this.capturedBindings = captured;
        this.thisObject = parent.thisObject;
        this.activeFunction = parent.activeFunction;
    }

    // Convenience for built-in function calls (no closure)
    CoreContext(CoreContext parent, Node node, Object[] functionArgs) {
        this(parent, node, functionArgs, null, null);
    }

    void event(EventType type, Node node) {
        if (root.listener != null) {
            Event event = new Event(type, this, node);
            root.listener.onEvent(event);
        }
    }

    void event(EventType type, Node node, Object value) {
        if (root.listener != null) {
            Event event = new Event(type, this, node, value);
            root.listener.onEvent(event);
        }
    }

    // public api ======================================================================================================
    //
    final CoreContext parent;
    final ContextScope scope;
    final int depth;
    final Node node;

    @Override
    public Engine getEngine() {
        return root.getEngine();
    }

    @Override
    public Context getParent() {
        // Internally `parent == null` at the script level (where lookup falls
        // through to `root` directly). Host inspection still expects to walk
        // up to the root via getParent(), so surface the root here.
        return parent != null ? parent : root;
    }

    @Override
    public ContextScope getScope() {
        return scope;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public Node getNode() {
        return node;
    }

    @Override
    public int getIteration() {
        return iteration;
    }

    @Override
    public String getPath() {
        String parentPath = depth == 0 ? null : parent.getPath();
        String suffix = iteration == -1 ? "" : "[" + iteration + "]";
        return parentPath == null ? node.type + suffix : parentPath + "." + node.type + suffix;
    }

    @Override
    public Object getThisObject() {
        return thisObject;
    }

    @Override
    public CallInfo getCallInfo() {
        return callInfo;
    }

    @Override
    public Object[] getCallArgs() {
        return callArgs;
    }

    @Override
    public String toString() {
        return getPath();
    }

    //=== Scope management =============================================================================================
    //
    void enterScope(ContextScope scope, Node node) {
        currentLevel++;
        if (scopeStack == null) {
            scopeStack = new ArrayList<>(4);
        }
        scopeStack.add(new ScopeEntry(currentLevel, scope, node));
    }

    void exitScope() {
        if (scopeStack != null && !scopeStack.isEmpty()) {
            if (bindings != null) {
                bindings.popLevel(currentLevel);
            }
            scopeStack.remove(scopeStack.size() - 1);
            currentLevel--;
        }
    }

    int findFunctionLevel() {
        if (scopeStack != null) {
            for (int i = scopeStack.size() - 1; i >= 0; i--) {
                ScopeEntry entry = scopeStack.get(i);
                if (entry.scope == ContextScope.FUNCTION) {
                    return entry.level;
                }
            }
        }
        return 0;  // Hoist to global if no function scope
    }

    ContextScope getCurrentScope() {
        if (scopeStack != null && !scopeStack.isEmpty()) {
            return scopeStack.getLast().scope;
        }
        return scope;
    }

    //=== Name resolution =============================================================================================
    //
    // Spec mapping: ResolveBinding (ES 8.1.2.1). Walks the lexical chain
    // once and returns the Slot — the unified handle that get / update /
    // hasKey compose over.
    //
    // Chain order: own bindings (local) → captured (closure snapshot) →
    // outer (lexical parent for function contexts; dynamic parent
    // otherwise) → root (with lazy built-in init).
    BindingSlot resolve(String key) {
        if (bindings != null) {
            BindingSlot s = bindings.getSlot(key);
            if (s != null) {
                return s;
            }
        }
        if (capturedBindings != null) {
            BindingSlot s = capturedBindings.getSlot(key);
            if (s != null) {
                return s;
            }
        }
        // Function contexts use lexical scoping — walk outer, NOT parent
        // (parent here is the caller's context, which would give dynamic
        // scoping). Non-function contexts (block / loop scopes inside a
        // function) have outer == null and walk parent. Without this, a
        // caller's parameter name used to shadow the callee's closure-
        // captured `var` / parameter of the same name.
        if (outer != null) {
            BindingSlot s = outer.resolve(key);
            if (s != null) {
                return s;
            }
        } else if (parent != null) {
            BindingSlot s = parent.resolve(key);
            if (s != null) {
                return s;
            }
        }
        return root.resolveOrInit(key);
    }

    Object get(String key) {
        if ("this".equals(key)) {
            return thisObject;
        }
        if (callArgs != null && "arguments".equals(key)) {
            JsArray a = argumentsForRead();
            if (a != null) {
                return a;
            }
            // arrow frame with no enclosing function — ordinary resolution
        }
        if (frameTable != null) {
            int idx = frameTable.indexOf(key);
            if (idx >= 0) {
                Object v = frame[idx];
                if (v != SlotTable.UNDECLARED) {
                    if (v == SlotTable.TDZ) {
                        throw JsErrorException.referenceError("cannot access '" + key + "' before initialization");
                    }
                    return v;
                }
                // undeclared: fall through — pre-declaration the store lacks
                // the name too, so the legacy chain is the same resolution
            }
        }
        BindingSlot s = resolve(key);
        if (s == null) {
            return Terms.UNDEFINED;
        }
        return readSlot(s, key);
    }

    /** The `arguments` visible at this frame: the frame's own for a normal
     *  function call, the lexically enclosing function's for an arrow frame
     *  (§10.2.1.3 — arrows have no own `arguments`; identity is shared with
     *  the enclosing frame's object). Returns null for an arrow with no
     *  enclosing function frame — the caller falls through to ordinary
     *  identifier resolution (a user binding named `arguments`, else
     *  undefined/ReferenceError). */
    JsArray argumentsForRead() {
        if (!arrowFrame) {
            return getArgumentsObject();
        }
        // An explicit binding literally named `arguments` (an arrow parameter
        // or a var) anywhere on the lexical path shadows the implicit object —
        // returning null defers to ordinary identifier resolution, which will
        // find that binding.
        if (hasOwnArgumentsBinding()) {
            return null;
        }
        // Lexical walk: a function frame's declaring context is `outer`;
        // script-level contexts chain via `parent`.
        CoreContext c = outer != null ? outer : parent;
        while (c != null) {
            if (c.hasOwnArgumentsBinding()) {
                return null;
            }
            if (c.callArgs != null && !c.arrowFrame) {
                return c.getArgumentsObject();
            }
            c = c.outer != null ? c.outer : c.parent;
        }
        return null;
    }

    /** True when this context declares its own binding named `arguments`
     *  (frame slot or store) — which must shadow any implicit arguments
     *  object further up the lexical chain. */
    private boolean hasOwnArgumentsBinding() {
        if (frameTable != null) {
            int idx = frameTable.indexOf("arguments");
            if (idx >= 0 && frame != null && frame[idx] != SlotTable.UNDECLARED) {
                return true;
            }
        }
        return bindings != null && bindings.hasMember("arguments");
    }

    /** Returns the `arguments` object for this function call, lazily wrapping
     *  {@link #callArgs} in a {@link JsArray} so writes (named or by index)
     *  land on a stable ObjectLike. Identity stays stable within the call. */
    JsArray getArgumentsObject() {
        if (argumentsObject == null) {
            List<Object> list = new ArrayList<>(callArgs.length);
            Collections.addAll(list, callArgs);
            argumentsObject = new JsArray(list);
        }
        return argumentsObject;
    }

    /** Apply TDZ check + JsLazy-unwrap to a resolved Slot. Shared between
     *  {@link #get} and {@link Interpreter#evalRefExpr}'s single-walk path. */
    Object readSlot(BindingSlot s, String key) {
        if (s.scope != null && !s.initialized) {
            throw JsErrorException.referenceError("cannot access '" + key + "' before initialization");
        }
        Object v = s.value;
        return v instanceof JsLazy lz ? lz.get() : v;
    }

    boolean hasKey(String key) {
        if ("this".equals(key)) {
            return true;
        }
        if (callArgs != null && "arguments".equals(key) && argumentsForRead() != null) {
            return true;
        }
        if (frameTable != null) {
            int idx = frameTable.indexOf(key);
            if (idx >= 0 && frame[idx] != SlotTable.UNDECLARED) {
                return true;
            }
        }
        return resolve(key) != null;
    }

    /** Sentinel for {@link #getOrNotFound}: the name is not bound anywhere
     *  in the lexical chain. Distinct from a binding whose value is
     *  undefined/null. */
    static final Object NOT_FOUND = new Object();

    /** Single-pass fusion of {@link #hasKey} + {@link #get} for the
     *  name-keyed identifier read path — one lexical-chain resolution
     *  instead of two. Returns {@link #NOT_FOUND} when the name is unbound;
     *  TDZ and {@link JsLazy} behave exactly as in {@link #get}. */
    Object getOrNotFound(String key) {
        if ("this".equals(key)) {
            return thisObject;
        }
        if (callArgs != null && "arguments".equals(key)) {
            JsArray a = argumentsForRead();
            if (a != null) {
                return a;
            }
            // arrow frame with no enclosing function — ordinary resolution
        }
        if (frameTable != null) {
            int idx = frameTable.indexOf(key);
            if (idx >= 0) {
                Object v = frame[idx];
                if (v != SlotTable.UNDECLARED) {
                    if (v == SlotTable.TDZ) {
                        throw JsErrorException.referenceError("cannot access '" + key + "' before initialization");
                    }
                    return v;
                }
                // undeclared: fall through — pre-declaration the store lacks
                // the name too, so the legacy chain is the same resolution
            }
        }
        BindingSlot s = resolve(key);
        if (s == null) {
            return NOT_FOUND;
        }
        return readSlot(s, key);
    }

    void put(String key, Object value) {
        declare(key, value, null, true);
    }

    void declare(String key, Object value, BindScope scope, boolean initialized) {
        if (value instanceof JsFunction fn && (fn.name == null || fn.name.isEmpty())) {
            // ES6 name inference: assign `fn.name = key` only when the function is
            // anonymous. Named function declarations (and references to them bound to
            // other keys/parameters) must keep their original .name per spec.
            fn.name = key;
        }
        // Frame local: var-kind declares (params, var, fn hoisting, for-of
        // bindings) and body-top-level let/const write the slot. Analyzer
        // invariants make the level logic below degenerate to a plain write
        // for these names: they are unshadowed (any second declaration of the
        // name forces STORE), so the redeclaration error and loop-scope cases
        // cannot arise, and function contexts have depth > 0 so the evalId
        // stamping never applies.
        if (frameTable != null) {
            int idx = frameTable.indexOf(key);
            if (idx >= 0) {
                frame[idx] = scope == null || initialized ? value : SlotTable.TDZ;
                return;
            }
        }
        if (scope != null) { // let or const
            BindingSlot existing = bindings == null ? null : bindings.getSlot(key);
            if (existing != null && existing.scope != null && existing.level == currentLevel) {
                ContextScope currentScope = getCurrentScope();
                if (currentScope == ContextScope.LOOP_INIT || currentScope == ContextScope.LOOP_BODY) {
                    // Loop iteration: re-declaration is valid (per-iteration scope).
                    // Fall through to pushBinding which shadows the existing slot.
                } else if (depth == 0 && existing.evalId != root.evalId) {
                    // Cross-eval re-declaration at top level (REPL semantics).
                    existing.value = value;
                    existing.scope = scope;
                    existing.initialized = initialized;
                    existing.evalId = root.evalId;
                    return;
                } else {
                    throw JsErrorException.syntaxError("identifier '" + key + "' has already been declared");
                }
            }
            if (bindings == null) {
                bindings = new BindingsStore();
            }
            bindings.pushBinding(key, value, scope, currentLevel, initialized);
            if (depth == 0) {
                // Stamp evalId on top-level let/const so cross-eval (REPL)
                // re-declaration semantics above can detect "this was
                // declared in a previous eval, so re-declaring is allowed."
                bindings.getSlot(key).evalId = root.evalId;
            }
        } else { // hoist var to function level
            int functionLevel = findFunctionLevel();
            if (bindings == null) {
                bindings = new BindingsStore();
            }
            BindingSlot existing = bindings.getSlot(key);
            if (existing != null && existing.level <= functionLevel) {
                existing.value = value;
            } else {
                bindings.pushBinding(key, value, null, functionLevel);
            }
        }
    }

    void update(String key, Object value) {
        update(key, value, null);
    }

    void update(String key, Object value, Node node) {
        if (frameTable != null) {
            int idx = frameTable.indexOf(key);
            if (idx >= 0 && frame[idx] != SlotTable.UNDECLARED) {
                updateSlot(idx, value, node);
                return;
            }
            // undeclared: fall through — a pre-declaration write resolves an
            // outer/global binding (or creates an implicit global), as today
        }
        BindingSlot s = resolve(key);
        if (s == null) {
            if (strict) {
                // Strict mode forbids the sloppy implicit-global creation:
                // assigning to an unresolvable name is a ReferenceError.
                throw JsErrorException.referenceError(key + " is not defined");
            }
            assignImplicitGlobal(key, value, node);
            return;
        }
        if (s.scope == BindScope.CONST && s.initialized) {
            throw JsErrorException.typeError("assignment to constant: " + key);
        }
        // NamedEvaluation (§13.15.2): when RHS is an anonymous function expression and
        // LHS is an IdentifierRef, set fn.name from the identifier. Mirrors the
        // declare-path hook above. Skipped for already-named functions so e.g.
        // `g = someNamedFn` does not clobber the original name.
        if (value instanceof JsFunction fn && (fn.name == null || fn.name.isEmpty())) {
            fn.name = key;
        }
        Object oldValue = s.value;
        s.initialized = true;
        // Unified write — works whether the Slot lives in this context's
        // bindings, in capturedBindings, in an outer context, or in root.
        // Sibling closures sharing the same Slot reference see the new
        // value immediately.
        s.value = value;
        if (root.listener != null) {
            root.listener.onBind(BindEvent.assign(key, value, oldValue, this, node));
        }
    }

    /** Slot analogue of the post-resolve half of {@link #update} — same const
     *  check, same name inference, same BindEvent. A TDZ slot behaves like the
     *  store's uninitialized binding: the write initializes (const included),
     *  and the event reports undefined as the old value, which is what the
     *  store held. Callers must have checked the slot is not UNDECLARED. */
    void updateSlot(int idx, Object value, Node node) {
        Object oldValue = frame[idx];
        if (oldValue == SlotTable.TDZ) {
            oldValue = Terms.UNDEFINED;
        } else if (frameTable.kinds[idx] == SlotTable.KIND_CONST) {
            throw JsErrorException.typeError("assignment to constant: " + frameTable.names[idx]);
        }
        if (value instanceof JsFunction fn && (fn.name == null || fn.name.isEmpty())) {
            fn.name = frameTable.names[idx];
        }
        frame[idx] = value;
        if (root.listener != null) {
            root.listener.onBind(BindEvent.assign(frameTable.names[idx], value, oldValue, this, node));
        }
    }

    private void assignImplicitGlobal(String key, Object value, Node node) {
        // ES6 non-strict implicit global: writes go straight to the engine's
        // single shared Bindings (root and script context point at the same
        // instance). No parent walk needed.
        root.bindings.putMember(key, value, null, true);
        if (root.listener != null) {
            root.listener.onBind(BindEvent.declare(key, value, BindScope.VAR, this, node));
        }
    }

    //==================================================================================================================
    //
    int iteration = -1;

    private ExitType exitType;
    private Object returnValue;
    private Object errorThrown;
    // The label of a `break foo` / `continue foo`, null for the unlabelled forms. Only a
    // BREAK or CONTINUE can carry one; every other completion clears it.
    private String exitLabel;
    // Provenance of a THROW completion: true only for a `throw` statement the author wrote, so the
    // host can tell a rulebook's deliberate refusal from an error the engine raised. Every other
    // path into stopAndThrow leaves it false.
    private boolean errorAuthored;
    private int errorAuthoredLine;

    Object stopAndBreak(String label) {
        exitType = ExitType.BREAK;
        exitLabel = label;
        returnValue = null;
        errorThrown = null;
        errorAuthored = false;
        errorAuthoredLine = 0;
        return null;
    }

    Object stopAndThrow(Object error) {
        exitType = ExitType.THROW;
        exitLabel = null;
        returnValue = null;
        errorThrown = error;
        errorAuthored = false;
        errorAuthoredLine = 0;
        return error;
    }

    /** {@link #stopAndThrow} from an authored {@code throw} statement at {@code line} (1-based). */
    Object stopAndThrowAuthored(Object error, int line) {
        stopAndThrow(error);
        errorAuthored = true;
        errorAuthoredLine = line;
        return error;
    }

    Object stopAndReturn(Object value) {
        exitType = ExitType.RETURN;
        exitLabel = null;
        returnValue = value;
        errorThrown = null;
        errorAuthored = false;
        errorAuthoredLine = 0;
        return value;
    }

    Object stopAndContinue(String label) {
        exitType = ExitType.CONTINUE;
        exitLabel = label;
        returnValue = null;
        errorThrown = null;
        errorAuthored = false;
        errorAuthoredLine = 0;
        return null;
    }

    boolean isStopped() {
        return exitType != null;
    }

    boolean isContinuing() {
        return exitType == ExitType.CONTINUE;
    }

    boolean isBreaking() {
        return exitType == ExitType.BREAK;
    }

    void reset() {
        exitType = null;
        exitLabel = null;
        returnValue = null;
        errorThrown = null;
        errorAuthored = false;
        errorAuthoredLine = 0;
    }

    /**
     * Put back a completion saved before a {@code finally} block was evaluated — see
     * {@link Interpreter} {@code evalTryStmt}. Deliberately restores the exit <i>type</i> rather
     * than re-deriving it from the values: {@code return null} and falling off the end carry the
     * same null return value and are different completions, and BREAK and CONTINUE carry no value
     * to derive anything from at all.
     */
    void restoreCompletion(ExitType savedExit, String savedLabel, Object savedReturn, Object savedError,
                           boolean savedAuthored, int savedAuthoredLine) {
        exitType = savedExit;
        exitLabel = savedLabel;
        returnValue = savedReturn;
        errorThrown = savedError;
        errorAuthored = savedAuthored;
        errorAuthoredLine = savedAuthoredLine;
    }

    boolean isError() {
        return exitType == ExitType.THROW;
    }

    public ExitType getExitType() {
        return exitType;
    }

    String getExitLabel() {
        return exitLabel;
    }

    @Override
    public Object getReturnValue() {
        return returnValue;
    }

    @Override
    public Object getErrorThrown() {
        return errorThrown;
    }

    boolean isErrorAuthored() {
        return errorAuthored;
    }

    int getErrorAuthoredLine() {
        return errorAuthoredLine;
    }

    void updateFrom(CoreContext childContext) {
        exitType = childContext.exitType;
        exitLabel = childContext.exitLabel;
        errorThrown = childContext.errorThrown;
        errorAuthored = childContext.errorAuthored;
        errorAuthoredLine = childContext.errorAuthoredLine;
        returnValue = childContext.returnValue;
    }

}
