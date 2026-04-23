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
        if (bindings.type == NodeType.LIT_ARRAY) {
            List<Object> list = null;
            if (value instanceof List) {
                list = (List<Object>) value;
            }
            evalLitArray(bindings, context, bindScope, list);
        } else if (bindings.type == NodeType.LIT_OBJECT) {
            Map<String, Object> object = null;
            if (value instanceof Map) {
                object = (Map<String, Object>) value;
            }
            evalLitObject(bindings, context, bindScope, object);
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
                evalAssign(lhs.getFirst(), context, BindScope.VAR, value, true);
            } else {
                PropertyAccess.set(lhs, context, value, node);
            }
            return value;
        }
        return PropertyAccess.compound(lhs, context, operator, value, node);
    }

    private static Object evalBlock(Node node, CoreContext context) {
        context.enterScope(ContextScope.BLOCK, node);
        context.event(EventType.CONTEXT_ENTER, node);
        Object blockResult = null;
        try {
            for (int i = 0, n = node.size(); i < n; i++) {
                Node child = node.get(i);
                if (child.type == NodeType.STATEMENT) {
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
        if (o == Terms.UNDEFINED) { // optional chaining
            return o;
        }
        if (o instanceof JsCallable callable) {
            List<Object> argsList = new ArrayList<>();
            int argsCount = fnArgsNode == null ? 0 : fnArgsNode.size();
            for (int i = 0; i < argsCount; i++) {
                Node fnArgNode = fnArgsNode.get(i);
                Node argNode = fnArgNode.get(0);
                if (argNode.isToken()) { // DOT_DOT_DOT
                    Object arg = eval(fnArgNode.get(1), context);
                    if (arg instanceof List<?> list) {
                        argsList.addAll(list);
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

    private static Object evalFnExpr(Node node, CoreContext context) {
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
                Iterable<KeyValue> iterable = Terms.toIterable(forObject);
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
                int index = -1;
                for (KeyValue kv : iterable) {
                    index++;
                    context.iteration = index;
                    Object varValue = in ? kv.key() : kv.value();
                    if (isLetOrConst) {
                        // Enter body scope for this iteration
                        context.enterScope(ContextScope.LOOP_BODY, forBody);
                        enteredBodyScope = true;
                    }
                    // Declare/assign loop variable(s) - handles both simple and destructuring cases
                    evalAssign(bindings, context, bindScope, varValue, true);
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

    private static Object evalLitArray(Node node, CoreContext context, BindScope bindScope, List<Object> bindSource) {
        int last = node.size() - 1;
        JsArray array = new JsArray();
        List<Object> list = array.list;  // Direct access to internal list for building
        int index = 0;
        for (int i = 1; i < last; i++) {
            Node elem = node.get(i);
            Node exprNode = elem.get(0);
            if (exprNode.token.type == DOT_DOT_DOT) { // rest
                if (bindScope != null) {
                    String varName = elem.getLast().getText();
                    JsArray restArray = new JsArray();
                    if (bindSource != null) {
                        for (int j = index; j < bindSource.size(); j++) {
                            // Use raw access for JsArray to preserve undefined values
                            Object elem_ = bindSource instanceof JsArray arr
                                    ? arr.getElement(j)
                                    : bindSource.get(j);
                            restArray.list.add(elem_);
                        }
                    }
                    context.declare(varName, restArray, toScope(bindScope), true);
                } else {
                    Object value = evalRefExpr(elem.get(1), context);
                    Iterable<KeyValue> iterable = Terms.toIterable(value);
                    for (KeyValue kv : iterable) {
                        list.add(kv.value());
                    }
                }
            } else if (exprNode.token.type == COMMA) { // sparse
                list.add(null);
                index++;
            } else {
                if (bindScope != null) {
                    Object value = Terms.UNDEFINED;
                    String varName = exprNode.getFirstToken().getText();
                    if (exprNode.getFirst().type == NodeType.ASSIGN_EXPR) { // default value
                        value = evalExpr(exprNode.getFirst().getLast(), context);
                    }
                    if (bindSource != null && index < bindSource.size()) {
                        // Use raw access to get actual value (including undefined)
                        // JsArray.get() auto-unwraps, but we need raw values for destructuring
                        Object temp = bindSource instanceof JsArray arr
                                ? arr.getElement(index)
                                : bindSource.get(index);
                        if (temp != Terms.UNDEFINED) {
                            value = temp;
                        }
                    }
                    context.declare(varName, value, toScope(bindScope), true);
                } else {
                    Object value = evalExpr(exprNode, context);
                    list.add(value);
                }
                index++;
            }
        }
        return array;
    }

    @SuppressWarnings("unchecked")
    private static Object evalLitObject(Node node, CoreContext context, BindScope bindScope, Map<String, Object> bindSource) {
        int last = node.size() - 1;
        Map<String, Object> result;
        if (bindSource != null) {
            result = new HashMap<>(bindSource); // use to derive ...rest if it appears
        } else {
            result = new JsObject(new LinkedHashMap<>(last - 1));
        }
        for (int i = 1; i < last; i++) {
            Node elem = node.get(i);
            Node keyNode = elem.getFirst();
            TokenType token = keyNode.token.type;
            String key;
            if (token == DOT_DOT_DOT) {
                key = elem.get(1).getText();
            } else if (token == S_STRING || token == D_STRING) {
                key = (String) Terms.literalValue(keyNode.token);
            } else { // IDENT, NUMBER
                key = keyNode.getText();
            }
            if (token == DOT_DOT_DOT) {
                if (bindScope != null) {
                    // previous keys were being removed from result
                    context.declare(key, result, toScope(bindScope), true);
                } else {
                    Object value = context.get(key);
                    if (value instanceof Map) {
                        Map<String, Object> temp = (Map<String, Object>) value;
                        result.putAll(temp);
                    }
                }
            } else if (elem.size() < 3) { // es6 enhanced object literals
                if (bindScope != null) {
                    Object value = Terms.UNDEFINED;
                    if (bindSource != null && bindSource.containsKey(key)) {
                        value = bindSource.get(key);
                    }
                    context.declare(key, value, toScope(bindScope), true);
                    result.remove(key);
                } else {
                    Object value = context.get(key);
                    result.put(key, value);
                }
            } else {
                if (bindScope != null) {
                    Object value = Terms.UNDEFINED;
                    if (bindSource != null && bindSource.containsKey(key)) {
                        value = bindSource.get(key);
                    }
                    if (elem.get(1).getFirstToken().type == EQ) { // default value
                        value = evalExpr(elem.get(2), context);
                        context.declare(key, value, toScope(bindScope), true);
                    } else {
                        String varName = elem.get(2).getText();
                        context.declare(varName, value, toScope(bindScope), true);
                    }
                    result.remove(key);
                } else {
                    Object value = evalExpr(elem.get(2), context);
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    private static String evalLitTemplate(Node node, CoreContext context) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = node.size(); i < n; i++) {
            Node child = node.get(i);
            if (child.token.type == T_STRING) {
                sb.append(child.token.getText());
            } else if (child.type == NodeType.EXPR) {
                Object value = eval(child, context);
                if (value == Terms.UNDEFINED) {
                    throw JsErrorException.referenceError(child.getText() + " is not defined");
                }
                sb.append(value);
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
            case NodeType.LIT_ARRAY -> evalLitArray(node, context, null, null);
            case NodeType.LIT_OBJECT -> evalLitObject(node, context, null, null);
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
            case MINUS -> terms(eval(exprNode, context), -1).mul();
            case PLUS -> Terms.objectToNumber(eval(exprNode, context));
            default -> throw new RuntimeException("unexpected operator: " + node.getFirst());
        };
    }

    private static Object evalProgram(Node node, CoreContext context) {
        Object progResult = null;
        for (int i = 0, n = node.size(); i < n; i++) {
            Node child = node.get(i);
            if (child.isEof()) {
                break;
            }
            progResult = eval(child, context);
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
                    if (name instanceof String) {
                        errorName = (String) name;
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
        return progResult;
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
            sb.append("  Error: ").append(e.getMessage()).append("\n");
            sb.append("==========");
            if (first.getResource().isFile()) {
                System.err.println("file://" + first.getResource().getUri().getPath() + ":" + first.getPositionDisplay() + " " + e);
            }
            // Carry engine-origin JS error identity across the boundary so
            // `EngineException.getJsErrorName()` works without re-parsing the message.
            String jsErrorName = null;
            JsErrorException jex = findJsErrorException(e);
            if (jex != null) {
                jsErrorName = jex.payload.getName();
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
            case FN_CALL_EXPR -> evalFnCall(node, context, false);
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
            case NEW_EXPR -> evalFnCall(node.get(1), context, true);
            case PAREN_EXPR -> eval(node.get(1), context);
            case PROGRAM -> evalProgram(node, context);
            case REF_EXPR -> evalRefExpr(node, context);
            case REF_BRACKET_EXPR, REF_DOT_EXPR -> PropertyAccess.get(node, context);
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
