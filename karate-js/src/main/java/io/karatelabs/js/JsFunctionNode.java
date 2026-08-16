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

import io.karatelabs.parser.Node;
import io.karatelabs.parser.NodeType;
import io.karatelabs.parser.TokenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class JsFunctionNode extends JsFunction {

    static final Logger logger = LoggerFactory.getLogger(JsFunctionNode.class);

    static {
        // Build one throwaway RangeError now. The recursion guard in
        // bindArgsAndExecute builds one on a thread whose stack is already
        // exhausted, and a class initializer that fails there is fatal for the
        // life of the JVM (every later touch is a NoClassDefFoundError). This
        // class loads with the first function definition, long before any deep
        // recursion, so the error path is warm by the time it is needed.
        JsErrorException.rangeError("");
    }

    final boolean arrow;
    // True for an `async` function / method / arrow, taken from the defining
    // node's parse-time marker. An async invocation never runs its body on the
    // calling thread: it spawns an activation and returns a promise.
    final boolean async;
    final Node node;
    final Node body; // STATEMENT or BLOCK (that may return expr)
    final List<Node> argNodes;
    final int argCount;
    final CoreContext declaredContext;
    // The private environment in effect where this function was created, captured
    // eagerly: declaredContext is a live context whose privateEnv is restored once
    // the enclosing class body finishes, so reading it at call time would be too late.
    final PrivateEnv privateEnv;
    final BindingsStore capturedBindings; // References to Slots at creation time, frozen-shape
    // Strict-mode is lexical: a function is strict if it carries its own
    // "use strict" prologue OR it was defined inside already-strict code
    // (declaredContext.strict). Resolved once at creation; the call frame
    // copies it onto CoreContext.strict.
    final boolean strict;
    // True for the synthesized constructor of a `class` — class constructors
    // are not callable without `new` (spec §15.7.14). Set by Interpreter at
    // class-eval time; default false for ordinary functions.
    boolean isClassConstructor;
    // True for a `class X extends Y` constructor — a `super(...)` call inside it
    // runs Y's constructor against the instance under construction.
    boolean isDerivedConstructor;
    // True when X extends Y but declares no constructor — the implicit
    // `constructor(...args) { super(...args); }`; the super-forward runs at
    // construction time (the synthesized body is empty).
    boolean isDefaultDerivedConstructor;
    // [[HomeObject]] for `super.member` resolution: the class prototype for an
    // instance method/constructor, or the constructor for a static method.
    // null for ordinary (non-class) functions.
    ObjectLike homeObject;
    // Public instance fields declared on the class, in source order. Run on each
    // new instance at construction (base class: before the constructor body;
    // derived: right after super() returns). Computed field names are resolved
    // once at class-definition time, so the key is a plain String. null when the
    // class declares no instance fields.
    List<FieldInit> instanceFields;
    // Private instance methods / accessors declared on the class. Their brand is
    // stamped onto each new instance before the field initializers run, so a
    // private method is callable from one.
    List<PrivateName> privateBrands;

    static final class FieldInit {
        final String key;         // null for a private field
        final PrivateName privateName; // null for a public field
        final Node initializer;   // EXPR node, or null for an uninitialized field

        FieldInit(String key, PrivateName privateName, Node initializer) {
            this.key = key;
            this.privateName = privateName;
            this.initializer = initializer;
        }
    }

    public JsFunctionNode(boolean arrow, Node node, List<Node> argNodes, Node body, CoreContext declaredContext) {
        this(arrow, node, argNodes, body, declaredContext, false);
    }

    // forceStrict overload — class bodies are always strict regardless of any
    // surrounding "use strict" directive (spec §15.7).
    JsFunctionNode(boolean arrow, Node node, List<Node> argNodes, Node body, CoreContext declaredContext,
                   boolean forceStrict) {
        this.arrow = arrow;
        this.async = node.async;
        this.node = node;
        this.argNodes = argNodes;
        this.argCount = argNodes.size();
        this.body = body;
        this.declaredContext = declaredContext;
        this.privateEnv = declaredContext == null ? null : declaredContext.privateEnv;
        this.strict = forceStrict
                || (declaredContext != null && declaredContext.strict)
                || (body.type == NodeType.BLOCK && Interpreter.hasUseStrictDirective(body));
        this.length = expectedArgCount(argNodes);
        // Capture references to let/const Slots at creation time for closure semantics
        this.capturedBindings = captureBindings(declaredContext);
        // Slot-frame analysis: once per function-definition node (cached there),
        // null when the flag is off or the analyzer declines the shape. Eager
        // only for loop-containing bodies; loop-free bodies are analyzed at
        // this instance's second call (see bindArgsAndExecute) so functions
        // called once never pay for analysis they cannot amortize.
        this.slotTable = SlotTable.ENABLED ? SlotTable.forNodeEager(node, argNodes, body) : null;
    }

    /**
     * Spec §15.1.5 ExpectedArgumentCount — {@code f.length} counts the formal
     * parameters BEFORE the first one carrying an initializer or a rest
     * element, so {@code function f(a, b = 1) {}} and
     * {@code function g(a, ...r) {}} both report 1. The two shapes are the same
     * ones {@link #executeBody} branches on: a leading {@code ...} token, and
     * the {@code FN_DECL_ARG: [target, EQ, EXPR, COMMA?]} default form.
     */
    private static int expectedArgCount(List<Node> argNodes) {
        int count = 0;
        for (Node argNode : argNodes) {
            if (argNode.getFirst().getFirstToken().type == TokenType.DOT_DOT_DOT) {
                break;
            }
            if (argNode.type == NodeType.FN_DECL_ARG && argNode.size() >= 3
                    && argNode.get(1).isToken() && argNode.get(1).token.type == TokenType.EQ) {
                break;
            }
            count++;
        }
        return count;
    }

    SlotTable slotTable;
    // Calls on THIS function object; trips deferred analysis at the second
    // call. Racy under sharing — a lost increment only delays analysis by a
    // call, and slotTable is republished from the node cache either way.
    private int callCount;

    private static BindingsStore captureBindings(CoreContext context) {
        if (context.bindings == null) {
            return null;
        }
        Map<String, BindingSlot> snapshot = null;
        for (String key : context.bindings.keys()) {
            BindingSlot s = context.bindings.getSlot(key);
            if (s != null && s.scope != null) { // Only capture let/const bindings
                if (snapshot == null) {
                    snapshot = new HashMap<>(4); // Typically few captured vars
                }
                snapshot.put(key, s); // Store reference, not copy
            }
        }
        return snapshot == null ? null : BindingsStore.captured(snapshot);
    }

    @Override
    public Object call(Context callerContext, Object[] args) {
        final CoreContext parentContext;
        // A host (Java) caller invokes us directly with no JS caller context (null / a foreign,
        // non-CoreContext caller). There is no surrounding JS statement boundary to convert an
        // uncaught `throw` into an EngineException the way evalProgram does, so we apply that same
        // conversion here — otherwise `someFn.call(null, args)` silently swallows a JS throw.
        final boolean hostCall;
        if (callerContext instanceof CoreContext cc) {
            parentContext = cc;
            hostCall = false;
        } else {
            parentContext = declaredContext;
            hostCall = true;
        }
        // Create lightweight function context with captured bindings
        CoreContext functionContext = new CoreContext(parentContext, node, args, declaredContext, capturedBindings);
        functionContext.strict = strict;
        functionContext.activeFunction = arrow
                ? (declaredContext != null ? declaredContext.activeFunction : null)
                : this;
        if (arrow) {
            Interpreter.bindArrowThis(functionContext, this);
        }
        // Hosts may invoke a shared function directly (null / foreign caller
        // context, outside any Engine.eval). The body executes against the
        // declaring Engine's globals, so make that engine current for
        // singleton-overlay resolution. Common case (JS-to-JS call under the
        // same engine's eval) pays one ThreadLocal read and skips the switch.
        Engine declaringEngine = declaredContext == null ? null : declaredContext.getEngine();
        if (declaringEngine == null || declaringEngine == Engine.current()) {
            Object result = bindArgsAndExecute(functionContext, parentContext, args);
            return hostCall ? hostResult(functionContext, parentContext, result) : result;
        }
        Engine prevEngine = Engine.enter(declaringEngine);
        try {
            Object result = bindArgsAndExecute(functionContext, parentContext, args);
            return hostCall ? hostResult(functionContext, parentContext, result) : result;
        } finally {
            Engine.exit(prevEngine);
        }
    }

    /**
     * Host-invocation boundary: when a Java caller (no JS caller context) runs us and the body left
     * an uncaught JS throw, surface it as a Java {@link EngineException} — the same conversion
     * {@code engine.eval} applies at the statement boundary — instead of returning normally. The
     * error was already propagated onto {@code parentContext} (the declaring context) by
     * {@link #bindArgsAndExecute}; clear it so the shared declaring context is not left dirty for the
     * next host call. A clean (non-error) completion returns the value unchanged.
     */
    private Object hostResult(CoreContext functionContext, CoreContext parentContext, Object result) {
        if (async) {
            // the result is the promise; the body runs on its own thread and its
            // completion state belongs to that promise, never to this context
            return result;
        }
        if (functionContext.isError()) {
            EngineException ex = Interpreter.errorAsException(functionContext, node);
            if (parentContext != null) {
                parentContext.reset();
            }
            throw ex;
        }
        return result;
    }

    /**
     * Called by Interpreter when context is pre-prepared with closure info.
     * Every JS-to-JS call funnels through here — {@link #call} and the
     * Interpreter's inlined call/construct/super paths alike — which makes it
     * the one place to turn runaway recursion into a JS-catchable error.
     * <p>
     * A {@link StackOverflowError} is uncatchable from JS and surfaces as a raw
     * Java leak (principle #2); the spec answer is a {@code RangeError}. The
     * unwound-and-discarded {@code functionContext} carries no state anyone can
     * observe, so the resulting {@link JsErrorException} then propagates
     * exactly like any other JS throw.
     * <p>
     * A depth counter was the alternative and is worse here: no fixed ceiling
     * is portable — this repo's own surefire JVM blows the Java stack at ~450
     * nested JS frames while a default main thread takes far more — so a limit
     * safe there either fails to protect or rejects legitimate recursion.
     * Catching costs nothing on the hot path (an exception-table entry, no
     * instructions) and is self-correcting on the cold one: if building the
     * error exhausts what little stack is left, that second overflow lands in
     * the caller's identical catch one JS frame out, where there is more room.
     */
    Object bindArgsAndExecute(CoreContext functionContext, CoreContext parentContext, Object[] args) {
        functionContext.privateEnv = privateEnv;
        if (async) {
            // Argument binding is part of the activation's startup, so it runs on
            // the activation thread under the startup-outcome protocol — not here.
            return AsyncSupport.callAsync(this, functionContext, args);
        }
        try {
            return executeBody(functionContext, parentContext, args);
        } catch (StackOverflowError e) {
            throw JsErrorException.rangeError("Maximum call stack size exceeded");
        }
    }

    /** The synchronous body run. For an async function this is what the
     *  activation thread executes; the caller has already been handed a promise. */
    Object executeBody(CoreContext functionContext, CoreContext parentContext, Object[] args) {
        // Attach the slot frame here — the single choke point every call path
        // shares before params bind and defaults evaluate. For an async function
        // this runs on the activation thread, so the frame exists before the
        // body's first statement there too; the frame lives on the context and
        // follows it across await suspensions.
        SlotTable table = slotTable;
        if (table == null && SlotTable.ENABLED && ++callCount == 2) {
            table = SlotTable.forNodeForced(node, argNodes, body);
            slotTable = table;
        }
        if (table != null) {
            functionContext.frame = table.newFrame();
            functionContext.frameTable = table;
        }
        for (int i = 0; i < argCount; i++) {
            Node argNode = argNodes.get(i);
            Node first = argNode.getFirst();
            if (first.getFirstToken().type == TokenType.DOT_DOT_DOT) { // varargs
                List<Object> remainingArgs = new ArrayList<>();
                for (int j = i; j < args.length; j++) {
                    remainingArgs.add(args[j]);
                }
                Node restTarget = argNode.get(1);
                if (restTarget.type == NodeType.LIT_ARRAY || restTarget.type == NodeType.LIT_OBJECT) {
                    // `...[a, b]` / `...{length}` — the collected rest is destructured, so it
                    // has to be a real array (the pattern reads `length`, the iterator, …).
                    Interpreter.evalAssign(restTarget, functionContext, BindScope.VAR,
                            new JsArray(remainingArgs), true);
                } else {
                    functionContext.put(restTarget.getText(), remainingArgs);
                }
                continue;
            }
            // Resolve the passed value or fall back to UNDEFINED — the param-level
            // default (FN_DECL_ARG: [target, EQ, EXPR, COMMA?]) then fires on
            // UNDEFINED for both IDENT and destructuring-pattern params. The
            // trailing COMMA is appended by the parser for every arg but the last,
            // so look up EQ/EXPR by index rather than via getLast().
            Object argValue = i < args.length ? args[i] : Terms.UNDEFINED;
            if (argValue == Terms.UNDEFINED
                    && argNode.type == NodeType.FN_DECL_ARG
                    && argNode.size() >= 3
                    && argNode.get(1).isToken()
                    && argNode.get(1).token.type == TokenType.EQ
                    && argNode.get(2).type == NodeType.EXPR) {
                argValue = Interpreter.eval(argNode.get(2), functionContext);
            }
            if (first.type == NodeType.LIT_ARRAY || first.type == NodeType.LIT_OBJECT) {
                Interpreter.evalAssign(first, functionContext, BindScope.VAR, argValue, true);
            } else {
                String argName = first.getText();
                // `table`, never the slotTable field: a concurrent second call can
                // publish the field mid-invocation, and a slot read from it would
                // dereference a frame this invocation decided not to attach.
                int slot = table == null ? -1 : table.paramSlots[i];
                if (slot >= 0) {
                    // Same name inference put→declare would apply, then a plain
                    // slot write (params are function-level and unshadowed).
                    if (argValue instanceof JsFunction fn && (fn.name == null || fn.name.isEmpty())) {
                        fn.name = argName;
                    }
                    functionContext.frame[slot] = argValue;
                } else {
                    functionContext.put(argName, argValue);
                }
            }
        }
        Object result = Interpreter.eval(body, functionContext);
        // exit function, only propagate error
        if (functionContext.isError()) {
            parentContext.updateFrom(functionContext);
        }
        if (body.type != NodeType.BLOCK) {
            return result;
        }
        // falling off the end of a block body completes with undefined — only an
        // explicit `return null` yields null, so key off the exit type, not the value
        return functionContext.getExitType() == null ? Terms.UNDEFINED : functionContext.getReturnValue();
    }

    @Override
    public String getSource() {
        return node.getTextIncludingWhitespace();
    }

    @Override
    public boolean isConstructable() {
        // async functions are not constructors (spec §27.7.4) — `new f()` on one
        // is a TypeError from the construct paths, same as for an arrow
        return !arrow && !async;
    }

}
