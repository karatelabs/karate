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

class CoreContext implements Context {

    ContextRoot root;

    Object thisObject = Terms.UNDEFINED;
    CallInfo callInfo;

    Bindings _bindings;

    // Function context fields (non-null indicates this is a function context)
    final Object[] callArgs;
    final CoreContext closureContext;

    // Scope management (level-keyed bindings)
    int currentLevel = 0;
    List<ScopeEntry> scopeStack; // Lazy - created on first enterScope

    // Captured bindings for closures (references to BindValues from function creation time)
    final Map<String, BindValue> capturedBindings;


    CoreContext(ContextRoot root, CoreContext parent, int depth, Node node, ContextScope scope, Map<String, Object> bindings) {
        this.root = root;
        this.parent = parent;
        this.depth = depth;
        this.node = node;
        this.scope = scope;
        this.callArgs = null;
        this.closureContext = null;
        this.capturedBindings = null;
        if (bindings != null) {
            this._bindings = bindings instanceof Bindings b ? b : new Bindings(bindings);
        }
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
                CoreContext closureContext, Map<String, BindValue> captured) {
        this.root = parent.root;
        this.parent = parent;
        this.depth = parent.depth + 1;
        this.node = node;
        this.scope = ContextScope.FUNCTION;
        this.callArgs = functionArgs;
        this.closureContext = closureContext;
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
            if (_bindings != null) {
                _bindings.popLevel(currentLevel);
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

    //==================================================================================================================
    //
    Object get(String key) {
        if ("this".equals(key)) {
            return thisObject;
        }
        // Function context: handle "arguments" keyword
        if (callArgs != null && "arguments".equals(key)) {
            return Arrays.asList(callArgs);
        }
        // Check local bindings first (local declarations shadow captured closures)
        if (_bindings != null) {
            Object result = _bindings.getMember(key);
            if (result != null || _bindings.hasMember(key)) {
                BindValue bv = findConstOrLet(key);
                if (bv != null && !bv.initialized) {
                    throw JsErrorException.referenceError("cannot access '" + key + "' before initialization");
                }
                if (result instanceof Supplier<?> supplier) {
                    return supplier.get();
                }
                return result;
            }
        }
        // Check captured bindings (closure values from function definition time)
        if (capturedBindings != null && capturedBindings.containsKey(key)) {
            return capturedBindings.get(key).value;
        }
        // Function contexts use lexical scoping - walk closureContext, NOT parent
        // (parent here is the caller's context, which would give dynamic scoping).
        // Non-function contexts (block/loop scopes inside a function) use parent.
        // Issue #2802: a caller's parameter name was shadowing the callee's
        // closure-captured `var`/parameter of the same name.
        if (closureContext != null) {
            if (closureContext.hasKey(key)) {
                return closureContext.get(key);
            }
        } else if (parent != null && parent.hasKey(key)) {
            return parent.get(key);
        }
        if (root.hasKey(key)) {
            return root.get(key);
        }
        return Terms.UNDEFINED;
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
            BindValue existing = findConstOrLetAtCurrentLevel(key);
            if (existing != null) {
                ContextScope currentScope = getCurrentScope();
                if (currentScope == ContextScope.LOOP_INIT || currentScope == ContextScope.LOOP_BODY) {
                    // Loop iteration: re-declaration is valid (per-iteration scope)
                    // Push a new binding that shadows the captured one
                } else if (depth == 0 && existing.evalId != root.evalId) {
                    // Cross-eval re-declaration at top level (REPL semantics)
                    existing.value = value;
                    existing.scope = scope;
                    existing.initialized = initialized;
                    existing.evalId = root.evalId;
                    return;
                } else {
                    throw JsErrorException.syntaxError("identifier '" + key + "' has already been declared");
                }
            }
            pushBinding(key, value, scope, initialized);
            if (depth == 0) {
                // Stamp evalId on top-level let/const so cross-eval (REPL)
                // re-declaration semantics in declare() can detect "this was
                // declared in a previous eval, so re-declaring is allowed."
                _bindings.getBindValue(key).evalId = root.evalId;
            }
        } else { // hoist var to function level
            int functionLevel = findFunctionLevel();
            if (_bindings == null) {
                _bindings = new Bindings();
            }
            // Check if var already exists at or below function level
            BindValue existing = _bindings.getBindValue(key);
            if (existing != null && existing.level <= functionLevel) {
                // Update existing var
                existing.value = value;
            } else {
                // Push new var at function level
                _bindings.pushBinding(key, value, null, functionLevel);
            }
        }
    }

    private BindValue findConstOrLetAtCurrentLevel(String key) {
        if (_bindings != null) {
            BindValue bv = _bindings.getBindValue(key);
            if (bv != null && bv.scope != null && bv.level == currentLevel) {
                return bv;
            }
        }
        return null;
    }

    void update(String key, Object value) {
        update(key, value, null);
    }

    void update(String key, Object value, Node node) {
        if (_bindings != null && _bindings.hasMember(key)) {
            BindValue bv = findConstOrLet(key);
            Object oldValue = _bindings.getMember(key);
            if (bv != null) {
                if (bv.scope == BindScope.CONST && bv.initialized) {
                    throw JsErrorException.typeError("assignment to constant: " + key);
                }
                bv.initialized = true;
            }
            _bindings.putMember(key, value);
            if (root.listener != null) {
                root.listener.onBind(BindEvent.assign(key, value, oldValue, this, node));
            }
        } else if (closureContext != null) {
            // Function context: assignment to outer-scope vars must follow
            // the lexical chain, not the dynamic caller chain (issue #2802).
            if (closureContext.hasKey(key)) {
                closureContext.update(key, value, node);
            } else {
                assignImplicitGlobal(key, value, node);
            }
        } else if (parent != null && parent.hasKey(key)) {
            parent.update(key, value, node);
        } else {
            assignImplicitGlobal(key, value, node);
        }
    }

    private void assignImplicitGlobal(String key, Object value, Node node) {
        // ES6 non-strict implicit global: writes go straight to the engine's
        // single shared Bindings (root and script context point at the same
        // instance). No parent walk needed.
        root._bindings.putMember(key, value, null, true);
        if (root.listener != null) {
            root.listener.onBind(BindEvent.declare(key, value, BindScope.VAR, this, node));
        }
    }

    private void putBinding(String key, Object value, BindScope scope, boolean initialized) {
        if (_bindings == null) {
            _bindings = new Bindings();
        }
        _bindings.putMember(key, value, scope, initialized);
    }

    private void pushBinding(String key, Object value, BindScope scope, boolean initialized) {
        if (_bindings == null) {
            _bindings = new Bindings();
        }
        _bindings.pushBinding(key, value, scope, currentLevel, initialized);
    }

    boolean hasKey(String key) {
        if ("this".equals(key)) {
            return true;
        }
        // Function context: handle "arguments" keyword
        if (callArgs != null && "arguments".equals(key)) {
            return true;
        }
        // Check local bindings first (local declarations shadow captured closures)
        if (_bindings != null && _bindings.hasMember(key)) {
            return true;
        }
        // Check captured bindings (closure values from function definition time)
        if (capturedBindings != null && capturedBindings.containsKey(key)) {
            return true;
        }
        // Function contexts use lexical scoping - mirror get() above.
        if (closureContext != null) {
            if (closureContext.hasKey(key)) {
                return true;
            }
        } else if (parent != null && parent.hasKey(key)) {
            return true;
        }
        return root.hasKey(key);
    }

    private BindValue findConstOrLet(String key) {
        if (_bindings != null) {
            BindValue bv = _bindings.getBindValue(key);
            if (bv != null && bv.scope != null) {
                return bv;
            }
        }
        return null;
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
