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

import java.util.List;
import java.util.Map;

/**
 * Static utility methods for property access operations.
 * Avoids object allocation by using static methods instead of instance-based approach.
 */
@SuppressWarnings("unchecked")
class PropertyAccess {

    static final Logger logger = LoggerFactory.getLogger(PropertyAccess.class);

    private PropertyAccess() {} // Prevent instantiation

    /**
     * Sentinel returned when an optional-chaining step short-circuits. Propagates
     * up through chain steps (REF_DOT_EXPR, REF_BRACKET_EXPR, FN_CALL_EXPR,
     * FN_TAGGED_TEMPLATE_EXPR) and is converted to UNDEFINED at the chain root
     * in {@link Interpreter#eval(Node, CoreContext)}.
     * <p>
     * Distinct from {@link Terms#UNDEFINED} so a real undefined value produced by
     * a non-{@code ?.} step (e.g. accessing a missing property in the middle of
     * the chain) is not mistakenly treated as a short-circuit signal.
     */
    static final Object SHORT_CIRCUITED = new Object() {
        @Override public String toString() { return "<<short-circuited>>"; }
    };

    /**
     * True if the chain rooted at {@code node} contains any {@code ?.} marker.
     * Walks down through chain types only — PAREN_EXPR or any non-chain node
     * ends the walk (parens reset short-circuit scope per spec).
     */
    static boolean chainHasOptional(Node node) {
        Node cur = node;
        while (cur != null) {
            if (cur.type == NodeType.REF_DOT_EXPR) {
                Node second = cur.size() > 1 ? cur.get(1) : null;
                if (second != null) {
                    if (second.isToken() && second.token.type == TokenType.QUES_DOT) return true;
                    if (!second.isToken() && second.size() > 0) {
                        Node firstOfSecond = second.getFirst();
                        if (firstOfSecond.isToken() && firstOfSecond.token.type == TokenType.QUES_DOT) return true;
                    }
                }
                cur = cur.size() > 0 ? cur.getFirst() : null;
            } else if (cur.type == NodeType.REF_BRACKET_EXPR
                    || cur.type == NodeType.FN_CALL_EXPR
                    || cur.type == NodeType.FN_TAGGED_TEMPLATE_EXPR) {
                cur = cur.size() > 0 ? cur.getFirst() : null;
            } else {
                return false;
            }
        }
        return false;
    }

    /**
     * True if {@code node} is the outermost chain step in its optional chain —
     * i.e., its parent is not a chain step that has {@code node} as its first
     * child. The chain root is where {@link #SHORT_CIRCUITED} is converted back
     * to {@link Terms#UNDEFINED}.
     */
    static boolean isChainRoot(Node node) {
        Node parent = node.getParent();
        if (parent == null) return true;
        if (parent.type == NodeType.REF_DOT_EXPR
                || parent.type == NodeType.REF_BRACKET_EXPR
                || parent.type == NodeType.FN_CALL_EXPR
                || parent.type == NodeType.FN_TAGGED_TEMPLATE_EXPR) {
            return parent.size() == 0 || parent.getFirst() != node;
        }
        return true;
    }

    /**
     * Decoded property-access site. {@code target} is the object being read or
     * written; {@code key} is a String for dot access ({@code .name}) or an
     * arbitrary value for bracket access ({@code [expr]}). {@code isIndex} lets
     * the consumer pick byIndex vs byName in one branch instead of repeating
     * the AST-shape dispatch in every operation.
     */
    private static final class AccessSite {
        final Object target;
        final Object key;
        final boolean isIndex;

        AccessSite(Object target, Object key, boolean isIndex) {
            this.target = target;
            this.key = key;
            this.isIndex = isIndex;
        }
    }

    /**
     * Resolves a REF_DOT_EXPR / REF_BRACKET_EXPR for write operations
     * (set / compound / inc-dec / delete). Used by everything except the read
     * paths — the read paths additionally handle bridge fallback on eval
     * exceptions and ?.-aware short-circuit, both of which are unique to read.
     * <p>
     * Returns null when the chain short-circuited (target evaluated to
     * {@link #SHORT_CIRCUITED}). Write-on-optional-chain is itself a parse-time
     * early error (see {@code JsParser.validateOptionalChainEarlyErrors}), so
     * a short-circuit here is unreachable in valid programs — the null return
     * is a defensive no-op.
     * <p>
     * Throws TypeError for the {@code ?.()} call-only AST shape, which cannot
     * be a write target.
     */
    private static AccessSite resolveWriteSite(Node node, CoreContext context) {
        if (node.type == NodeType.REF_DOT_EXPR) {
            Node second = node.get(1);
            if (second.isToken()) {
                Object target = Interpreter.eval(node.getFirst(), context);
                if (target == SHORT_CIRCUITED) return null;
                return new AccessSite(target, node.get(2).getText(), false);
            }
            if (second.type == NodeType.REF_BRACKET_EXPR) {
                Object target = Interpreter.eval(node.getFirst(), context);
                if (target == SHORT_CIRCUITED) return null;
                Object index = Interpreter.eval(second.get(2), context);
                return new AccessSite(target, index, true);
            }
            throw JsErrorException.typeError("cannot write to optional call expression");
        }
        // REF_BRACKET_EXPR
        Object target = Interpreter.eval(node.getFirst(), context);
        if (target == SHORT_CIRCUITED) return null;
        Object index = Interpreter.eval(node.get(2), context);
        return new AccessSite(target, index, true);
    }

    //=== Simple get/set operations ===

    /**
     * Get a property value from a node expression.
     */
    static Object get(Node node, CoreContext context) {
                return switch (node.type) {
            case REF_EXPR -> getRefExpr(node, context, false);
            case REF_DOT_EXPR -> getRefDotExpr(node, context, false);
            case REF_BRACKET_EXPR -> getRefBracketExpr(node, context, false);
            case LIT_EXPR -> Interpreter.eval(node, context);
            case PAREN_EXPR -> Interpreter.eval(node.get(1), context);
            case FN_CALL_EXPR -> Interpreter.eval(node, context);
            default -> throw JsErrorException.typeError("cannot get from: " + node);
        };
    }

    /**
     * Get a callable and its receiver (this object) for method invocation.
     * Returns a 2-element array: [callable, receiver].
     * For method calls like obj.method(), receiver is obj.
     * For direct calls like foo(), receiver is null.
     */
    static Object[] getCallable(Node node, CoreContext context) {
                return switch (node.type) {
            case REF_EXPR -> new Object[]{getRefExpr(node, context, true), null};
            case REF_DOT_EXPR -> getCallableRefDotExpr(node, context);
            case REF_BRACKET_EXPR -> getCallableRefBracketExpr(node, context);
            // Per spec the Reference Record from a PAREN_EXPR is the same as the inner
            // expression's — so `(a.b)()` and `(a?.b)()` must preserve `a` as `this`.
            // Recurse via getCallable on the inner property-access expression; for
            // anything else the parens just pass the value through with no receiver.
            case PAREN_EXPR -> getCallableParenInner(node, context);
            case FN_CALL_EXPR -> new Object[]{Interpreter.eval(node, context), null};
            case FN_TAGGED_TEMPLATE_EXPR -> new Object[]{Interpreter.eval(node, context), null};
            case FN_EXPR, FN_ARROW_EXPR -> new Object[]{Interpreter.eval(node, context), null};
            default -> throw JsErrorException.typeError("cannot call: " + node);
        };
    }

    /**
     * Set a property value on a node expression.
     */
    static void set(Node node, CoreContext context, Object value) {
        set(node, context, value, null);
    }

    /**
     * Set a property value on a node expression with tracking node for events.
     */
    static void set(Node node, CoreContext context, Object value, Node trackingNode) {
                switch (node.type) {
            case REF_EXPR -> context.update(node.getText(), value, trackingNode);
            case REF_DOT_EXPR, REF_BRACKET_EXPR -> {
                AccessSite site = resolveWriteSite(node, context);
                if (site == null) return;
                if (site.isIndex) setByIndex(site.target, site.key, value, context, trackingNode);
                else setByName(site.target, (String) site.key, value, context, trackingNode);
            }
            default -> throw JsErrorException.typeError("cannot set on: " + node);
        }
    }

    //=== Compound operations (get + modify + set) ===

    /**
     * Compound assignment: obj.x op= value (e.g., +=, -=, *=, etc.)
     * Evaluates the base expression once, then applies the operation.
     * Returns the new value.
     */
    static Object compound(Node node, CoreContext context, TokenType operator, Object operand) {
        return compound(node, context, operator, operand, null);
    }

    /**
     * Compound assignment with tracking node for events.
     */
    static Object compound(Node node, CoreContext context, TokenType operator, Object operand, Node trackingNode) {
                return switch (node.type) {
            case REF_EXPR -> {
                String name = node.getText();
                Object oldValue = context.get(name);
                Object newValue = applyOperator(oldValue, operator, operand, context);
                context.update(name, newValue, trackingNode);
                yield newValue;
            }
            case REF_DOT_EXPR, REF_BRACKET_EXPR -> {
                AccessSite site = resolveWriteSite(node, context);
                if (site == null) yield Terms.UNDEFINED;
                yield site.isIndex
                        ? compoundByIndex(site.target, site.key, operator, operand, context, trackingNode)
                        : compoundByName(site.target, (String) site.key, operator, operand, context, trackingNode);
            }
            default -> throw JsErrorException.typeError("cannot apply compound assignment to: " + node);
        };
    }

    /**
     * Post-increment/decrement: returns old value, updates variable.
     */
    static Object postIncDec(Node node, CoreContext context, boolean isIncrement) {
                return switch (node.type) {
            case REF_EXPR -> {
                String name = node.getText();
                Object oldValue = context.get(name);
                Object step = Terms.incDecStep(oldValue);
                Object newValue = isIncrement ? Terms.add(oldValue, step, context) : new Terms(oldValue, step).min();
                context.update(name, newValue);
                yield oldValue;
            }
            case REF_DOT_EXPR, REF_BRACKET_EXPR -> {
                AccessSite site = resolveWriteSite(node, context);
                if (site == null) yield Terms.UNDEFINED;
                yield site.isIndex
                        ? postIncDecByIndex(site.target, site.key, isIncrement, context)
                        : postIncDecByName(site.target, (String) site.key, isIncrement, context);
            }
            case LIT_EXPR -> {
                // Handle literals like (x)++ where x is wrapped
                Object oldValue = Interpreter.eval(node, context);
                yield oldValue; // Can't actually modify a literal result
            }
            default -> throw JsErrorException.typeError("cannot apply post inc/dec to: " + node);
        };
    }

    /**
     * Pre-increment/decrement: updates variable, returns new value.
     */
    static Object preIncDec(Node node, CoreContext context, boolean isIncrement) {
                return switch (node.type) {
            case REF_EXPR -> {
                String name = node.getText();
                Object oldValue = context.get(name);
                Object step = Terms.incDecStep(oldValue);
                Object newValue = isIncrement ? Terms.add(oldValue, step, context) : new Terms(oldValue, step).min();
                context.update(name, newValue);
                yield newValue;
            }
            case REF_DOT_EXPR, REF_BRACKET_EXPR -> {
                AccessSite site = resolveWriteSite(node, context);
                if (site == null) yield Terms.UNDEFINED;
                yield site.isIndex
                        ? preIncDecByIndex(site.target, site.key, isIncrement, context)
                        : preIncDecByName(site.target, (String) site.key, isIncrement, context);
            }
            default -> throw JsErrorException.typeError("cannot apply pre inc/dec to: " + node);
        };
    }

    /**
     * Delete a property. Returns true on success.
     */
    static boolean delete(Node node, CoreContext context) {
                return switch (node.type) {
            case REF_EXPR -> false; // Can't delete variables
            case REF_DOT_EXPR, REF_BRACKET_EXPR -> {
                AccessSite site;
                try {
                    site = resolveWriteSite(node, context);
                } catch (JsErrorException e) {
                    // delete on `?.()` shape: legacy behavior was to return false silently
                    yield false;
                }
                if (site == null) yield false;
                yield deleteByKey(site.target, Terms.toPropertyKey(site.key), context, node);
            }
            default -> false;
        };
    }

    //=== Private implementation methods ===

    private static Object getRefExpr(Node node, CoreContext context, boolean functionCall) {
        String name = node.getText();
        if (context.hasKey(name)) {
            Object result = context.get(name);
            if (functionCall && context.root.bridge != null && result instanceof ExternalAccess ea) {
                return (JsConstructor) (c, args) -> ea.construct(args);
            }
            return result;
        }
        throw JsErrorException.referenceError(name + " is not defined");
    }

    /**
     * Shared {@code REF_DOT_EXPR} resolution for both the value-only
     * ({@link #getRefDotExpr}) and call-site ({@link #getCallableRefDotExpr})
     * paths. Captures every AST shape (named dot, optional dot, optional
     * bracket, optional call), the external-bridge fallback on eval failure,
     * and the {@code ?.} short-circuit propagation in one place.
     * <p>
     * When {@code outReceiver} is non-null, the resolved LHS object is
     * written to {@code outReceiver[1]} on the property-projection paths
     * ({@code obj.x} → {@code object}, {@code obj?.[expr]} → {@code object});
     * left as null on short-circuit, bridge-forType wraps, and bare-object
     * passthrough. Caller pre-allocates the array (no per-call record
     * allocation on the hot value-read path; one Object[2] for the callable
     * path matches the pre-unify cost).
     */
    private static Object resolveRefDot(Node node, CoreContext context, boolean functionCall, Object[] outReceiver) {
        String name;
        Object object;
        boolean optional;

        if (node.get(1).type == NodeType.TOKEN) {
            optional = node.get(1).token.type == TokenType.QUES_DOT;
            name = node.get(2).getText();
            try {
                object = Interpreter.eval(node.getFirst(), context);
            } catch (Exception e) {
                if (context.root.bridge != null) {
                    String base = node.getFirst().getText();
                    String path = base + "." + name;
                    ExternalAccess ja = context.root.bridge.forType(path);
                    if (ja != null) {
                        if (functionCall) {
                            return (JsConstructor) (c, args) -> ja.construct(args);
                        }
                        return ja;
                    }
                    object = context.root.bridge.forType(base);
                } else {
                    object = null;
                }
                if (object == null) {
                    throw new RuntimeException("expression: " + node.getFirst().getText() + " - " + e.getMessage(), e);
                }
            }
            // Propagate short-circuit from a deeper ?. step.
            if (object == SHORT_CIRCUITED) return SHORT_CIRCUITED;
            // Local ?. fires here.
            if (optional && (object == null || object == Terms.UNDEFINED)) return SHORT_CIRCUITED;
        } else {
            optional = true;
            if (node.get(1).type == NodeType.REF_BRACKET_EXPR) {
                object = Interpreter.eval(node.getFirst(), context);
                if (object == SHORT_CIRCUITED) return SHORT_CIRCUITED;
                // ?.[expr] fires here — index is not evaluated when short-circuiting.
                if (object == null || object == Terms.UNDEFINED) return SHORT_CIRCUITED;
                Object index = Interpreter.eval(node.get(1).get(2), context);
                if (outReceiver != null) outReceiver[1] = object;
                return getByIndex(object, index, false, context, functionCall);
            } else {
                object = Interpreter.eval(node.getFirst(), context);
                if (object == SHORT_CIRCUITED) return SHORT_CIRCUITED;
                name = null;
            }
        }

        if (functionCall) {
            Object jsValue = Terms.toJsValue(object);
            if (jsValue != null) {
                object = jsValue;
            }
        }

        if (name == null) {
            // ?.() / bare-object passthrough — receiver stays null since the
            // dot didn't actually project a property.
            if (functionCall && context.root.bridge != null && object instanceof ExternalAccess ea) {
                return (JsConstructor) (c, args) -> ea.construct(args);
            }
            return object;
        }

        if (outReceiver != null) outReceiver[1] = object;
        return getByName(object, name, optional, context, functionCall);
    }

    private static Object getRefDotExpr(Node node, CoreContext context, boolean functionCall) {
        return resolveRefDot(node, context, functionCall, null);
    }

    private static Object[] getCallableRefDotExpr(Node node, CoreContext context) {
        Object[] result = new Object[2];
        result[0] = resolveRefDot(node, context, true, result);
        return result;
    }

    private static Object getRefBracketExpr(Node node, CoreContext context, boolean functionCall) {
        Object object = Interpreter.eval(node.getFirst(), context);
        if (object == SHORT_CIRCUITED) return SHORT_CIRCUITED;
        Object index = Interpreter.eval(node.get(2), context);
        return getByIndex(object, index, false, context, functionCall);
    }

    // Unwraps PAREN_EXPR -> [(, EXPR_LIST[EXPR[<inner>]], )] to preserve the
    // inner reference for receiver binding — `(a.b)()` must call with `this = a`,
    // and `(a?.b)()` likewise when `a?.b` resolves. Only single-expression parens
    // qualify; comma operators (`(a, b)()`) drop the reference per spec.
    // Parens also terminate the optional chain — a short-circuit inside the
    // parens surfaces as undefined here, so the outer call gets a "not a function"
    // TypeError rather than silently returning undefined.
    private static Object[] getCallableParenInner(Node node, CoreContext context) {
        Node body = node.size() > 1 ? node.get(1) : null;
        Node inner = null;
        if (body != null && body.type == NodeType.EXPR_LIST && body.size() == 1) {
            Node onlyExpr = body.getFirst();
            if (onlyExpr.type == NodeType.EXPR && onlyExpr.size() == 1) {
                inner = onlyExpr.getFirst();
            }
        }
        if (inner != null && (inner.type == NodeType.REF_DOT_EXPR
                || inner.type == NodeType.REF_BRACKET_EXPR
                || inner.type == NodeType.PAREN_EXPR)) {
            Object[] result = getCallable(inner, context);
            if (result[0] == SHORT_CIRCUITED) {
                return new Object[]{Terms.UNDEFINED, null};
            }
            return result;
        }
        return new Object[]{Interpreter.eval(node.get(1), context), null};
    }

    private static Object[] getCallableRefBracketExpr(Node node, CoreContext context) {
        Object object = Interpreter.eval(node.getFirst(), context);
        if (object == SHORT_CIRCUITED) return new Object[]{SHORT_CIRCUITED, null};
        Object index = Interpreter.eval(node.get(2), context);
        return new Object[]{getByIndex(object, index, false, context, true), object};
    }

    private static Object getByIndex(Object object, Object index, boolean optional,
                                      CoreContext context, boolean functionCall) {
        if (!functionCall && index instanceof Number n) {
            int i = n.intValue();
            if (object == null || object == Terms.UNDEFINED) {
                if (optional) return Terms.UNDEFINED;
                throw JsErrorException.typeError("cannot read properties of " + object + " (reading '[" + i + "]')");
            }
            if (object instanceof JsArray array) {
                return array.getIndexedValue(i, array, context);
            }
            if (object instanceof List<?> list) {
                if (i < 0 || i >= list.size()) return Terms.UNDEFINED;
                // Translate JsArray.HOLE → undefined so callers reading from
                // a raw List that was sourced from a sparse JsArray (e.g.
                // Array.prototype.* methods that return rawList directly)
                // never see the sentinel.
                return JsArray.unwrapHole(list.get(i));
            }
            if (object instanceof String s) {
                if (i < 0 || i >= s.length()) return Terms.UNDEFINED;
                return s.substring(i, i + 1);
            }
            if (object instanceof byte[] bytes) {
                if (i < 0 || i >= bytes.length) return Terms.UNDEFINED;
                return bytes[i] & 0xFF;
            }
            ObjectLike converted = Terms.toObjectLike(object);
            if (converted instanceof JsArray jsArray) {
                return jsArray.getIndexedValue(i, jsArray, context);
            }
            if (object instanceof Map || object instanceof ObjectLike) {
                return getByName(object, Terms.toPropertyKey(index), optional, context, functionCall);
            }
            throw JsErrorException.typeError("get by index [" + i + "] for non-array: " + object);
        }
        return getByName(object, Terms.toPropertyKey(index), optional, context, functionCall);
    }

    private static Object getByName(Object object, String name, boolean optional,
                                     CoreContext context, boolean functionCall) {
        if (object == null || object == Terms.UNDEFINED) {
            if (context.hasKey(name)) {
                Object result = context.get(name);
                if (functionCall && context.root.bridge != null && result instanceof ExternalAccess ea) {
                    return (JsConstructor) (c, args) -> ea.construct(args);
                }
                return result;
            }
            if (optional) return Terms.UNDEFINED;
            throw JsErrorException.typeError("cannot read properties of " + object + " (reading '" + name + "')");
        }

        if (object instanceof JsObject jsObj) {
            if (jsObj.containsKey(name)) {
                return jsObj.getMember(name, object, context);
            }
            Object result = jsObj.getMember(name, object, context);
            if (isFound(result)) return result;
            return Terms.UNDEFINED;
        } else if (object instanceof JsArray jsArr) {
            // Own properties pass through raw — preserves a literal {@code null}
            // value at an index/named key (test262
            // {@code defineProperty/15.2.3.6-4-{207,208,216,312}} install a
            // value of {@code null} via {@code defineProperty(arr, "0",
            // {value: null})} and then read {@code arr[0]}; without the
            // own-check the {@link #isFound} fallback wrongly converts
            // {@code null} → {@code undefined}).
            if (jsArr.isOwnProperty(name)) {
                return jsArr.getMember(name, object, context);
            }
            Object result = jsArr.getMember(name, object, context);
            if (isFound(result)) return result;
            return Terms.UNDEFINED;
        } else if (object instanceof ObjectLike ol) {
            Object result = ol.getMember(name, object, context);
            if (isFound(result)) return result;
        } else if (object instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) object;
            if (map.containsKey(name)) return map.get(name);
            Object result = new JsObject(map).getMember(name, object, context);
            if (result != null) return result;
        } else if (object instanceof List) {
            ObjectLike ol = Terms.toObjectLike(object);
            if (ol != null) {
                Object result = ol.getMember(name, object, context);
                if (isFound(result)) return result;
            }
        } else {
            ObjectLike ol = Terms.toObjectLike(object);
            if (ol != null) {
                Object result = ol.getMember(name, object, context);
                if (isFound(result)) return result;
            }
        }

        if (object instanceof JsCallable callable) {
            return new JsFunction() {
                @Override
                public Object call(Context context, Object[] args) {
                    return callable.call(context, args);
                }
            }.getMember(name);
        }

        return accessViaBridge(object, name, context, functionCall);
    }

    private static void setByIndex(Object object, Object index, Object value, CoreContext context, Node trackingNode) {
        if (index instanceof Number n) {
            int i = n.intValue();
            // JsArray with a descriptor at this index (accessor or attributed
            // data property installed via Object.defineProperty) takes the slow
            // path through setByName, which honors AccessorSlot setters and
            // writable=false enforcement. The hot path (no descriptor) skips
            // the check via JsArray.hasIndexedDescriptor's null guard.
            // <p>
            // Non-extensible / sealed / frozen arrays also route through
            // setByName so {@link JsArray#putMember} can enforce the integrity
            // bits — the dense {@code list.set(i, value)} path below would
            // otherwise silently overwrite a frozen index or extend a sealed
            // array. Hot path stays branch-light: {@code isExtensible()} is a
            // single boolean read.
            if (object instanceof JsArray array
                    && (array.hasIndexedDescriptor(i) || !array.isExtensible())) {
                setByName(object, String.valueOf(i), value, context, trackingNode);
                return;
            }
            if (object instanceof List) {
                List<Object> list = (List<Object>) object;
                Object oldValue = i < list.size() ? list.get(i) : Terms.UNDEFINED;
                if (i < list.size()) {
                    list.set(i, value);
                } else {
                    // JS semantics: arr[i] = x for i >= length extends the
                    // array with holes (sparse positions whose own property
                    // is absent). JsArray distinguishes HOLE from explicit
                    // undefined via the dedicated sentinel — use it here so
                    // hasOwnProperty(intermediate) === false. Raw List hosts
                    // (Java ArrayList passed in by the user) don't model
                    // holes; UNDEFINED is the closest representable value.
                    Object pad = object instanceof JsArray ? JsArray.HOLE : Terms.UNDEFINED;
                    while (list.size() < i) {
                        list.add(pad);
                    }
                    list.add(value);
                }
                firePropertySet(context, String.valueOf(i), value, oldValue, object, trackingNode);
                return;
            } else if (object instanceof byte[] bytes) {
                if (value instanceof Number v) {
                    Object oldValue = i < bytes.length ? bytes[i] & 0xFF : Terms.UNDEFINED;
                    bytes[i] = (byte) (v.intValue() & 0xFF);
                    firePropertySet(context, String.valueOf(i), v.intValue() & 0xFF, oldValue, object, trackingNode);
                }
                return;
            }
        }
        setByName(object, Terms.toPropertyKey(index), value, context, trackingNode);
    }

    /**
     * Spec-shape {@code [[Set]]} entry — walks the prototype chain for accessor
     * descriptors (so a setter installed on {@code Array.prototype["0"]} fires
     * when {@code Array.prototype.{push, unshift}} stores at index 0), routes
     * {@code length} through {@code JsArray.handleLengthAssign} for the spec
     * Uint32 + writable + partial-truncate dance, and otherwise falls through
     * to {@code putMember}. Package-private so {@code JsArrayPrototype.{push,
     * unshift}} can do per-item Set in the spec sequence.
     */
    static void setByName(Object object, String name, Object value, CoreContext context, Node trackingNode) {
        if (name == null) {
            throw JsErrorException.typeError("unexpected set [null]:" + value + " on: " + object);
        }
        if (object == null) {
            context.update(name, value, trackingNode);
        } else if (object instanceof ObjectLike objectLike) {
            // Spec ArraySetLength dispatch needs context for valueOf/toString
            // coercion; route through the JsArray-specific entry point.
            // Throws RangeError on invalid Uint32; silently ignores writable=false
            // and partial-truncate failures (lenient mode — strict-mode TypeError
            // flip lives elsewhere).
            if (objectLike instanceof JsArray ja && "length".equals(name)) {
                Object oldLen = ja.size();
                ja.handleLengthAssign(value, context);
                firePropertySet(context, name, ja.size(), oldLen, object, trackingNode);
                return;
            }
            // If an accessor descriptor lives at `name` anywhere in the
            // prototype chain, invoke its setter via slot.write —
            // bypassing putMember preserves the descriptor and threads
            // the live ctx so setters that read other properties or throw
            // see the correct call frame. Lenient: a get-only accessor
            // silently drops the write.
            AccessorSlot accSlot = findAccessorInChain(objectLike, name);
            if (accSlot != null) {
                accSlot.write(object, value, context, false);
                return;
            }
            Object oldValue = objectLike.getMember(name);
            objectLike.putMember(name, value);
            firePropertySet(context, name, value, oldValue, object, trackingNode);
        } else if (object instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) object;
            Object oldValue = map.get(name);
            map.put(name, value);
            firePropertySet(context, name, value, oldValue, object, trackingNode);
        } else if (context.root.bridge != null) {
            try {
                if (object instanceof ExternalAccess ja) {
                    ja.setProperty(name, value);
                } else {
                    ExternalAccess ja = context.root.bridge.forInstance(object);
                    ja.setProperty(name, value);
                }
                firePropertySet(context, name, value, null, object, trackingNode);
            } catch (Exception e) {
                logger.error("external bridge error: {}", e.getMessage());
                throw JsErrorException.typeError("cannot set '" + name + "'");
            }
        } else {
            throw JsErrorException.typeError("cannot set '" + name + "'");
        }
    }

    private static void firePropertySet(CoreContext context, String name, Object value, Object oldValue, Object target, Node node) {
        if (context.root.listener != null) {
            context.root.listener.onBind(BindEvent.propertySet(name, value, oldValue, target, context, node));
        }
    }

    //=== Compound operation workers ===

    private static Object compoundByIndex(Object object, Object index, TokenType operator, Object operand, CoreContext context, Node trackingNode) {
        if (index instanceof Number n) {
            int i = n.intValue();
            if (object instanceof List) {
                List<Object> list = (List<Object>) object;
                Object oldValue = i < list.size() ? list.get(i) : Terms.UNDEFINED;
                Object newValue = applyOperator(oldValue, operator, operand, context);
                list.set(i, newValue);
                firePropertySet(context, String.valueOf(i), newValue, oldValue, object, trackingNode);
                return newValue;
            }
        }
        return compoundByName(object, Terms.toPropertyKey(index), operator, operand, context, trackingNode);
    }

    private static Object compoundByName(Object object, String name, TokenType operator, Object operand, CoreContext context, Node trackingNode) {
        Object oldValue = getByName(object, name, false, context, false);
        Object newValue = applyOperator(oldValue, operator, operand, context);
        setByName(object, name, newValue, context, trackingNode);
        return newValue;
    }

    private static Object postIncDecByIndex(Object object, Object index, boolean isIncrement, CoreContext context) {
        if (index instanceof Number n) {
            int i = n.intValue();
            if (object instanceof List) {
                List<Object> list = (List<Object>) object;
                Object oldValue = i < list.size() ? list.get(i) : Terms.UNDEFINED;
                Object step = Terms.incDecStep(oldValue);
                Object newValue = isIncrement ? Terms.add(oldValue, step, context) : new Terms(oldValue, step).min();
                list.set(i, newValue);
                firePropertySet(context, String.valueOf(i), newValue, oldValue, object, null);
                return oldValue;
            }
        }
        return postIncDecByName(object, Terms.toPropertyKey(index), isIncrement, context);
    }

    private static Object postIncDecByName(Object object, String name, boolean isIncrement, CoreContext context) {
        Object oldValue = getByName(object, name, false, context, false);
        Object step = Terms.incDecStep(oldValue);
        Object newValue = isIncrement ? Terms.add(oldValue, step, context) : new Terms(oldValue, step).min();
        setByName(object, name, newValue, context, null);
        return oldValue;
    }

    private static Object preIncDecByIndex(Object object, Object index, boolean isIncrement, CoreContext context) {
        if (index instanceof Number n) {
            int i = n.intValue();
            if (object instanceof List) {
                List<Object> list = (List<Object>) object;
                Object oldValue = i < list.size() ? list.get(i) : Terms.UNDEFINED;
                Object step = Terms.incDecStep(oldValue);
                Object newValue = isIncrement ? Terms.add(oldValue, step, context) : new Terms(oldValue, step).min();
                list.set(i, newValue);
                firePropertySet(context, String.valueOf(i), newValue, oldValue, object, null);
                return newValue;
            }
        }
        return preIncDecByName(object, Terms.toPropertyKey(index), isIncrement, context);
    }

    private static Object preIncDecByName(Object object, String name, boolean isIncrement, CoreContext context) {
        Object oldValue = getByName(object, name, false, context, false);
        Object step = Terms.incDecStep(oldValue);
        Object newValue = isIncrement ? Terms.add(oldValue, step, context) : new Terms(oldValue, step).min();
        setByName(object, name, newValue, context, null);
        return newValue;
    }

    private static boolean deleteByKey(Object object, String key, CoreContext context, Node node) {
        Object oldValue = null;
        if (object instanceof ObjectLike ol) {
            oldValue = ol.getMember(key);
            ol.removeMember(key);
            firePropertyDelete(context, key, oldValue, object, node);
            return true;
        } else if (object instanceof Map<?, ?> map) {
            oldValue = ((Map<String, Object>) map).get(key);
            ((Map<String, Object>) map).remove(key);
            firePropertyDelete(context, key, oldValue, object, node);
            return true;
        }
        return false;
    }

    private static void firePropertyDelete(CoreContext context, String name, Object oldValue, Object target, Node node) {
        if (context.root.listener != null) {
            context.root.listener.onBind(BindEvent.propertyDelete(name, oldValue, target, context, node));
        }
    }

    //=== Helper methods ===

    private static Object applyOperator(Object oldValue, TokenType operator, Object operand, CoreContext context) {
        return switch (operator) {
            case PLUS_EQ -> Terms.add(oldValue, operand, context);
            case MINUS_EQ -> new Terms(oldValue, operand).min();
            case STAR_EQ -> new Terms(oldValue, operand).mul();
            case SLASH_EQ -> new Terms(oldValue, operand).div();
            case PERCENT_EQ -> new Terms(oldValue, operand).mod();
            case STAR_STAR_EQ -> new Terms(oldValue, operand).exp();
            case GT_GT_EQ -> new Terms(oldValue, operand).bitShiftRight();
            case LT_LT_EQ -> new Terms(oldValue, operand).bitShiftLeft();
            case GT_GT_GT_EQ -> new Terms(oldValue, operand).bitShiftRightUnsigned();
            case AMP_EQ -> new Terms(oldValue, operand).bitAnd();
            case PIPE_EQ -> new Terms(oldValue, operand).bitOr();
            case CARET_EQ -> new Terms(oldValue, operand).bitXor();
            default -> throw new RuntimeException("unexpected operator: " + operator);
        };
    }

    private static boolean isFound(Object result) {
        return result != null && result != Terms.UNDEFINED;
    }

    /** Walks the prototype chain looking for an accessor slot at
     *  {@code name}. Returns the first {@link AccessorSlot} found, or
     *  {@code null} (no accessor in chain — write proceeds as a normal
     *  data put on the receiver). Stops at the first own slot at each
     *  level, even if it's a data slot — matches spec
     *  OrdinarySetWithOwnDescriptor semantics. */
    private static AccessorSlot findAccessorInChain(ObjectLike obj, String name) {
        ObjectLike current = obj;
        while (current != null) {
            PropertySlot s = ownSlot(current, name);
            if (s instanceof AccessorSlot acc) return acc;
            if (s != null) return null; // own data slot — accessor lookup stops here
            current = current.getPrototype();
        }
        return null;
    }

    /** Single-signature own-slot lookup across the three slot-bearing
     *  storage shapes ({@link JsObject}, {@link JsArray}, {@link Prototype}).
     *  Returns {@code null} for absent / tombstoned keys and for hosts
     *  without a slot store (raw Maps, Java-bridge objects). Cross-cutting
     *  helper for {@link #findAccessorInChain} and
     *  {@code Object.getOwnPropertyDescriptor}'s accessor-shape probe. */
    static PropertySlot ownSlot(Object obj, String key) {
        if (obj instanceof JsObject jo) return jo.getOwnSlot(key);
        if (obj instanceof JsArray ja) return ja.getOwnSlot(key);
        if (obj instanceof Prototype p) return p.getOwnSlot(key);
        return null;
    }

    private static Object accessViaBridge(Object object, String name, CoreContext context, boolean functionCall) {
        if (context.root.bridge == null) {
            return Terms.UNDEFINED;
        }
        try {
            ExternalAccess ja = object instanceof ExternalAccess ea
                    ? ea : context.root.bridge.forInstance(object);
            return functionCall ? ja.getMethod(name) : ja.getProperty(name);
        } catch (Exception e) {
            return Terms.UNDEFINED;
        }
    }

}
