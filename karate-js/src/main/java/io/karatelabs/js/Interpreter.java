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
import io.karatelabs.parser.Token;
import io.karatelabs.parser.TokenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static io.karatelabs.parser.TokenType.*;

class Interpreter {

    static final Logger logger = LoggerFactory.getLogger(Interpreter.class);

    private static List<Node> fnArgs(Node fnArgs) {
        List<Node> list = new ArrayList<>(fnArgs.size() - 2);
        for (int i = 0, n = fnArgs.size(); i < n; i++) {
            Node fnArg = fnArgs.get(i);
            if (fnArg.type != NodeType.FN_DECL_ARG) {
                continue;
            }
            list.add(fnArg);
        }
        return list;
    }

    private static Terms terms(Node node, CoreContext context) {
        return terms(eval(node.get(0), context), eval(node.get(2), context));
    }

    private static Terms terms(Object lhs, Object rhs) {
        return new Terms(lhs, rhs);
    }

    @SuppressWarnings("unchecked")
    static Object evalAssign(Node bindings, CoreContext context, BindScope bindScope, Object value, boolean initialized) {
        if (bindings.type == NodeType.LIT_ARRAY || bindings.type == NodeType.LIT_OBJECT) {
            destructurePattern(bindings, context, bindScope, value, initialized);
        } else if (bindings.isToken() && bindings.token.type == IDENT) {
            String name = bindings.getText();
            context.declare(name, value, toScope(bindScope), initialized);
            if (context.root.listener != null) {
                context.root.listener.onBind(BindEvent.declare(name, value, bindScope, context, bindings));
            }
        } else {
            List<Node> varNames = bindings.findChildren(IDENT);
            for (Node varName : varNames) {
                String name = varName.getText();
                context.declare(name, value, toScope(bindScope), initialized);
                if (context.root.listener != null) {
                    context.root.listener.onBind(BindEvent.declare(name, value, bindScope, context, bindings));
                }
            }
        }
        return value;
    }

    private static Object evalAssignExpr(Node node, CoreContext context) {
        Node lhs = node.get(0);
        TokenType operator = node.get(1).token.type;
        Object value = eval(node.get(2), context);
        if (operator == EQ) {
            if (lhs.type == NodeType.LIT_EXPR) {
                Node pattern = lhs.getFirst();
                if (pattern.type == NodeType.LIT_ARRAY || pattern.type == NodeType.LIT_OBJECT) {
                    destructurePattern(pattern, context, null, value, false);
                } else {
                    evalAssign(pattern, context, BindScope.VAR, value, true);
                }
            } else {
                PropertyAccess.set(lhs, context, value, node);
            }
            return value;
        }
        return PropertyAccess.compound(lhs, context, operator, value, node);
    }

    /**
     * Walk a destructuring pattern (LIT_ARRAY or LIT_OBJECT) and bind each leaf
     * target from the corresponding piece of `source`. When `bindScope` is
     * non-null the pattern is a declaration (`var/let/const [a] = ...`) and
     * leaves are declared; when null it is an assignment expression
     * (`[a] = ...`) and leaves are updated / applied via `PropertyAccess.set`.
     * Nested patterns recurse; default values fire only when the corresponding
     * source value is undefined.
     */
    @SuppressWarnings("unchecked")
    private static void destructurePattern(Node pattern, CoreContext context,
                                            BindScope bindScope, Object source, boolean initialized) {
        // Per spec 13.3.3.5: destructuring null/undefined throws TypeError.
        // ArrayBindingPattern calls GetIterator(value); ObjectBindingPattern
        // calls RequireObjectCoercible(value); both fail for null/undefined.
        if (source == null || source == Terms.UNDEFINED) {
            throw JsErrorException.typeError("cannot destructure " + source);
        }
        if (pattern.type == NodeType.LIT_ARRAY) {
            // Array destructuring per spec 13.3.3.5 calls GetIterator(value).
            // Pull through the unified iterator surface — IterUtils throws TypeError
            // for non-iterables (boolean, plain object without @@iterator, etc.).
            JsIterator iter = IterUtils.getIterator(source, context);
            int last = pattern.size() - 1;
            for (int i = 1; i < last; i++) {
                Node elem = pattern.get(i);
                Node first = elem.get(0);
                if (first.isToken() && first.token.type == DOT_DOT_DOT) {
                    JsArray rest = new JsArray();
                    while (iter.hasNext()) {
                        rest.list.add(iter.next());
                    }
                    bindTarget(elem.get(1), context, bindScope, rest, initialized);
                    break;
                } else if (first.isToken() && first.token.type == COMMA) {
                    if (iter.hasNext()) iter.next();
                } else {
                    Object v = Terms.UNDEFINED;
                    if (iter.hasNext()) {
                        Object temp = iter.next();
                        if (temp != Terms.UNDEFINED) {
                            v = temp;
                        }
                    }
                    bindTarget(first, context, bindScope, v, initialized);
                }
            }
        } else if (pattern.type == NodeType.LIT_OBJECT) {
            // Use ObjectLike.getMember for property reads — Map.get on JsObject
            // auto-unwraps UNDEFINED → null via Engine.toJava, which would make
            // a present-but-undefined property look like an absent one, and
            // suppress the default-value branch below.
            ObjectLike objSource = (source instanceof ObjectLike) ? (ObjectLike) source : null;
            Map<String, Object> map = (source instanceof Map<?, ?>) ? (Map<String, Object>) source : null;
            Set<String> consumed = new HashSet<>();
            int last = pattern.size() - 1;
            for (int i = 1; i < last; i++) {
                Node elem = pattern.get(i);
                Node keyNode = elem.getFirst();
                TokenType keyType = keyNode.token.type;
                if (keyType == DOT_DOT_DOT) {
                    Map<String, Object> rest = new LinkedHashMap<>();
                    if (map != null) {
                        for (Map.Entry<String, Object> e : map.entrySet()) {
                            if (!consumed.contains(e.getKey())) {
                                rest.put(e.getKey(), e.getValue());
                            }
                        }
                    }
                    bindTarget(elem.get(1), context, bindScope, new JsObject(rest), initialized);
                    break;
                }
                boolean computed = keyType == L_BRACKET;
                int afterKeyPos = computed ? 3 : 1;
                String key;
                if (computed) {
                    Object keyValue = evalExpr(elem.get(1), context);
                    key = Terms.toStringCoerce(keyValue, context);
                } else if (keyType == S_STRING || keyType == D_STRING) {
                    key = (String) Terms.literalValue(keyNode.token);
                } else {
                    key = keyNode.getText();
                }
                consumed.add(key);
                Object v = Terms.UNDEFINED;
                if (objSource != null) {
                    // getMember returns the raw slot value (UNDEFINED stays UNDEFINED,
                    // unlike Map.get which unwraps via Engine.toJava). But null from
                    // getMember is ambiguous: could be "present with explicit null"
                    // or "absent" — disambiguate via Map.containsKey on the own
                    // properties map so default fires only for true absence / undefined.
                    Object temp = objSource.getMember(key);
                    if (temp == Terms.UNDEFINED) {
                        // v stays UNDEFINED, default will fire
                    } else if (temp == null) {
                        if (map != null && map.containsKey(key)) v = null;
                        // else absent, v stays UNDEFINED, default fires
                    } else {
                        v = temp;
                    }
                } else if (map != null && map.containsKey(key)) {
                    Object temp = map.get(key);
                    if (temp != Terms.UNDEFINED) {
                        v = temp;
                    }
                }
                if (!computed && elem.size() < 3) {
                    // shorthand {foo}
                    bindLeaf(keyNode, key, context, bindScope, v, initialized);
                } else if (!computed && elem.size() == 3 && elem.get(1).token.type == EQ) {
                    // shorthand with default {foo = default}
                    if (v == Terms.UNDEFINED) {
                        v = evalExpr(elem.get(2), context);
                    }
                    bindLeaf(keyNode, key, context, bindScope, v, initialized);
                } else {
                    // keyed: {foo: target} or {foo: target = default} or {[k]: target}
                    bindTarget(elem.get(afterKeyPos + 1), context, bindScope, v, initialized);
                }
            }
        }
    }

    /**
     * Bind `value` to one destructuring target node. The target may be an
     * IDENT reference, a property reference (o.x / o[i], assignment mode only),
     * a nested array / object pattern, or any of those wrapped in a
     * default-value `ASSIGN_EXPR` layer that fires only when `value` is
     * undefined.
     */
    private static void bindTarget(Node target, CoreContext context,
                                    BindScope bindScope, Object value, boolean initialized) {
        Node node = target;
        if (node.type == NodeType.EXPR) {
            node = node.getFirst();
        }
        if (node.type == NodeType.ASSIGN_EXPR) {
            Node defaultNode = node.getLast();
            node = node.getFirst();
            if (value == Terms.UNDEFINED) {
                value = evalExpr(defaultNode, context);
            }
        }
        Node inner = node;
        if (node.type == NodeType.LIT_EXPR) {
            inner = node.getFirst();
        }
        if (inner.type == NodeType.LIT_ARRAY || inner.type == NodeType.LIT_OBJECT) {
            destructurePattern(inner, context, bindScope, value, initialized);
        } else if (node.type == NodeType.REF_EXPR) {
            bindLeaf(node, node.getText(), context, bindScope, value, initialized);
        } else if (node.type == NodeType.REF_DOT_EXPR || node.type == NodeType.REF_BRACKET_EXPR) {
            PropertyAccess.set(node, context, value, null);
        } else if (node.isToken() && node.token.type == IDENT) {
            bindLeaf(node, node.getText(), context, bindScope, value, initialized);
        }
    }

    private static void bindLeaf(Node node, String name, CoreContext context,
                                  BindScope bindScope, Object value, boolean initialized) {
        if (bindScope != null) {
            context.declare(name, value, toScope(bindScope), initialized);
            if (context.root.listener != null) {
                context.root.listener.onBind(BindEvent.declare(name, value, bindScope, context, node));
            }
        } else {
            context.update(name, value, node);
        }
    }

    private static Object evalBlock(Node node, CoreContext context) {
        context.enterScope(ContextScope.BLOCK, node);
        context.event(EventType.CONTEXT_ENTER, node);
        // Hoist function declarations before any statement runs so earlier
        // statements can still reference them. Re-running the FN_EXPR in normal
        // flow would replace the hoisted binding with a fresh JsFunctionNode and
        // drop any property assignments made on the hoisted function (e.g.
        // foo.prototype = X before function foo(){}); the loop below skips them.
        hoistFunctionDeclarations(node, context);
        Object blockResult = null;
        try {
            for (int i = 0, n = node.size(); i < n; i++) {
                Node child = node.get(i);
                if (child.type == NodeType.STATEMENT) {
                    // Function decls produce an empty completion per spec; skip
                    // here (they were already pre-bound by hoistFunctionDeclarations
                    // above) so blockResult continues to track the previous value.
                    if (isFunctionDeclarationStatement(child)) {
                        continue;
                    }
                    blockResult = eval(child, context);
                    if (context.isStopped()) {
                        break;
                    }
                }
            }
        } finally {
            context.event(EventType.CONTEXT_EXIT, node);
            context.exitScope();
        }
        // errors would be handled by caller
        return context.isStopped() ? context.getReturnValue() : blockResult;
    }

    private static Object evalBreakStmt(Node node, CoreContext context) {
        return context.stopAndBreak();
    }

    private static Object evalContinueStmt(Node node, CoreContext context) {
        return context.stopAndContinue();
    }

    private static Object evalDeleteStmt(Node node, CoreContext context) {
        PropertyAccess.delete(node.get(1).getFirst(), context);
        return true;
    }

    private static Object evalExpr(Node node, CoreContext context) {
        node = node.getFirst();
        context.event(EventType.EXPRESSION_ENTER, node);
        try {
            Object result = eval(node, context);
            if (context.root.listener != null) {
                context.event(EventType.EXPRESSION_EXIT, node);
            }
            return result;
        } catch (Exception e) {
            if (context.root.listener != null) {
                Event event = new Event(EventType.EXPRESSION_EXIT, context, node);
                ExitResult exitResult = context.root.listener.onError(event, e);
                if (exitResult != null && exitResult.ignoreError) {
                    return exitResult.returnValue;
                }
            }
            throw e;
        }
    }

    private static Object evalExprList(Node node, CoreContext context) {
        Object result = null;
        for (int i = 0, n = node.size(); i < n; i++) {
            Node child = node.get(i);
            if (child.type == NodeType.EXPR) {
                result = eval(child, context);
            }
        }
        return result;
    }

    // Converts the SHORT_CIRCUITED sentinel back to UNDEFINED at the chain root.
    // Inner chain steps see SHORT_CIRCUITED and propagate; only the outermost step
    // in an optional chain — the chain root — surfaces UNDEFINED to the rest of the program.
    private static Object chainStepResult(Object result, Node node) {
        if (result == PropertyAccess.SHORT_CIRCUITED && PropertyAccess.isChainRoot(node)) {
            return Terms.UNDEFINED;
        }
        return result;
    }

    private static Object evalFnCall(Node node, CoreContext context, boolean newKeyword) {
        Node fnArgsNode;
        if (newKeyword) {
            node = node.getFirst();
            // check for new keyword with no parentheses for the constructor
            if (node.size() == 1) {
                fnArgsNode = null;
            } else {
                fnArgsNode = node.get(2);
                node = node.getFirst();
            }
        } else {
            fnArgsNode = node.get(2);
            node = node.getFirst();
        }
        Object[] callableAndReceiver = PropertyAccess.getCallable(node, context);
        Object o = callableAndReceiver[0];
        Object receiver = callableAndReceiver[1];
        if (o == PropertyAccess.SHORT_CIRCUITED) return PropertyAccess.SHORT_CIRCUITED;
        return invokeCallable(o, receiver, fnArgsNode, newKeyword, node, context);
    }

    // Handles `a?.(args)` shape: REF_DOT_EXPR[base, FN_CALL_EXPR[?., (, args, )]].
    // Per spec the chain head is the base (must be evaluated once); if nullish,
    // short-circuit the entire call without evaluating the args. Goes through
    // getCallable so a method-reference base (`a.b?.()`) keeps `a` as receiver.
    private static Object evalOptionalCall(Node node, CoreContext context) {
        Object[] callableAndReceiver = PropertyAccess.getCallable(node.getFirst(), context);
        Object o = callableAndReceiver[0];
        Object receiver = callableAndReceiver[1];
        if (o == PropertyAccess.SHORT_CIRCUITED) return PropertyAccess.SHORT_CIRCUITED;
        if (o == null || o == Terms.UNDEFINED) return PropertyAccess.SHORT_CIRCUITED;
        Node callExpr = node.get(1); // FN_CALL_EXPR -> [?., (, FN_CALL_ARGS, )]
        Node fnArgsNode = callExpr.get(2);
        return invokeCallable(o, receiver, fnArgsNode, false, node.getFirst(), context);
    }

    private static Object invokeCallable(Object o, Object receiver, Node fnArgsNode,
                                          boolean newKeyword, Node node, CoreContext context) {
        if (o instanceof JsCallable callable) {
            if (newKeyword && !callable.isConstructable()) {
                throw JsErrorException.typeError(node.toStringWithoutType() + " is not a constructor");
            }
            List<Object> argsList = new ArrayList<>();
            int argsCount = fnArgsNode == null ? 0 : fnArgsNode.size();
            for (int i = 0; i < argsCount; i++) {
                Node fnArgNode = fnArgsNode.get(i);
                Node argNode = fnArgNode.get(0);
                if (argNode.isToken()) { // DOT_DOT_DOT
                    Object arg = eval(fnArgNode.get(1), context);
                    JsIterator iter = IterUtils.getIterator(arg, context);
                    while (iter.hasNext()) {
                        argsList.add(iter.next());
                    }
                } else {
                    Object arg = eval(argNode, context);
                    argsList.add(arg);
                }
            }
            // Convert JS types to Java types if JS/Java boundary:
            // - undefined → null
            // - JsValue (JsDate, etc.) → unwrapped via getJavaValue()
            if (callable.isExternal()) {
                argsList.replaceAll(arg -> {
                    if (arg == Terms.UNDEFINED) return null;
                    // Unwrap JsValue (JsDate, JsUint8Array) but not JsPrimitive (Boolean/String/Number constructors)
                    if (arg instanceof JsValue jv && !(arg instanceof JsPrimitive)) return jv.getJavaValue();
                    return arg;
                });
            }
            Object[] args = argsList.toArray();
            CoreContext callContext;
            JsObject newInstance = null;
            Object result;
            // For user-defined functions, create full function context with closure info
            if (callable instanceof JsFunctionNode jsFunc) {
                callContext = new CoreContext(context, node, args, jsFunc.declaredContext, jsFunc.capturedBindings);
                if (newKeyword) {
                    callContext.callInfo = new CallInfo(true, callable);
                    newInstance = new JsObject();
                    Object proto = jsFunc.getMember("prototype");
                    if (proto instanceof ObjectLike protoObj) {
                        newInstance.setPrototype(protoObj);
                    }
                    callContext.thisObject = newInstance;
                } else if (!jsFunc.arrow) {
                    // Regular functions get their own 'this', arrow functions inherit from parent
                    callContext.thisObject = receiver == null ? callable : receiver;
                }
                callContext.event(EventType.CONTEXT_ENTER, node);
                // bindArgsAndExecute handles error propagation internally
                result = jsFunc.bindArgsAndExecute(callContext, context, args);
            } else {
                // For built-in callables, create function context with args for event tracking
                callContext = new CoreContext(context, node, args);
                if (newKeyword) {
                    callContext.callInfo = new CallInfo(true, callable);
                    // Built-in constructors handle their own construction
                    callContext.thisObject = callable;
                } else {
                    callContext.thisObject = receiver == null ? callable : receiver;
                }
                callContext.event(EventType.CONTEXT_ENTER, node);
                result = callable.call(callContext, args);
                // Propagate exit state from built-in calls
                context.updateFrom(callContext);
            }
            if (newKeyword && newInstance != null) {
                // Return the new instance unless constructor explicitly returns an object
                if (!(result instanceof ObjectLike)) {
                    result = newInstance;
                }
            }
            return result;
        } else {
            throw JsErrorException.typeError(node.toStringWithoutType() + " is not a function");
        }
    }

    // Handles NEW_EXPR dispatch. Most operand shapes go through evalFnCall's
    // newKeyword path. FN_TAGGED_TEMPLATE_EXPR is special: per spec the tagged
    // template is itself a MemberExpression whose evaluation is the function
    // result, and `new` applies to that result with no arguments.
    private static Object evalNewExpr(Node node, CoreContext context) {
        // NEW_EXPR -> [NEW, EXPR -> [<operand>]]; evalFnCall expects the EXPR wrapper.
        Node exprWrap = node.get(1);
        Node operand = exprWrap.getFirst();
        if (operand.type != NodeType.FN_TAGGED_TEMPLATE_EXPR) {
            return evalFnCall(exprWrap, context, true);
        }
        // Per spec, `new tag`x`` applies new to the result of the tagged template
        // invocation, not to the tag itself. Evaluate the tagged template, then
        // construct the result with no args. (`new tag`x`()` parses as FN_CALL_EXPR
        // wrapping FN_TAGGED_TEMPLATE_EXPR and goes through the default path above.)
        Object callable = evalFnTaggedTemplate(operand, context);
        if (context.isError()) {
            return Terms.UNDEFINED;
        }
        if (!(callable instanceof JsCallable c) || !c.isConstructable()) {
            throw JsErrorException.typeError(operand.toStringWithoutType() + " is not a constructor");
        }
        return invokeAsConstructor(c, new Object[0], operand, context);
    }

    /**
     * Construct {@code callable} as if invoked via {@code new}. Used by
     * {@code Reflect.construct} which has no syntactic Node to thread through;
     * the engine creates a synthetic placeholder so event tracing has something
     * to attach to. Throws TypeError if the target is not constructable.
     */
    static Object constructFromHost(JsCallable callable, Object[] args, CoreContext context) {
        if (!callable.isConstructable()) {
            throw JsErrorException.typeError("target is not a constructor");
        }
        return invokeAsConstructor(callable, args, new Node(NodeType.NEW_EXPR), context);
    }

    private static Object invokeAsConstructor(JsCallable callable, Object[] args, Node node, CoreContext context) {
        CoreContext callContext;
        JsObject newInstance = null;
        Object result;
        if (callable instanceof JsFunctionNode jsFunc) {
            callContext = new CoreContext(context, node, args, jsFunc.declaredContext, jsFunc.capturedBindings);
            callContext.callInfo = new CallInfo(true, callable);
            newInstance = new JsObject();
            Object proto = jsFunc.getMember("prototype");
            if (proto instanceof ObjectLike protoObj) {
                newInstance.setPrototype(protoObj);
            }
            callContext.thisObject = newInstance;
            callContext.event(EventType.CONTEXT_ENTER, node);
            result = jsFunc.bindArgsAndExecute(callContext, context, args);
        } else {
            callContext = new CoreContext(context, node, args);
            callContext.callInfo = new CallInfo(true, callable);
            callContext.thisObject = callable;
            callContext.event(EventType.CONTEXT_ENTER, node);
            result = callable.call(callContext, args);
            context.updateFrom(callContext);
        }
        if (newInstance != null && !(result instanceof ObjectLike)) {
            result = newInstance;
        }
        return result;
    }

    // Tagged template: tag`abc ${x} def` invokes tag(strings, x, ...) where
    // strings is a JsArray of the cooked string segments with a .raw property
    // holding a parallel array of the un-escaped source segments.
    // Node shape: FN_TAGGED_TEMPLATE_EXPR -> [<callable_expr>, LIT_TEMPLATE]
    private static Object evalFnTaggedTemplate(Node node, CoreContext context) {
        Node callableNode = node.get(0);
        Node tpl = node.get(1);
        // Walk LIT_TEMPLATE children to recover paired cooked/raw segments and
        // substitution expressions. For N expressions there are always N+1
        // string slots (possibly empty).
        JsArray cooked = new JsArray();
        JsArray raw = new JsArray();
        List<Object> substitutions = new ArrayList<>();
        StringBuilder cookedAccum = new StringBuilder();
        StringBuilder rawAccum = new StringBuilder();
        for (int i = 0, n = tpl.size(); i < n; i++) {
            Node child = tpl.get(i);
            if (child.isToken()) {
                TokenType tt = child.token.type;
                if (tt == T_STRING) {
                    String text = child.token.getText();
                    rawAccum.append(text);
                    cookedAccum.append(unescapeString(text));
                } else if (tt == DOLLAR_L_CURLY) {
                    cooked.add(cookedAccum.toString());
                    raw.add(rawAccum.toString());
                    cookedAccum.setLength(0);
                    rawAccum.setLength(0);
                }
                // BACKTICK, R_CURLY: ignore
            } else if (child.type == NodeType.EXPR) {
                Object value = eval(child, context);
                if (context.isError()) {
                    return Terms.UNDEFINED;
                }
                substitutions.add(value);
            }
        }
        // Final trailing segment (always present, possibly empty)
        cooked.add(cookedAccum.toString());
        raw.add(rawAccum.toString());
        cooked.putMember("raw", raw);

        Object[] callableAndReceiver = PropertyAccess.getCallable(callableNode, context);
        Object o = callableAndReceiver[0];
        Object receiver = callableAndReceiver[1];
        if (o == Terms.UNDEFINED) { // optional chaining
            return o;
        }
        if (!(o instanceof JsCallable callable)) {
            throw JsErrorException.typeError(callableNode.toStringWithoutType() + " is not a function");
        }
        Object[] args = new Object[substitutions.size() + 1];
        args[0] = cooked;
        for (int i = 0; i < substitutions.size(); i++) {
            args[i + 1] = substitutions.get(i);
        }
        if (callable.isExternal()) {
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (a == Terms.UNDEFINED) args[i] = null;
                else if (a instanceof JsValue jv && !(a instanceof JsPrimitive)) args[i] = jv.getJavaValue();
            }
        }
        CoreContext callContext;
        Object result;
        if (callable instanceof JsFunctionNode jsFunc) {
            callContext = new CoreContext(context, node, args, jsFunc.declaredContext, jsFunc.capturedBindings);
            if (!jsFunc.arrow) {
                callContext.thisObject = receiver == null ? callable : receiver;
            }
            callContext.event(EventType.CONTEXT_ENTER, node);
            result = jsFunc.bindArgsAndExecute(callContext, context, args);
        } else {
            callContext = new CoreContext(context, node, args);
            callContext.thisObject = receiver == null ? callable : receiver;
            callContext.event(EventType.CONTEXT_ENTER, node);
            result = callable.call(callContext, args);
            context.updateFrom(callContext);
        }
        return result;
    }

    // Pre-walk parent's STATEMENT children for function declarations
    // (FN_EXPR with leading `function` keyword + IDENT name) and bind each name
    // before any statement runs. Re-evaluation when the declaration is reached in
    // normal flow re-binds to a fresh JsFunctionNode with the same captures —
    // functionally equivalent, just a small extra allocation per declaration.
    static void hoistFunctionDeclarations(Node parent, CoreContext context) {
        for (int i = 0, n = parent.size(); i < n; i++) {
            Node child = parent.get(i);
            if (isFunctionDeclarationStatement(child)) {
                evalFnExpr(child.getFirst(), context);
            }
        }
    }

    /**
     * True if {@code stmt} is a {@code STATEMENT} wrapping a named {@code FN_EXPR}
     * (i.e., a {@code function name(){}} declaration at statement position). Used
     * by both the hoisting pre-pass and the main eval loop to skip re-evaluation
     * — running the FN_EXPR twice would replace the hoisted binding with a fresh
     * {@link JsFunctionNode}, dropping any property assignments made on the
     * hoisted function before its lexical position (e.g. {@code foo.prototype = X}
     * before {@code function foo(){}}).
     */
    private static boolean isFunctionDeclarationStatement(Node stmt) {
        if (stmt.type != NodeType.STATEMENT || stmt.size() == 0) {
            return false;
        }
        Node first = stmt.getFirst();
        if (first.type != NodeType.FN_EXPR || first.size() < 2) {
            return false;
        }
        Node second = first.get(1);
        return second.token != null && second.token.type == IDENT;
    }

    private static Object evalFnExpr(Node node, CoreContext context) {
        // Shorthand method syntax ({foo() {...}}) produces an FN_EXPR whose
        // first child is FN_DECL_ARGS (no leading `function` keyword / name).
        if (node.getFirst().type == NodeType.FN_DECL_ARGS) {
            return new JsFunctionNode(false, node, fnArgs(node.getFirst()), node.getLast(), context);
        }
        if (node.get(1).token.type == IDENT) {
            JsFunctionNode fn = new JsFunctionNode(false, node, fnArgs(node.get(2)), node.getLast(), context);
            context.put(node.get(1).getText(), fn);
            return fn;
        } else {
            return new JsFunctionNode(false, node, fnArgs(node.get(1)), node.getLast(), context);
        }
    }

    private static Object evalFnArrowExpr(Node node, CoreContext context) {
        List<Node> argNodes;
        if (node.getFirst().token.type == IDENT) {
            argNodes = Collections.singletonList(node);
        } else {
            argNodes = fnArgs(node.getFirst());
        }
        return new JsFunctionNode(true, node, argNodes, node.getLast(), context);
    }

    private static Object evalForStmt(Node node, CoreContext context) {
        // Enter loop init scope
        context.enterScope(ContextScope.LOOP_INIT, node);
        context.event(EventType.CONTEXT_ENTER, node);
        Node forBody = node.getLast();
        Object forResult = null;
        boolean enteredBodyScope = false;
        try {
            if (node.get(2).token.type == SEMI) {
                // rare case: "for(;;)"
            } else if (node.get(3).token.type == SEMI) {
                // C-style for loop: for(init; condition; increment)
                boolean isLetOrConst;
                BindScope loopVarScope = null;
                List<String> loopVarNames = null;
                if (node.get(2).type == NodeType.VAR_STMT) {
                    evalVarStmt(node.get(2), context);
                    isLetOrConst = node.get(2).getFirstToken().type != VAR;
                    if (isLetOrConst) {
                        // Collect all loop variable names across declarators for per-iteration capture
                        loopVarNames = new ArrayList<>();
                        for (Node declarator : node.get(2).findImmediateChildren(NodeType.VAR_DECL)) {
                            for (Node ident : declarator.findChildren(IDENT)) {
                                loopVarNames.add(ident.getText());
                            }
                        }
                        loopVarScope = node.get(2).getFirstToken().type == LET ? BindScope.LET : BindScope.CONST;
                    }
                } else {
                    isLetOrConst = false;
                    eval(node.get(2), context);
                }
                if (node.get(4).token.type == SEMI) {
                    // rare no-condition case: "for(init;;increment)"
                } else {
                    Node forAfter = node.get(6).token.type == R_PAREN ? null : node.get(6);
                    int index = -1;
                    while (true) {
                        index++;
                        context.iteration = index;
                        Object forCondition = eval(node.get(4), context);
                        if (Terms.isTruthy(forCondition)) {
                            if (isLetOrConst && loopVarNames != null && !loopVarNames.isEmpty()) {
                                // Snapshot all loop variables, then push fresh bindings for this iteration
                                List<Object> snapshot = new ArrayList<>(loopVarNames.size());
                                for (String name : loopVarNames) {
                                    snapshot.add(context.get(name));
                                }
                                context.enterScope(ContextScope.LOOP_BODY, forBody);
                                enteredBodyScope = true;
                                for (int k = 0; k < loopVarNames.size(); k++) {
                                    context.declare(loopVarNames.get(k), snapshot.get(k), loopVarScope, true);
                                }
                            }
                            forResult = eval(forBody, context);
                            if (isLetOrConst && enteredBodyScope) {
                                context.exitScope();
                                enteredBodyScope = false;
                            }
                            if (context.isStopped()) {
                                if (context.isContinuing()) {
                                    context.reset();
                                } else { // break, return or throw
                                    break;
                                }
                            }
                            if (forAfter != null) {
                                eval(forAfter, context);
                            }
                        } else {
                            break;
                        }
                    }
                }
            } else { // for in / of
                boolean in = node.get(3).token.type == IN;
                Object forObject = eval(node.get(4), context);
                BindScope bindScope;
                Node bindings;
                if (node.get(2).type == NodeType.VAR_STMT) {
                    // Unwrap the single VAR_DECL to its inner binding (IDENT | LIT_ARRAY | LIT_OBJECT).
                    // for-in/of spec disallows more than one declarator and disallows initializers.
                    Node declarator = node.get(2).get(1);
                    bindings = declarator.getFirst();
                    bindScope = switch (node.get(2).getFirstToken().type) {
                        case LET -> BindScope.LET;
                        case CONST -> BindScope.CONST;
                        default -> BindScope.VAR;
                    };
                } else {
                    bindScope = BindScope.VAR;
                    bindings = node.get(2);
                }
                boolean isLetOrConst = bindScope == BindScope.LET || bindScope == BindScope.CONST;
                // for-in keeps key-enumeration semantics (Object.keys-equivalent; null/undefined
                // sources are silently iterated zero times per spec).
                // for-of takes the spec iteration protocol: GetIterator(value) — TypeError on
                // null/undefined or any non-iterable, value-only enumeration.
                if (in) {
                    int index = -1;
                    for (KeyValue kv : Terms.toIterable(forObject)) {
                        index++;
                        context.iteration = index;
                        if (isLetOrConst) {
                            context.enterScope(ContextScope.LOOP_BODY, forBody);
                            enteredBodyScope = true;
                        }
                        evalAssign(bindings, context, bindScope, kv.key(), true);
                        forResult = eval(forBody, context);
                        if (isLetOrConst && enteredBodyScope) {
                            context.exitScope();
                            enteredBodyScope = false;
                        }
                        if (context.isStopped()) {
                            if (context.isContinuing()) {
                                context.reset();
                            } else {
                                break;
                            }
                        }
                    }
                } else {
                    JsIterator iter = IterUtils.getIterator(forObject, context);
                    int index = -1;
                    while (iter.hasNext()) {
                        index++;
                        context.iteration = index;
                        Object varValue = iter.next();
                        if (isLetOrConst) {
                            context.enterScope(ContextScope.LOOP_BODY, forBody);
                            enteredBodyScope = true;
                        }
                        evalAssign(bindings, context, bindScope, varValue, true);
                        forResult = eval(forBody, context);
                        if (isLetOrConst && enteredBodyScope) {
                            context.exitScope();
                            enteredBodyScope = false;
                        }
                        if (context.isStopped()) {
                            if (context.isContinuing()) {
                                context.reset();
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
        } finally {
            // Ensure we exit body scope if still in it
            if (enteredBodyScope) {
                context.exitScope();
            }
        }
        // break was consumed by this loop — don't propagate to parent block
        if (context.isBreaking()) {
            context.reset();
        }
        context.event(EventType.CONTEXT_EXIT, node);
        // Exit loop init scope
        context.exitScope();
        return forResult;
    }

    private static Object evalIfStmt(Node node, CoreContext context) {
        if (Terms.isTruthy(eval(node.get(2), context))) {
            return eval(node.get(4), context);
        } else {
            if (node.size() > 5) {
                return eval(node.get(6), context);
            }
            return null;
        }
    }

    private static Object evalInstanceOfExpr(Node node, CoreContext context) {
        return Terms.instanceOf(eval(node.get(0), context), eval(node.get(2), context));
    }

    private static BindScope toScope(BindScope scope) {
        return scope == BindScope.VAR ? null : scope;
    }

    private static Object evalLitArray(Node node, CoreContext context) {
        int last = node.size() - 1;
        JsArray array = new JsArray();
        List<Object> list = array.list;  // Direct access to internal list for building
        for (int i = 1; i < last; i++) {
            Node elem = node.get(i);
            Node exprNode = elem.get(0);
            if (exprNode.token.type == DOT_DOT_DOT) { // spread
                Object value = evalRefExpr(elem.get(1), context);
                JsIterator iter = IterUtils.getIterator(value, context);
                while (iter.hasNext()) {
                    list.add(iter.next());
                }
            } else if (exprNode.token.type == COMMA) { // sparse hole
                list.add(null);
            } else {
                list.add(evalExpr(exprNode, context));
            }
        }
        return array;
    }

    private static Object evalLitObject(Node node, CoreContext context) {
        int last = node.size() - 1;
        Map<String, Object> result = new JsObject(new LinkedHashMap<>(last - 1));
        for (int i = 1; i < last; i++) {
            Node elem = node.get(i);
            Node keyNode = elem.getFirst();
            TokenType token = keyNode.token.type;
            // ES6 getter/setter: OBJECT_ELEM starts with IDENT 'get' or 'set',
            // followed by the property name (IDENT/STRING/NUMBER or [expr]), then FN_EXPR.
            // Distinguished from shorthand methods named 'get'/'set' (which have FN_EXPR
            // directly at position 1, not position >=2).
            if (token == IDENT
                    && (isAccessorKeyword(keyNode.getText()))
                    && elem.size() > 2
                    && accessorFnExprPosition(elem) > 1) {
                evalAccessorElem(elem, keyNode.getText(), context, result);
                continue;
            }
            // Computed keys: [expr] — OBJECT_ELEM starts with L_BRACKET, EXPR, R_BRACKET.
            // The value (or FN_EXPR for shorthand method) follows at position 3.
            boolean computed = token == L_BRACKET;
            int afterKeyPos = computed ? 3 : 1;
            String key;
            if (computed) {
                Object keyValue = evalExpr(elem.get(1), context);
                key = Terms.toStringCoerce(keyValue, context);
            } else if (token == DOT_DOT_DOT) {
                key = elem.get(1).getText();
            } else if (token == S_STRING || token == D_STRING) {
                key = (String) Terms.literalValue(keyNode.token);
            } else { // IDENT, NUMBER
                key = keyNode.getText();
            }
            if (token == DOT_DOT_DOT) {
                Object value = context.get(key);
                if (value instanceof Map<?, ?> temp) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) temp;
                    result.putAll(map);
                }
            } else if (elem.size() > afterKeyPos && elem.get(afterKeyPos).type == NodeType.FN_EXPR) {
                // Shorthand method: {foo() {...}} or {[k]() {...}} — the synthetic
                // FN_EXPR is the next child after the key structure.
                result.put(key, evalFnExpr(elem.get(afterKeyPos), context));
            } else if (!computed && elem.size() < 3) { // shorthand {foo}
                result.put(key, context.get(key));
            } else {
                result.put(key, evalExpr(elem.get(afterKeyPos + 1), context));
            }
        }
        return result;
    }

    private static boolean isAccessorKeyword(String text) {
        return "get".equals(text) || "set".equals(text);
    }

    private static final Object[] EMPTY_ARGS = new Object[0];

    /** Invokes an accessor getter with {@code this} bound to the owning object. */
    static Object invokeGetter(JsCallable getter, Object thisObj, CoreContext context) {
        Object savedThis = context.thisObject;
        context.thisObject = thisObj;
        try {
            return getter.call(context, EMPTY_ARGS);
        } finally {
            context.thisObject = savedThis;
        }
    }

    /** Invokes an accessor setter with {@code this} bound to the owning object. */
    static void invokeSetter(JsCallable setter, Object thisObj, Object value, CoreContext context) {
        Object savedThis = context.thisObject;
        context.thisObject = thisObj;
        try {
            setter.call(context, new Object[]{value});
        } finally {
            context.thisObject = savedThis;
        }
    }

    /**
     * Returns the index of the FN_EXPR child (the accessor body) within an
     * OBJECT_ELEM, or {@code -1} if absent. The parser places it as the last
     * structural child; a trailing COMMA token may sit after it.
     */
    private static int accessorFnExprPosition(Node elem) {
        for (int j = elem.size() - 1; j >= 0; j--) {
            if (elem.get(j).type == NodeType.FN_EXPR) {
                return j;
            }
        }
        return -1;
    }

    private static void evalAccessorElem(Node elem, String kind, CoreContext context, Map<String, Object> result) {
        int fnPos = accessorFnExprPosition(elem);
        // Key is at position 1 (IDENT/STRING/NUMBER) or [L_BRACKET, EXPR, R_BRACKET]
        // starting at position 1.
        Node keyChild = elem.get(1);
        String key;
        if (keyChild.token != null && keyChild.token.type == L_BRACKET) {
            Object keyValue = evalExpr(elem.get(2), context);
            key = Terms.toStringCoerce(keyValue, context);
        } else if (keyChild.token != null
                && (keyChild.token.type == S_STRING || keyChild.token.type == D_STRING)) {
            key = (String) Terms.literalValue(keyChild.token);
        } else {
            key = keyChild.getText();
        }
        Object fn = evalFnExpr(elem.get(fnPos), context);
        if (!(fn instanceof JsCallable callable)) {
            return; // defensive; evalFnExpr always returns a JsFunctionNode
        }
        Object existing = result.get(key);
        JsAccessor acc = existing instanceof JsAccessor ea
                ? ea
                : new JsAccessor(null, null);
        if ("get".equals(kind)) {
            acc.getter = callable;
        } else {
            acc.setter = callable;
        }
        result.put(key, acc);
    }

    private static String evalLitTemplate(Node node, CoreContext context) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = node.size(); i < n; i++) {
            if (context.isError()) {
                return sb.toString();
            }
            Node child = node.get(i);
            if (child.token.type == T_STRING) {
                sb.append(unescapeString(child.token.getText()));
            } else if (child.type == NodeType.EXPR) {
                Object value = eval(child, context);
                if (context.isError()) {
                    return sb.toString();
                }
                sb.append(Terms.toStringCoerce(value, context));
            }
        }
        return sb.toString();
    }

    /**
     * Converts escape sequences in a string to their actual characters (standard JS behavior).
     * Handles: backslash-n, backslash-r, backslash-t, backslash-backslash, backslash-quote, etc.
     */
    private static String unescapeString(String s) {
        if (s.indexOf('\\') == -1) {
            return s; // no escapes, fast path
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n' -> { sb.append('\n'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    case 'b' -> { sb.append('\b'); i++; }
                    case 'f' -> { sb.append('\f'); i++; }
                    case '0' -> { sb.append('\0'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case '\'' -> { sb.append('\''); i++; }
                    case '"' -> { sb.append('"'); i++; }
                    case 'u' -> {
                        // Unicode escape sequence (4 hex digits)
                        if (i + 5 < s.length()) {
                            try {
                                int code = Integer.parseInt(s.substring(i + 2, i + 6), 16);
                                sb.append((char) code);
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append(c); // invalid escape, keep as-is
                            }
                        } else {
                            sb.append(c);
                        }
                    }
                    default -> sb.append(c); // unknown escape, keep backslash
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Object evalLitExpr(Node node, CoreContext context) {
        node = node.getFirst();
        if (node.isToken()) {
            Object value = Terms.literalValue(node.token);
            // Unescape string literals at runtime
            if (value instanceof String s && (node.token.type == TokenType.S_STRING || node.token.type == TokenType.D_STRING)) {
                return unescapeString(s);
            }
            return value;
        }
        return switch (node.type) {
            case NodeType.LIT_ARRAY -> evalLitArray(node, context);
            case NodeType.LIT_OBJECT -> evalLitObject(node, context);
            case NodeType.LIT_TEMPLATE -> evalLitTemplate(node, context);
            case NodeType.LIT_REGEX -> new JsRegex(node.getFirstToken().getText());
            default -> throw new RuntimeException("unexpected lit expr: " + node);
        };
    }

    private static Object evalLogicBitExpr(Node node, CoreContext context) {
        return switch (node.get(1).token.type) {
            case AMP -> terms(node, context).bitAnd();
            case PIPE -> terms(node, context).bitOr();
            case CARET -> terms(node, context).bitXor();
            case GT_GT -> terms(node, context).bitShiftRight();
            case LT_LT -> terms(node, context).bitShiftLeft();
            case GT_GT_GT -> terms(node, context).bitShiftRightUnsigned();
            default -> throw new RuntimeException("unexpected operator: " + node.get(1));
        };
    }

    private static boolean evalLogicExpr(Node node, CoreContext context) {
        Object lhs = eval(node.get(0), context);
        Object rhs = eval(node.get(2), context);
        TokenType logicOp = node.get(1).token.type;
        if (Terms.NAN.equals(lhs) || Terms.NAN.equals(rhs)) {
            if (logicOp == NOT_EQ || logicOp == NOT_EQ_EQ) {
                return true;  // NaN is not equal to anything, including itself
            }
            return false;  // NaN compared to anything with ==, ===, <, >, <=, >= is false
        }
        return switch (logicOp) {
            case EQ_EQ -> Terms.eq(lhs, rhs, false);
            case EQ_EQ_EQ -> Terms.eq(lhs, rhs, true);
            case NOT_EQ -> !Terms.eq(lhs, rhs, false);
            case NOT_EQ_EQ -> !Terms.eq(lhs, rhs, true);
            case LT -> Terms.lt(lhs, rhs);
            case GT -> Terms.gt(lhs, rhs);
            case LT_EQ -> Terms.ltEq(lhs, rhs);
            case GT_EQ -> Terms.gtEq(lhs, rhs);
            default -> throw new RuntimeException("unexpected operator: " + node.get(1));
        };
    }

    private static Object evalLogicAndExpr(Node node, CoreContext context) {
        Object lhsValue = eval(node.get(0), context);
        boolean lhs = Terms.isTruthy(lhsValue);
        if (node.get(1).token.type == AMP_AMP) {
            if (lhs) {
                return eval(node.get(2), context);
            } else {
                return lhsValue;
            }
        } else { // PIPE_PIPE
            if (lhs) {
                return lhsValue;
            } else {
                return eval(node.get(2), context);
            }
        }
    }

    private static Object evalLogicNullishExpr(Node node, CoreContext context) {
        Object lhsValue = eval(node.get(0), context);
        // ?? returns lhs if it's not null/undefined, otherwise rhs
        if (lhsValue == null || lhsValue == Terms.UNDEFINED) {
            return eval(node.get(2), context);
        }
        return lhsValue;
    }

    private static Object evalLogicTernExpr(Node node, CoreContext context) {
        if (Terms.isTruthy(eval(node.get(0), context))) {
            return eval(node.get(2), context);
        } else {
            return eval(node.get(4), context);
        }
    }

    private static Object evalMathAddExpr(Node node, CoreContext context) {
        return switch (node.get(1).token.type) {
            case PLUS -> Terms.add(eval(node.get(0), context), eval(node.get(2), context));
            case MINUS -> terms(node, context).min();
            default -> throw new RuntimeException("unexpected operator: " + node.get(1));
        };
    }

    private static Object evalMathMulExpr(Node node, CoreContext context) {
        return switch (node.get(1).token.type) {
            case STAR -> terms(node, context).mul();
            case SLASH -> terms(node, context).div();
            case PERCENT -> terms(node, context).mod();
            default -> throw new RuntimeException("unexpected operator: " + node.get(1));
        };
    }

    private static Object evalMathPostExpr(Node node, CoreContext context) {
        boolean isIncrement = node.get(1).token.type == PLUS_PLUS;
        return PropertyAccess.postIncDec(node.get(0), context, isIncrement);
    }

    private static Object evalMathPreExpr(Node node, CoreContext context) {
        Node exprNode = node.get(1).getFirst();
        return switch (node.get(0).token.type) {
            case PLUS_PLUS -> PropertyAccess.preIncDec(exprNode, context, true);
            case MINUS_MINUS -> PropertyAccess.preIncDec(exprNode, context, false);
            case MINUS -> {
                Object v = eval(exprNode, context);
                // Rare path: BigInt unary negation needs to stay in the BigInt domain.
                // The plain-Number case below would TypeError because rhs (-1) is Integer.
                if (v instanceof java.math.BigInteger bi) {
                    yield Terms.narrowBigInt(bi.negate());
                }
                yield terms(v, -1).mul();
            }
            case PLUS -> {
                Object v = eval(exprNode, context);
                // Spec: unary + on BigInt is a TypeError (no implicit BigInt → Number).
                if (v instanceof java.math.BigInteger) {
                    throw JsErrorException.typeError("Cannot convert a BigInt to a number using unary +");
                }
                yield Terms.objectToNumber(v);
            }
            default -> throw new RuntimeException("unexpected operator: " + node.getFirst());
        };
    }

    private static Object evalProgram(Node node, CoreContext context) {
        // Hoist top-level function declarations so they're visible before their lexical
        // position. Per ES5/ES6, `function foo() {}` at script-level is registered before
        // any other statement runs — code earlier in the source can still reference foo.
        hoistFunctionDeclarations(node, context);
        // Per spec, FunctionDeclaration produces an empty completion (the previous
        // value carries through). We additionally fall back to the last hoisted
        // function when the script contains *only* declarations — a karate-js
        // convention so host code that loads a script consisting of just a
        // function definition can use the eval result directly.
        Object progResult = null;
        boolean anyNonDecl = false;
        Object lastFnDecl = null;
        for (int i = 0, n = node.size(); i < n; i++) {
            Node child = node.get(i);
            if (child.isEof()) {
                break;
            }
            if (isFunctionDeclarationStatement(child)) {
                String fnName = child.getFirst().get(1).getText();
                lastFnDecl = context.get(fnName);
                continue;
            }
            progResult = eval(child, context);
            anyNonDecl = true;
            if (context.isError()) {
                Object errorThrown = context.getErrorThrown();
                String errorMessage = null;
                String errorName = null;
                if (errorThrown instanceof JsObject jsError) {
                    Object message = jsError.getMember("message");
                    if (message instanceof String) {
                        errorMessage = (String) message;
                    }
                    Object name = jsError.getMember("name");
                    if (name instanceof String s && !s.isEmpty()) {
                        errorName = s;
                    } else if (jsError.getMember("constructor") instanceof JsFunction ctor
                            && ctor.getMember("name") instanceof String ctorName
                            && !ctorName.isEmpty()) {
                        // User-defined error classes (e.g. test262's Test262Error) often omit
                        // .name on the prototype; fall back to constructor.name so host callers
                        // still see a meaningful error type.
                        errorName = ctorName;
                    }
                }
                String rawMessage = errorMessage == null ? errorThrown.toString() : errorMessage;
                // Keep a readable prefix in the message for logging, but the structured
                // errorName is what callers (like the test262 runner) should consult.
                if (errorName != null && !rawMessage.startsWith(errorName + ":")) {
                    rawMessage = errorName + ": " + rawMessage;
                }
                String message = child.toStringError(rawMessage);
                throw new EngineException(message, null, errorName);
            }
        }
        return anyNonDecl ? progResult : lastFnDecl;
    }

    private static Object evalRefExpr(Node node, CoreContext context) {
        if (node.getFirst().type == NodeType.FN_ARROW_EXPR) { // arrow function
            return evalFnArrowExpr(node.getFirst(), context);
        } else {
            String varName = node.getText();
            if (context.hasKey(varName)) {
                return context.get(varName);
            }
            throw JsErrorException.referenceError(varName + " is not defined");
        }
    }

    private static Object evalReturnStmt(Node node, CoreContext context) {
        if (node.size() > 1) {
            return context.stopAndReturn(eval(node.get(1), context));
        } else {
            return context.stopAndReturn(null);
        }
    }

    private static Object evalStatement(Node node, CoreContext context) {
        node = node.getFirst(); // go straight to relevant node
        if (node.token.type == SEMI) { // ignore empty statements
            return null;
        }
        context.event(EventType.STATEMENT_ENTER, node);

        // Check interceptor before execution
        @SuppressWarnings("unchecked")
        RunInterceptor<Object> interceptor = (RunInterceptor<Object>) context.root.interceptor;
        DebugPointFactory<Object> pointFactory = null;
        Object point = null;
        if (interceptor != null && context.root.pointFactory != null) {
            @SuppressWarnings("unchecked")
            DebugPointFactory<Object> factory = (DebugPointFactory<Object>) context.root.pointFactory;
            pointFactory = factory;
            Token first = node.getFirstToken();
            String sourcePath = first.getResource() != null ? first.getResource().getRelativePath() : null;
            point = factory.create(DebugPointFactory.JS_STATEMENT, first.line, sourcePath, node, context);
            RunInterceptor.Action action = interceptor.beforeExecute(point);
            if (action == RunInterceptor.Action.SKIP) {
                context.event(EventType.STATEMENT_EXIT, node);
                return Terms.UNDEFINED;
            } else if (action == RunInterceptor.Action.WAIT) {
                action = interceptor.waitForResume();
                if (action == RunInterceptor.Action.SKIP) {
                    context.event(EventType.STATEMENT_EXIT, node);
                    return Terms.UNDEFINED;
                }
            }
        }

        try {
            Object statementResult = eval(node, context);
            if (logger.isTraceEnabled() || Engine.DEBUG) {
                NodeType nodeType = node.type;
                if (nodeType != NodeType.EXPR && nodeType != NodeType.BLOCK) {
                    Token first = node.getFirstToken();
                    logger.trace("{}{} {} | {}", first.getResource(), first.getPositionDisplay(), statementResult, node);
                    if (Engine.DEBUG) {
                        System.out.println(first.getResource() + first.getPositionDisplay() + " " + statementResult + " | " + node);
                    }
                }
            }
            // Notify interceptor of successful execution
            if (interceptor != null && pointFactory != null) {
                if (point == null) {
                    Token first = node.getFirstToken();
                    String sourcePath = first.getResource() != null ? first.getResource().getRelativePath() : null;
                    point = pointFactory.create(DebugPointFactory.JS_STATEMENT, first.line, sourcePath, node, context);
                }
                interceptor.afterExecute(point, statementResult, null);
            }
            context.event(EventType.STATEMENT_EXIT, node);
            return statementResult;
        } catch (Exception e) {
            // Notify interceptor of failed execution
            if (interceptor != null && pointFactory != null) {
                if (point == null) {
                    Token first = node.getFirstToken();
                    String sourcePath = first.getResource() != null ? first.getResource().getRelativePath() : null;
                    point = pointFactory.create(DebugPointFactory.JS_STATEMENT, first.line, sourcePath, node, context);
                }
                interceptor.afterExecute(point, null, e);
            }
            if (context.root.listener != null) {
                Event event = new Event(EventType.STATEMENT_EXIT, context, node);
                ExitResult exitResult = context.root.listener.onError(event, e);
                if (exitResult != null && exitResult.ignoreError) {
                    return exitResult.returnValue;
                }
            }
            // Avoid wrapping already-wrapped JS errors (prevents duplicate error messages)
            if (e instanceof EngineException) {
                throw e;
            }
            // Flow-control signals from host functions are intentional — never wrap as errors
            if (e instanceof FlowControlSignal) {
                throw (RuntimeException) e;
            }
            Token first = node.getFirstToken();
            // Carry engine-origin JS error identity across the boundary so
            // `EngineException.getJsErrorName()` works without re-parsing the message.
            String jsErrorName = null;
            JsErrorException jex = findJsErrorException(e);
            if (jex != null) {
                jsErrorName = jex.payload.getName();
            } else {
                // Map raw Java exceptions to a JS error constructor name so host
                // callers never see "IndexOutOfBoundsException" leak through. The
                // message body stays the JVM text (still informative), but the
                // structured jsErrorName + prefix make classification JS-native.
                jsErrorName = classifyJavaException(e);
            }
            String body = e.getMessage();
            if (jsErrorName != null && body != null && !body.startsWith(jsErrorName + ":")) {
                body = jsErrorName + ": " + body;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("js failed:\n");
            sb.append("==========\n");
            if (first.getResource().isFile()) {
                sb.append("  File: ").append(first.getResource()).append("\n");
            }
            if (first.line != 0) {
                sb.append("  Line: ").append(first.line + 1).append(", Col: ").append(first.col).append("\n");
            }
            sb.append("  Code: ").append(first.getLineText().trim()).append("\n");
            sb.append("  Error: ").append(body).append("\n");
            sb.append("==========");
            if (first.getResource().isFile()) {
                System.err.println("file://" + first.getResource().getUri().getPath() + ":" + first.getPositionDisplay() + " " + e);
            }
            throw new EngineException(sb.toString(), e, jsErrorName);
        }
    }

    private static Object evalSwitchStmt(Node node, CoreContext context) {
        Object switchValue = eval(node.get(2), context);
        List<Node> caseNodes = node.findImmediateChildren(NodeType.CASE_BLOCK);
        boolean found = false;
        Object result = null;
        try {
            for (Node caseNode : caseNodes) {
                if (!found) {
                    Object caseValue = eval(caseNode.get(1), context);
                    if (Terms.eq(switchValue, caseValue, true)) {
                        found = true;
                    }
                }
                if (found) {
                    for (int i = 3; i < caseNode.size(); i++) {
                        result = eval(caseNode.get(i), context);
                        if (context.isStopped()) {
                            return result;
                        }
                    }
                }
            }
            List<Node> defaultNodes = node.findImmediateChildren(NodeType.DEFAULT_BLOCK);
            if (!defaultNodes.isEmpty()) {
                result = evalBlock(defaultNodes.getFirst(), context);
            }
            return result;
        } finally {
            // break was consumed by this switch — don't propagate to parent block
            if (context.isBreaking()) {
                context.reset();
            }
        }
    }

    private static Object evalThrowStmt(Node node, CoreContext context) {
        Object result = eval(node.get(1), context);
        return context.stopAndThrow(result);
    }

    /**
     * Walks the cause chain looking for a {@link JsErrorException}. Returns it
     * when found, or {@code null} otherwise. Used at both the JS-catch boundary
     * ({@link #evalTryStmt}) and the host boundary ({@link #evalStatement}) to
     * read the structured JS-error payload without re-parsing messages.
     */
    private static JsErrorException findJsErrorException(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof JsErrorException jex) return jex;
            Throwable next = cur.getCause();
            if (next == null || next == cur) return null;
            cur = next;
        }
        return null;
    }

    /**
     * Map raw Java exceptions to the JS error constructor name callers should
     * see. Walks the cause chain so wrapping (e.g. reflection's
     * {@code InvocationTargetException}) doesn't hide the real origin. Returns
     * {@code null} if nothing maps — callers then leave jsErrorName unset.
     */
    private static String classifyJavaException(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof IndexOutOfBoundsException) return "RangeError";
            if (cur instanceof ArithmeticException) return "RangeError";
            if (cur instanceof NullPointerException) return "TypeError";
            if (cur instanceof ClassCastException) return "TypeError";
            if (cur instanceof NumberFormatException) return "TypeError";
            Throwable next = cur.getCause();
            if (next == null || next == cur) return null;
            cur = next;
        }
        return null;
    }

    /**
     * Build the {@link JsError} that JS code observes in a {@code catch} clause.
     * Prefers a structured {@link JsErrorException} payload from the cause chain;
     * falls back to a generic {@code Error} built from the deepest cause's message.
     */
    private static JsError buildCaughtError(Throwable e) {
        JsErrorException jex = findJsErrorException(e);
        if (jex != null) return jex.payload;
        Throwable cause = e;
        while (cause instanceof EngineException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        return new JsError(msg, cause);
    }

    /**
     * Wire {@code errObj.constructor} to the registered global matching its
     * {@code .name} (or {@code Error} as a fallback) so that JS-side identity
     * checks — {@code e.constructor === TypeError}, {@code e.constructor.name} —
     * behave the same whether the error was produced by a JS {@code throw} or by
     * engine code.
     */
    private static void wireErrorConstructor(JsError errObj, CoreContext context) {
        if (errObj.getConstructor() != null || errObj.getName() == null) return;
        Object ctor = context.root.get(errObj.getName());
        if (!(ctor instanceof JsError)) {
            ctor = context.root.get("Error");
        }
        if (ctor instanceof JsError ctorErr) {
            errObj.setConstructor(ctorErr);
        }
    }

    private static Object evalTryStmt(Node node, CoreContext context) {
        Object tryValue;
        try {
            tryValue = eval(node.get(1), context);
        } catch (RuntimeException e) {
            // FlowControlSignal (e.g. template redirect) is intentional — never JS-catch
            if (e instanceof FlowControlSignal) {
                throw e;
            }
            JsError errObj = buildCaughtError(e);
            wireErrorConstructor(errObj, context);
            context.stopAndThrow(errObj);
            tryValue = null;
        }
        Node finallyBlock = null;
        if (node.get(2).token.type == CATCH) {
            if (node.size() > 7) {
                finallyBlock = node.get(8);
            }
            if (context.isError()) {
                Object errorThrown = context.getErrorThrown();
                context.reset(); // Clear error before entering catch
                context.enterScope(ContextScope.CATCH, node);
                context.event(EventType.CONTEXT_ENTER, node);
                try {
                    if (node.get(3).token.type == L_PAREN) {
                        String errorName = node.get(4).getText();
                        context.put(errorName, errorThrown);
                        tryValue = eval(node.get(6), context);
                    } else { // catch without variable name, 3 is block
                        tryValue = eval(node.get(3), context);
                    }
                    if (context.isError()) { // catch threw error
                        tryValue = null;
                    }
                } finally {
                    context.event(EventType.CONTEXT_EXIT, node);
                    context.exitScope();
                }
            }
        } else if (node.get(2).token.type == FINALLY) {
            finallyBlock = node.get(3);
        }
        if (finallyBlock != null) {
            boolean wasError = context.isError();
            Object savedError = context.getErrorThrown();
            Object savedReturn = context.getReturnValue();
            if (wasError) {
                context.reset();
            }
            context.enterScope(ContextScope.BLOCK, node);
            context.event(EventType.CONTEXT_ENTER, node);
            try {
                eval(finallyBlock, context);
                if (context.isError()) {
                    throw new RuntimeException("finally block threw error: " + context.getErrorThrown());
                }
            } finally {
                context.event(EventType.CONTEXT_EXIT, node);
                context.exitScope();
            }
            // Restore error state if there was one
            if (wasError) {
                context.stopAndThrow(savedError);
            } else if (savedReturn != null) {
                context.stopAndReturn(savedReturn);
            }
        }
        return tryValue;
    }

    private static Object evalTypeofExpr(Node node, CoreContext context) {
        try {
            Object value = eval(node.get(1), context);
            return Terms.typeOf(value);
        } catch (Exception e) {
            return Terms.UNDEFINED.toString();
        }
    }

    private static Object evalUnaryExpr(Node node, CoreContext context) {
        Object unaryValue = eval(node.get(1), context);
        return switch (node.getFirst().token.type) {
            case NOT -> !Terms.isTruthy(unaryValue);
            case TILDE -> Terms.bitNot(unaryValue);
            case VOID -> Terms.UNDEFINED; // operand evaluated for side effects, result discarded
            default -> throw new RuntimeException("unexpected operator: " + node.getFirst());
        };
    }

    private static Object evalVarStmt(Node node, CoreContext context) {
        BindScope bindScope = switch (node.getFirstToken().type) {
            case CONST -> BindScope.CONST;
            case LET -> BindScope.LET;
            default -> BindScope.VAR;
        };
        Object lastValue = Terms.UNDEFINED;
        for (int i = 0, n = node.size(); i < n; i++) {
            Node child = node.get(i);
            if (child.type != NodeType.VAR_DECL) {
                continue;
            }
            Node binding = child.getFirst();
            Object value;
            boolean initialized;
            if (child.size() > 2) { // binding, EQ, expr
                value = eval(child.get(2), context);
                initialized = true;
            } else {
                value = Terms.UNDEFINED;
                initialized = false;
            }
            evalAssign(binding, context, bindScope, value, initialized);
            lastValue = value;
        }
        return lastValue;
    }

    private static Object evalWhileStmt(Node node, CoreContext context) {
        context.enterScope(ContextScope.LOOP_INIT, node);
        context.event(EventType.CONTEXT_ENTER, node);
        Node whileBody = node.getLast();
        Node whileExpr = node.get(2);
        Object whileResult = null;
        try {
            while (true) {
                Object whileCondition = eval(whileExpr, context);
                if (!Terms.isTruthy(whileCondition)) {
                    break;
                }
                whileResult = eval(whileBody, context);
                if (context.isStopped()) {
                    if (context.isContinuing()) {
                        context.reset();
                    } else { // break, return or throw
                        break;
                    }
                }
            }
        } finally {
            // break was consumed by this loop — don't propagate to parent block
            if (context.isBreaking()) {
                context.reset();
            }
            context.event(EventType.CONTEXT_EXIT, node);
            context.exitScope();
        }
        return whileResult;
    }

    private static Object evalDoWhileStmt(Node node, CoreContext context) {
        context.enterScope(ContextScope.LOOP_INIT, node);
        context.event(EventType.CONTEXT_ENTER, node);
        Node doBody = node.get(1);
        Node doExpr = node.get(4);
        Object doResult = null;
        try {
            while (true) {
                doResult = eval(doBody, context);
                if (context.isStopped()) {
                    if (context.isContinuing()) {
                        context.reset();
                    } else { // break, return or throw
                        break;
                    }
                }
                Object doCondition = eval(doExpr, context);
                if (!Terms.isTruthy(doCondition)) {
                    break;
                }
            }
        } finally {
            // break was consumed by this loop — don't propagate to parent block
            if (context.isBreaking()) {
                context.reset();
            }
            context.event(EventType.CONTEXT_EXIT, node);
            context.exitScope();
        }
        return doResult;
    }

    static Object eval(Node node, CoreContext context) {
        return switch (node.type) {
            case ASSIGN_EXPR -> evalAssignExpr(node, context);
            case BLOCK -> evalBlock(node, context);
            case BREAK_STMT -> evalBreakStmt(node, context);
            case CONTINUE_STMT -> evalContinueStmt(node, context);
            case DELETE_STMT -> evalDeleteStmt(node, context);
            case EXPR -> evalExpr(node, context);
            case EXPR_LIST -> evalExprList(node, context);
            case LIT_EXPR -> evalLitExpr(node, context);
            case FN_EXPR -> evalFnExpr(node, context);
            case FN_ARROW_EXPR -> evalFnArrowExpr(node, context);
            case FN_CALL_EXPR -> chainStepResult(evalFnCall(node, context, false), node);
            case FN_TAGGED_TEMPLATE_EXPR -> chainStepResult(evalFnTaggedTemplate(node, context), node);
            case FOR_STMT -> evalForStmt(node, context);
            case IF_STMT -> evalIfStmt(node, context);
            case INSTANCEOF_EXPR -> evalInstanceOfExpr(node, context);
            case LOGIC_EXPR -> evalLogicExpr(node, context);
            case LOGIC_AND_EXPR -> evalLogicAndExpr(node, context);
            case LOGIC_NULLISH_EXPR -> evalLogicNullishExpr(node, context);
            case LOGIC_BIT_EXPR -> evalLogicBitExpr(node, context);
            case LOGIC_TERN_EXPR -> evalLogicTernExpr(node, context);
            case MATH_ADD_EXPR -> evalMathAddExpr(node, context);
            case MATH_EXP_EXPR -> terms(node, context).exp();
            case MATH_MUL_EXPR -> evalMathMulExpr(node, context);
            case MATH_POST_EXPR -> evalMathPostExpr(node, context);
            case MATH_PRE_EXPR -> evalMathPreExpr(node, context);
            case NEW_EXPR -> evalNewExpr(node, context);
            case PAREN_EXPR -> eval(node.get(1), context);
            case PROGRAM -> evalProgram(node, context);
            case REF_EXPR -> evalRefExpr(node, context);
            case REF_BRACKET_EXPR -> chainStepResult(PropertyAccess.get(node, context), node);
            case REF_DOT_EXPR -> {
                // Special case: `a?.(args)` parses as REF_DOT_EXPR[base, FN_CALL_EXPR[?., (, args, )]]
                // — dispatch as a call rather than a property get so the function is invoked.
                Node second = node.size() > 1 ? node.get(1) : null;
                Object result = (second != null && second.type == NodeType.FN_CALL_EXPR)
                        ? evalOptionalCall(node, context)
                        : PropertyAccess.get(node, context);
                yield chainStepResult(result, node);
            }
            case RETURN_STMT -> evalReturnStmt(node, context);
            case STATEMENT -> evalStatement(node, context);
            case SWITCH_STMT -> evalSwitchStmt(node, context);
            case THROW_STMT -> evalThrowStmt(node, context);
            case TRY_STMT -> evalTryStmt(node, context);
            case TYPEOF_EXPR -> evalTypeofExpr(node, context);
            case UNARY_EXPR -> evalUnaryExpr(node, context);
            case VAR_STMT -> evalVarStmt(node, context);
            case WHILE_STMT -> evalWhileStmt(node, context);
            case DO_WHILE_STMT -> evalDoWhileStmt(node, context);
            default -> throw new RuntimeException(node.toStringError("eval - unexpected node"));
        };
    }

}
