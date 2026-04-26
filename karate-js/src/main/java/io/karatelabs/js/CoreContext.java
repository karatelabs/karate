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
import java.util.function.Supplier;

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

    BindingsStore bindings;

    // Function context fields (non-null indicates this is a function context)
    final Object[] callArgs;
    final CoreContext outer;

    // Scope management (level-keyed bindings)
    int currentLevel = 0;
    List<ScopeEntry> scopeStack; // Lazy - created on first enterScope

    // Captured bindings for closures (references to Slots from function creation time).
    // Stored as an immutable BindingsStore so resolve() walks one chain shape; structural
    // mutation through the captured handle is a no-op (sibling closures still see value
    // updates through the existing-key fast path).
    final BindingsStore capturedBindings;


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
    // otherwise — see issue #2802) → root (with lazy built-in init).
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
        // function) have outer == null and walk parent. Issue #2802: a
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
            return Arrays.asList(callArgs);
        }
        BindingSlot s = resolve(key);
        if (s == null) {
            return Terms.UNDEFINED;
        }
        return readSlot(s, key);
    }

    /** Apply TDZ check + Supplier-unwrap to a resolved Slot. Shared between
     *  {@link #get} and {@link Interpreter#evalRefExpr}'s single-walk path. */
    Object readSlot(BindingSlot s, String key) {
        if (s.scope != null && !s.initialized) {
            throw JsErrorException.referenceError("cannot access '" + key + "' before initialization");
        }
        Object v = s.value;
        return v instanceof Supplier<?> supplier ? supplier.get() : v;
    }

    boolean hasKey(String key) {
        if ("this".equals(key)) {
            return true;
        }
        if (callArgs != null && "arguments".equals(key)) {
            return true;
        }
        return resolve(key) != null;
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
        BindingSlot s = resolve(key);
        if (s == null) {
            assignImplicitGlobal(key, value, node);
            return;
        }
        if (s.scope == BindScope.CONST && s.initialized) {
            throw JsErrorException.typeError("assignment to constant: " + key);
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

    Object stopAndBreak() {
        exitType = ExitType.BREAK;
        returnValue = null;
        errorThrown = null;
        return null;
    }

    Object stopAndThrow(Object error) {
        exitType = ExitType.THROW;
        returnValue = null;
        errorThrown = error;
        return error;
    }

    Object stopAndReturn(Object value) {
        exitType = ExitType.RETURN;
        returnValue = value;
        errorThrown = null;
        return value;
    }

    Object stopAndContinue() {
        exitType = ExitType.CONTINUE;
        returnValue = null;
        errorThrown = null;
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
        returnValue = null;
        errorThrown = null;
    }

    boolean isError() {
        return exitType == ExitType.THROW;
    }

    public ExitType getExitType() {
        return exitType;
    }

    @Override
    public Object getReturnValue() {
        return returnValue;
    }

    @Override
    public Object getErrorThrown() {
        return errorThrown;
    }

    void updateFrom(CoreContext childContext) {
        exitType = childContext.exitType;
        errorThrown = childContext.errorThrown;
        returnValue = childContext.returnValue;
    }

}
