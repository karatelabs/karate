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
    final Map<String, BindValue> capturedBindings; // References to BindValues at creation time

    public JsFunctionNode(boolean arrow, Node node, List<Node> argNodes, Node body, CoreContext declaredContext) {
        this.arrow = arrow;
        this.node = node;
        this.argNodes = argNodes;
        this.argCount = argNodes.size();
        this.body = body;
        this.declaredContext = declaredContext;
        // Capture references to let/const BindValues at creation time for closure semantics
        this.capturedBindings = captureBindings(declaredContext);
    }

    private static Map<String, BindValue> captureBindings(CoreContext context) {
        if (context._bindings == null) {
            return null;
        }
        Map<String, BindValue> snapshot = null;
        for (String key : context._bindings.keySet()) {
            BindValue bv = context._bindings.getBindValue(key);
            if (bv != null && bv.scope != null) { // Only capture let/const bindings
                if (snapshot == null) {
                    snapshot = new HashMap<>(4); // Typically few captured vars
                }
                snapshot.put(key, bv); // Store reference, not copy
            }
        }
        return snapshot;
    }

    @Override
    public Object call(Context callerContext, Object[] args) {
        final CoreContext parentContext;
        if (callerContext instanceof CoreContext cc) {
            parentContext = cc;
        } else {
            parentContext = declaredContext;
        }
        // Create lightweight function context with captured bindings
        CoreContext functionContext = new CoreContext(parentContext, node, args, declaredContext, capturedBindings);
        return bindArgsAndExecute(functionContext, parentContext, args);
    }

    // Called by Interpreter when context is pre-prepared with closure info
    Object bindArgsAndExecute(CoreContext functionContext, CoreContext parentContext, Object[] args) {
        int actualArgCount = Math.min(args.length, argCount);
        for (int i = 0; i < actualArgCount; i++) {
            Node argNode = argNodes.get(i);
            Node first = argNode.getFirst();
            if (first.getFirstToken().type == TokenType.DOT_DOT_DOT) { // varargs
                List<Object> remainingArgs = new ArrayList<>();
                for (int j = i; j < args.length; j++) {
                    remainingArgs.add(args[j]);
                }
                String argName = argNode.getLast().getText();
                functionContext.put(argName, remainingArgs);
            } else if (first.type == NodeType.LIT_ARRAY || first.type == NodeType.LIT_OBJECT) {
                Interpreter.evalAssign(first, functionContext, BindScope.VAR, args[i], true);
            } else {
                String argName = argNode.getFirst().getText();
                Object argValue = args[i];
                if (argValue == Terms.UNDEFINED) {
                    // check if default value expression exists
                    // Only for FN_DECL_ARG nodes, not for single-param arrow functions
                    // where argNode is the whole FN_ARROW_EXPR (getLast() would be the body!)
                    Node exprNode = argNode.getLast();
                    if (argNode.type == NodeType.FN_DECL_ARG && exprNode.type == NodeType.EXPR) {
                        argValue = Interpreter.eval(exprNode, functionContext);
                    }
                }
                functionContext.put(argName, argValue);
            }
        }
        if (args.length < argCount) {
            for (int i = args.length; i < argCount; i++) {
                Node argNode = argNodes.get(i);
                String argName = argNode.getFirst().getText();
                Node exprNode = argNode.getLast();
                Object argValue;
                // Only evaluate as default if argNode is FN_DECL_ARG (not FN_ARROW_EXPR)
                if (argNode.type == NodeType.FN_DECL_ARG && exprNode.type == NodeType.EXPR) {
                    argValue = Interpreter.eval(exprNode, functionContext);
                } else {
                    argValue = Terms.UNDEFINED;
                }
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
    public String toString() {
        return node.toString();
    }

}
