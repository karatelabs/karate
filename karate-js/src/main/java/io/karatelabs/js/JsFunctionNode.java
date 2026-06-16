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

    final boolean arrow;
    final Node node;
    final Node body; // STATEMENT or BLOCK (that may return expr)
    final List<Node> argNodes;
    final int argCount;
    final CoreContext declaredContext;
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

    static final class FieldInit {
        final String key;
        final Node initializer; // EXPR node, or null for an uninitialized field

        FieldInit(String key, Node initializer) {
            this.key = key;
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
        this.node = node;
        this.argNodes = argNodes;
        this.argCount = argNodes.size();
        this.body = body;
        this.declaredContext = declaredContext;
        this.strict = forceStrict
                || (declaredContext != null && declaredContext.strict)
                || (body.type == NodeType.BLOCK && Interpreter.hasUseStrictDirective(body));
        // Spec §15.2: f.length is the number of formal parameters before the
        // first one with a default value or a rest element. Approximating with
        // argCount is correct for the common case (no defaults, no rest) and
        // off-by-N for the rest — refine if test262 surfaces it.
        this.length = argCount;
        // Capture references to let/const Slots at creation time for closure semantics
        this.capturedBindings = captureBindings(declaredContext);
    }

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
        if (functionContext.isError()) {
            EngineException ex = Interpreter.errorAsException(functionContext, node);
            if (parentContext != null) {
                parentContext.reset();
            }
            throw ex;
        }
        return result;
    }

    // Called by Interpreter when context is pre-prepared with closure info
    Object bindArgsAndExecute(CoreContext functionContext, CoreContext parentContext, Object[] args) {
        for (int i = 0; i < argCount; i++) {
            Node argNode = argNodes.get(i);
            Node first = argNode.getFirst();
            if (first.getFirstToken().type == TokenType.DOT_DOT_DOT) { // varargs
                List<Object> remainingArgs = new ArrayList<>();
                for (int j = i; j < args.length; j++) {
                    remainingArgs.add(args[j]);
                }
                String argName = argNode.getLast().getText();
                functionContext.put(argName, remainingArgs);
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
                functionContext.put(argName, argValue);
            }
        }
        Object result = Interpreter.eval(body, functionContext);
        // exit function, only propagate error
        if (functionContext.isError()) {
            parentContext.updateFrom(functionContext);
        }
        return body.type == NodeType.BLOCK ? functionContext.getReturnValue() : result;
    }

    @Override
    public String getSource() {
        return node.getTextIncludingWhitespace();
    }

    @Override
    public boolean isConstructable() {
        return !arrow;
    }

}
