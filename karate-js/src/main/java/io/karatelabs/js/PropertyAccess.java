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

    /**
     * An empty object used only to reach {@code Object.prototype} when a raw Map has no such
     * key. Shared because it holds no state of its own — {@code getMember} reads the prototype
     * chain and never mutates the receiver — and it is never handed to script code.
     */
    private static final JsObject PROTOTYPE_PROBE = new JsObject();

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
        /** Non-null for {@code obj.#x}: the write goes to the object's private
         *  state, never to {@code props}, so {@code key} is unused. */
        final PrivateName privateName;

        AccessSite(Object target, Object key, boolean isIndex) {
            this(target, key, isIndex, null);
        }

        AccessSite(Object target, Object key, boolean isIndex, PrivateName privateName) {
            this.target = target;
            this.key = key;
            this.isIndex = isIndex;
            this.privateName = privateName;
        }
    }

    /**
     * Returned instead of a real site when an optional-chaining step in the
     * target short-circuited. Kept distinct from the {@code null} (abrupt
     * completion) return so the two outcomes stay separable: an abrupt
     * completion leaves the pending throw to propagate, while a short circuit
     * makes the whole reference {@code undefined} — there is no reference, so
     * {@code delete} on it succeeds.
     */
    private static final AccessSite SHORT_CIRCUIT_SITE = new AccessSite(null, null, false);

    /**
     * Resolves a REF_DOT_EXPR / REF_BRACKET_EXPR for write operations
     * (set / compound / inc-dec / delete). Used by everything except the read
     * paths — the read paths additionally handle bridge fallback on eval
     * exceptions and ?.-aware short-circuit, both of which are unique to read.
     * <p>
     * Returns {@link #SHORT_CIRCUIT_SITE} when the chain short-circuited
     * (target evaluated to {@link #SHORT_CIRCUITED}), null on an abrupt
     * completion. Assignment / compound / inc-dec on an optional chain is a
     * parse-time early error (see
     * {@code JsParser.validateOptionalChainEarlyErrors}), so the short-circuit
     * reaches only {@code delete} and the destructuring-pattern leaves.
     * <p>
     * Throws TypeError for the {@code ?.()} call-only AST shape, which cannot
     * be a write target.
     */
    private static AccessSite resolveWriteSite(Node node, CoreContext context) {
        // The isStopped checks after each eval keep an abrupt completion (a
        // cooperative `throw` in the target or computed-key expression) from
        // reaching the by-index / by-name workers, whose own TypeErrors would
        // otherwise overwrite the pending error. A null site is a no-op at
        // every caller, so the abrupt completion propagates unmodified.
        if (node.type == NodeType.REF_DOT_EXPR) {
            Node second = node.get(1);
            if (second.isToken()) {
                Node key = node.get(2);
                Object target = Interpreter.eval(node.getFirst(), context);
                if (target == SHORT_CIRCUITED) return SHORT_CIRCUIT_SITE;
                if (context.isStopped()) return null;
                if (key.token.type == TokenType.PRIVATE_NAME) {
                    return new AccessSite(target, null, false,
                            PrivateAccess.resolve(key.getText(), context));
                }
                return new AccessSite(target, key.getText(), false);
            }
            if (second.type == NodeType.REF_BRACKET_EXPR) {
                Object target = Interpreter.eval(node.getFirst(), context);
                if (target == SHORT_CIRCUITED) return SHORT_CIRCUIT_SITE;
                if (context.isStopped()) return null;
                Object index = Interpreter.eval(second.get(2), context);
                if (context.isStopped()) return null;
                return new AccessSite(target, index, true);
            }
            throw JsErrorException.typeError("cannot write to optional call expression");
        }
        // REF_BRACKET_EXPR
        Object target = Interpreter.eval(node.getFirst(), context);
        if (target == SHORT_CIRCUITED) return SHORT_CIRCUIT_SITE;
        if (context.isStopped()) return null;
        Object index = Interpreter.eval(node.get(2), context);
        if (context.isStopped()) return null;
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
            case REF_EXPR -> {
                int slot = node.slot;
                Object[] frame;
                if (slot >= 0 && (frame = context.frame) != null && frame[slot] != SlotTable.UNDECLARED) {
                    context.updateSlot(slot, value, trackingNode);
                } else {
                    context.update(node.getText(), value, trackingNode);
                }
            }
            case REF_DOT_EXPR, REF_BRACKET_EXPR -> {
                AccessSite site = resolveWriteSite(node, context);
                if (site == null || site == SHORT_CIRCUIT_SITE) return;
                if (site.privateName != null) PrivateAccess.set(site.target, site.privateName, value, context);
                else if (site.isIndex) setByIndex(site.target, site.key, value, context, trackingNode);
                else setByName(site.target, (String) site.key, value, context, trackingNode);
            }
            default -> throw JsErrorException.typeError("cannot set on: " + node);
        }
    }

    //=== Assignment and compound operations ===

    /**
     * Simple assignment (`=`) in spec §13.15.2 evaluation order: the LHS
     * Reference — base object and computed key — is evaluated BEFORE the RHS
     * expression, and an abrupt completion at either step skips the write.
     * Returns the assigned value (the value of the whole assignment
     * expression).
     */
    static Object assign(Node node, CoreContext context, Node rhsNode, Node trackingNode) {
                return switch (node.type) {
            case REF_EXPR -> {
                // An identifier Reference resolves without observable side
                // effects; an unresolvable name only throws at PutValue time,
                // which is after the RHS has been evaluated.
                Object value = Interpreter.eval(rhsNode, context);
                if (context.isStopped()) yield value;
                context.update(node.getText(), value, trackingNode);
                yield value;
            }
            case REF_DOT_EXPR, REF_BRACKET_EXPR -> {
                AccessSite site = resolveWriteSite(node, context);
                if (context.isStopped()) yield Terms.UNDEFINED;
                Object value = Interpreter.eval(rhsNode, context);
                if (site == null || site == SHORT_CIRCUIT_SITE || context.isStopped()) yield value;
                if (site.target == null && node.getFirst().type == NodeType.SUPER_EXPR) {
                    // A super reference whose home object has a null [[Prototype]]
                    // resolves to a null base; PutValue on it is a TypeError —
                    // AFTER the RHS has been evaluated (hence not in
                    // resolveWriteSite). Without this, setByName's null-target
                    // fallback would treat the write as a scope update.
                    throw JsErrorException.typeError("cannot set property on null 'super' base");
                }
                if (site.privateName != null) PrivateAccess.set(site.target, site.privateName, value, context);
                else if (site.isIndex) setByIndex(site.target, site.key, value, context, trackingNode);
                else setByName(site.target, (String) site.key, value, context, trackingNode);
                yield value;
            }
            default -> throw JsErrorException.typeError("cannot set on: " + node);
        };
    }

    /**
     * Compound assignment (`op=`, e.g. +=, -=, *=) in spec §13.15 order:
     * resolve the LHS Reference, GetValue it, and only then evaluate the RHS —
     * a computed-key or getter side effect precedes any RHS side effect, and
     * an abrupt completion at each step skips the rest. Returns the new value.
     */
    static Object compound(Node node, CoreContext context, TokenType operator, Node rhsNode, Node trackingNode) {
                return switch (node.type) {
            case REF_EXPR -> {
                // Slot fast path, in the same spec order as the name tail below: read the
                // LHS (a TDZ read throws before any RHS side effect), THEN evaluate the RHS.
                int slot = node.slot;
                Object[] frame;
                if (slot >= 0 && (frame = context.frame) != null && frame[slot] != SlotTable.UNDECLARED) {
                    Object oldValue = frame[slot];
                    if (oldValue == SlotTable.TDZ) {
                        throw SlotTable.tdzError(node.getText());
                    }
                    Object operand = Interpreter.eval(rhsNode, context);
                    if (context.isStopped()) yield Terms.UNDEFINED;
                    Object newValue = applyOperator(oldValue, operator, operand, context);
                    context.updateSlot(slot, newValue, trackingNode);
                    yield newValue;
                }
                yield compoundRefByName(node, context, operator, rhsNode, trackingNode);
            }
            case REF_DOT_EXPR, REF_BRACKET_EXPR -> {
                AccessSite site = resolveWriteSite(node, context);
                if (site == null || site == SHORT_CIRCUIT_SITE || context.isStopped()) yield Terms.UNDEFINED;
                Object oldValue = site.privateName != null
                        ? PrivateAccess.get(site.target, site.privateName, context)
                        : site.isIndex
                                ? getByIndex(site.target, site.key, false, context, false)
                                : getByName(site.target, (String) site.key, false, context, false);
                if (context.isStopped()) yield Terms.UNDEFINED;
                Object operand = Interpreter.eval(rhsNode, context);
                if (context.isStopped()) yield Terms.UNDEFINED;
                Object newValue = applyOperator(oldValue, operator, operand, context);
                if (context.isStopped()) yield Terms.UNDEFINED;
                if (site.privateName != null) PrivateAccess.set(site.target, site.privateName, newValue, context);
                else if (site.isIndex) setByIndex(site.target, site.key, newValue, context, trackingNode);
                else setByName(site.target, (String) site.key, newValue, context, trackingNode);
                yield newValue;
            }
            default -> throw JsErrorException.typeError("cannot apply compound assignment to: " + node);
        };
    }

    /**
     * ES2021 logical-assignment (||=, &&=, ??=) with short-circuit semantics.
     * The LHS reference is resolved once (target + key are evaluated in spec
     * order, so `base[key()] ||= rhs` calls `key()` exactly once); the RHS
     * expression is evaluated only when the operator's condition requires it.
     */
    static Object logicalCompound(Node node, CoreContext context, TokenType operator, Node rhsNode, Node trackingNode) {
                return switch (node.type) {
            case REF_EXPR -> {
                int slot = node.slot;
                Object[] frame;
                if (slot >= 0 && (frame = context.frame) != null && frame[slot] != SlotTable.UNDECLARED) {
                    Object oldValue = frame[slot];
                    if (oldValue == SlotTable.TDZ) {
                        throw SlotTable.tdzError(node.getText());
                    }
                    if (!shouldLogicalAssign(operator, oldValue)) yield oldValue;
                    Object newValue = Interpreter.eval(rhsNode, context);
                    if (context.isStopped()) yield null;
                    context.updateSlot(slot, newValue, trackingNode);
                    yield newValue;
                }
                yield logicalCompoundRefByName(node, context, operator, rhsNode, trackingNode);
            }
            case REF_DOT_EXPR, REF_BRACKET_EXPR -> {
                AccessSite site = resolveWriteSite(node, context);
                if (site == null || site == SHORT_CIRCUIT_SITE) yield Terms.UNDEFINED;
                // JS-level throw inside the target or index eval surfaces as
                // context.isStopped() (eval returns null on abrupt completion).
                // Bail before doing any further checks so the in-flight throw
                // — not our own TypeError — is what the catch sees.
                if (context.isStopped()) yield null;
                // RequireObjectCoercible fires before ToPropertyKey on the index —
                // null/undefined target must throw TypeError before any toString
                // on a non-primitive key is invoked.
                if (site.target == null || site.target == Terms.UNDEFINED) {
                    throw JsErrorException.typeError("cannot read properties of "
                            + (site.target == null ? "null" : "undefined"));
                }
                if (site.privateName != null) {
                    Object oldValue = PrivateAccess.get(site.target, site.privateName, context);
                    if (!shouldLogicalAssign(operator, oldValue)) yield oldValue;
                    Object newValue = Interpreter.eval(rhsNode, context);
                    if (context.isStopped()) yield null;
                    PrivateAccess.set(site.target, site.privateName, newValue, context);
                    yield newValue;
                }
                yield site.isIndex
                        ? logicalCompoundByIndex(site.target, site.key, operator, rhsNode, context, trackingNode)
                        : logicalCompoundByName(site.target, (String) site.key, operator, rhsNode, context, trackingNode);
            }
            default -> throw JsErrorException.typeError("cannot apply logical-assignment to: " + node);
        };
    }

    // Name-keyed REF_EXPR tails, outlined from their switch cases to keep the
    // slot fast paths small enough to inline reliably.

    private static Object compoundRefByName(Node node, CoreContext context, TokenType operator, Node rhsNode, Node trackingNode) {
        String name = node.getText();
        Object oldValue = context.get(name);
        if (context.isStopped()) return Terms.UNDEFINED;
        Object operand = Interpreter.eval(rhsNode, context);
        if (context.isStopped()) return Terms.UNDEFINED;
        Object newValue = applyOperator(oldValue, operator, operand, context);
        context.update(name, newValue, trackingNode);
        return newValue;
    }

    private static Object logicalCompoundRefByName(Node node, CoreContext context, TokenType operator, Node rhsNode, Node trackingNode) {
        String name = node.getText();
        Object oldValue = context.get(name);
        if (!shouldLogicalAssign(operator, oldValue)) return oldValue;
        Object newValue = Interpreter.eval(rhsNode, context);
        if (context.isStopped()) return null;
        context.update(name, newValue, trackingNode);
        return newValue;
    }

    private static Object incDecRefByName(Node node, CoreContext context, boolean isIncrement, boolean returnNew) {
        String name = node.getText();
        Object oldValue = context.get(name);
        Object step = Terms.incDecStep(oldValue);
        Object newValue = isIncrement ? Terms.add(oldValue, step, context) : Terms.min(oldValue, step, context);
        context.update(name, newValue);
        return returnNew ? newValue : oldValue;
    }

    private static boolean shouldLogicalAssign(TokenType operator, Object lhsValue) {
        return switch (operator) {
            case PIPE_PIPE_EQ -> !Terms.isTruthy(lhsValue);
            case AMP_AMP_EQ -> Terms.isTruthy(lhsValue);
            case QUES_QUES_EQ -> lhsValue == null || lhsValue == Terms.UNDEFINED;
            default -> throw new RuntimeException("unexpected logical-assignment operator: " + operator);
        };
    }

    private static Object logicalCompoundByIndex(Object object, Object index, TokenType operator, Node rhsNode, CoreContext context, Node trackingNode) {
        if (index instanceof Number n) {
            int i = n.intValue();
            if (object instanceof List) {
                List<Object> list = (List<Object>) object;
                Object oldValue = i < list.size() ? list.get(i) : Terms.UNDEFINED;
                if (!shouldLogicalAssign(operator, oldValue)) return oldValue;
                Object newValue = Interpreter.eval(rhsNode, context);
                if (context.isStopped()) return null;
                while (list.size() <= i) list.add(Terms.UNDEFINED);
                list.set(i, newValue);
                firePropertySet(context, String.valueOf(i), newValue, oldValue, object, trackingNode);
                return newValue;
            }
        }
        return logicalCompoundByName(object, Terms.toPropertyKey(index), operator, rhsNode, context, trackingNode);
    }

    private static Object logicalCompoundByName(Object object, String name, TokenType operator, Node rhsNode, CoreContext context, Node trackingNode) {
        Object oldValue = getByName(object, name, false, context, false);
        if (!shouldLogicalAssign(operator, oldValue)) return oldValue;
        Object newValue = Interpreter.eval(rhsNode, context);
        if (context.isStopped()) return null;
        setByName(object, name, newValue, context, trackingNode);
        return newValue;
    }

    /**
     * Post-increment/decrement: returns old value, updates variable.
     */
    static Object postIncDec(Node node, CoreContext context, boolean isIncrement) {
                return switch (node.type) {
            case REF_EXPR -> {
                int slot = node.slot;
                Object[] frame;
                if (slot >= 0 && (frame = context.frame) != null && frame[slot] != SlotTable.UNDECLARED) {
                    Object oldValue = frame[slot];
                    if (oldValue == SlotTable.TDZ) {
                        throw SlotTable.tdzError(node.getText());
                    }
                    Object step = Terms.incDecStep(oldValue);
                    Object newValue = isIncrement ? Terms.add(oldValue, step, context) : Terms.min(oldValue, step, context);
                    context.updateSlot(slot, newValue, null);
                    yield oldValue;
                }
                yield incDecRefByName(node, context, isIncrement, false);
            }
            case REF_DOT_EXPR, REF_BRACKET_EXPR -> {
                AccessSite site = resolveWriteSite(node, context);
                if (site == null || site == SHORT_CIRCUIT_SITE) yield Terms.UNDEFINED;
                if (site.privateName != null) yield privateIncDec(site, isIncrement, false, context);
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
                int slot = node.slot;
                Object[] frame;
                if (slot >= 0 && (frame = context.frame) != null && frame[slot] != SlotTable.UNDECLARED) {
                    Object oldValue = frame[slot];
                    if (oldValue == SlotTable.TDZ) {
                        throw SlotTable.tdzError(node.getText());
                    }
                    Object step = Terms.incDecStep(oldValue);
                    Object newValue = isIncrement ? Terms.add(oldValue, step, context) : Terms.min(oldValue, step, context);
                    context.updateSlot(slot, newValue, null);
                    yield newValue;
                }
                yield incDecRefByName(node, context, isIncrement, true);
            }
            case REF_DOT_EXPR, REF_BRACKET_EXPR -> {
                AccessSite site = resolveWriteSite(node, context);
                if (site == null || site == SHORT_CIRCUIT_SITE) yield Terms.UNDEFINED;
                if (site.privateName != null) yield privateIncDec(site, isIncrement, true, context);
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
                // a short-circuited chain leaves no reference to delete, which
                // per spec is a successful delete
                if (site == SHORT_CIRCUIT_SITE) yield true;
                if (site == null) yield false;
                yield deleteByKey(site.target, Terms.toPropertyKey(site.key), context, node);
            }
            default -> false;
        };
    }

    //=== Private implementation methods ===

    private static Object getRefExpr(Node node, CoreContext context, boolean functionCall) {
        int slot = node.slot;
        if (slot >= 0) {
            Object[] frame = context.frame;
            if (frame != null) {
                Object v = frame[slot];
                if (v != SlotTable.UNDECLARED) {
                    if (v == SlotTable.TDZ) {
                        throw SlotTable.tdzError(node.getText());
                    }
                    if (functionCall && context.root.bridge != null && v instanceof ExternalAccess ea) {
                        return externalConstructor(ea);
                    }
                    return v;
                }
            }
        }
        return getRefExprByName(node, context, functionCall);
    }

    // Name-keyed tail of getRefExpr, outlined to keep the slot fast path small
    // enough to inline reliably.
    private static Object getRefExprByName(Node node, CoreContext context, boolean functionCall) {
        String name = node.getText();
        if (context.hasKey(name)) {
            Object result = context.get(name);
            if (functionCall && context.root.bridge != null && result instanceof ExternalAccess ea) {
                return externalConstructor(ea);
            }
            return result;
        }
        throw JsErrorException.referenceError(name + " is not defined");
    }

    private static JsConstructor externalConstructor(ExternalAccess ea) {
        return (c, args) -> ea.construct(args);
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

        // `super.x` — look the property up on the parent prototype, but bind the
        // method receiver (`this`) to the current instance, not the prototype.
        if (node.getFirst().type == NodeType.SUPER_EXPR && node.get(1).type == NodeType.TOKEN) {
            ObjectLike superProto = Interpreter.evalSuperBase(context);
            if (outReceiver != null) outReceiver[1] = context.thisObject;
            return getByName(superProto, node.get(2).getText(), false, context, functionCall);
        }

        if (node.get(1).type == NodeType.TOKEN) {
            optional = node.get(1).token.type == TokenType.QUES_DOT;
            Node keyNode = node.get(2);
            if (keyNode.token.type == TokenType.PRIVATE_NAME) {
                // Private state is not a property: no bridge fallback, no prototype
                // walk, and the brand check is what reports a foreign receiver.
                PrivateName pn = PrivateAccess.resolve(keyNode.getText(), context);
                Object receiver = Interpreter.eval(node.getFirst(), context);
                if (receiver == SHORT_CIRCUITED) return SHORT_CIRCUITED;
                if (optional && (receiver == null || receiver == Terms.UNDEFINED)) return SHORT_CIRCUITED;
                if (outReceiver != null) outReceiver[1] = receiver;
                return PrivateAccess.get(receiver, pn, context);
            }
            name = keyNode.getText();
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
        Object result = getByIndex(object, index, false, context, functionCall);
        // PROPERTY_GET: a value read (not a method-call receiver fetch). Guarded so the
        // Object[] is only built when a listener is attached (bracket reads are hot).
        if (!functionCall && context.root.listener != null) {
            context.event(EventType.PROPERTY_GET, node, new Object[]{object, index, result});
        }
        return result;
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
            // Reading a property of null/undefined is a TypeError (or undefined under ?.).
            // Do NOT fall back to a same-named scope variable: `a.b.c` where `a.b` is null
            // must not silently resolve to a variable `c` that happens to be in scope. That
            // false resolution made `match obj.nullNode.id == '...'` pass by reading an
            // unrelated `id` binding (e.g. a Scenario-Outline Examples column) instead of
            // degrading to #notpresent.
            if (optional) return Terms.UNDEFINED;
            throw JsErrorException.typeError("cannot read properties of " + object + " (reading '" + name + "')");
        }

        if (object instanceof JsObject jsObj) {
            if (jsObj.containsKey(name)) {
                return jsObj.getMember(name, object, context);
            }
            Object result = jsObj.getMember(name, object, context);
            if (isFound(result)) return result;
            // JsValue wrappers may carry an original Java value (e.g. JsDate
            // from a ZonedDateTime); route the missed lookup through it via
            // the unified bridge fallback below so native methods like
            // ZonedDateTime.format remain callable. A plain JsObject without
            // an original returns UNDEFINED here — bridge access on a JS-only
            // object would expose wrapper internals and shadow the
            // intentionally-undefined property.
            if (!(object instanceof JsValue jv) || jv.getOriginalJavaValue() == null) {
                return Terms.UNDEFINED;
            }
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
            // The key is not in the map, so only the Object prototype can answer — `toString`,
            // `hasOwnProperty` and friends. This used to ask a fresh JsObject seeded with the
            // whole map, which copied every entry into a Slot to then miss on all of them: an
            // O(size) allocation on every missed key read, and `response.absentField` on a large
            // JSON body is exactly that. An empty probe reaches the same prototype chain, and the
            // receiver passed along is still the real map.
            Object result = PROTOTYPE_PROBE.getMember(name, object, context);
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

        // Unified bridge fallback. A JsValue wrapper exposing an original Java
        // value (JsDate from a ZonedDateTime / Instant / LocalDate*, etc.)
        // routes through it so native Java methods are reachable. Raw Java
        // objects (Map, List, POJO) route through the object itself.
        Object bridgeTarget = (object instanceof JsValue jv) ? jv.getOriginalJavaValue() : object;
        if (bridgeTarget == null) return Terms.UNDEFINED;
        return accessViaBridge(bridgeTarget, name, context, functionCall);
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
                accSlot.write(object, value, context, context.strict);
                return;
            }
            Object oldValue = objectLike.getMember(name);
            objectLike.putMember(name, value, context, context.strict);
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

    private static Object postIncDecByIndex(Object object, Object index, boolean isIncrement, CoreContext context) {
        if (index instanceof Number n) {
            int i = n.intValue();
            if (object instanceof List) {
                List<Object> list = (List<Object>) object;
                Object oldValue = i < list.size() ? list.get(i) : Terms.UNDEFINED;
                Object step = Terms.incDecStep(oldValue);
                Object newValue = isIncrement ? Terms.add(oldValue, step, context) : Terms.min(oldValue, step, context);
                list.set(i, newValue);
                firePropertySet(context, String.valueOf(i), newValue, oldValue, object, null);
                return oldValue;
            }
        }
        return postIncDecByName(object, Terms.toPropertyKey(index), isIncrement, context);
    }

    private static Object privateIncDec(AccessSite site, boolean isIncrement, boolean pre, CoreContext context) {
        Object oldValue = PrivateAccess.get(site.target, site.privateName, context);
        Object step = Terms.incDecStep(oldValue);
        Object newValue = isIncrement ? Terms.add(oldValue, step, context) : Terms.min(oldValue, step, context);
        PrivateAccess.set(site.target, site.privateName, newValue, context);
        return pre ? newValue : oldValue;
    }

    private static Object postIncDecByName(Object object, String name, boolean isIncrement, CoreContext context) {
        Object oldValue = getByName(object, name, false, context, false);
        Object step = Terms.incDecStep(oldValue);
        Object newValue = isIncrement ? Terms.add(oldValue, step, context) : Terms.min(oldValue, step, context);
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
                Object newValue = isIncrement ? Terms.add(oldValue, step, context) : Terms.min(oldValue, step, context);
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
        Object newValue = isIncrement ? Terms.add(oldValue, step, context) : Terms.min(oldValue, step, context);
        setByName(object, name, newValue, context, null);
        return newValue;
    }

    private static boolean deleteByKey(Object object, String key, CoreContext context, Node node) {
        Object oldValue = null;
        if (object instanceof ObjectLike ol) {
            oldValue = ol.getMember(key);
            ol.removeMember(key, context, context.strict);
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
            case MINUS_EQ -> Terms.min(oldValue, operand, context);
            case STAR_EQ -> Terms.mul(oldValue, operand, context);
            case SLASH_EQ -> Terms.div(oldValue, operand, context);
            case PERCENT_EQ -> Terms.mod(oldValue, operand, context);
            case STAR_STAR_EQ -> Terms.exp(oldValue, operand, context);
            case GT_GT_EQ -> Terms.bitShiftRight(oldValue, operand, context);
            case LT_LT_EQ -> Terms.bitShiftLeft(oldValue, operand, context);
            case GT_GT_GT_EQ -> Terms.bitShiftRightUnsigned(oldValue, operand, context);
            case AMP_EQ -> Terms.bitAnd(oldValue, operand, context);
            case PIPE_EQ -> Terms.bitOr(oldValue, operand, context);
            case CARET_EQ -> Terms.bitXor(oldValue, operand, context);
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
            if (functionCall) {
                return ja.getMethod(name);
            }
            // a property read that misses is the common case here — every `x.y` on a Java
            // object where y is not a member, and every absent key on a Map, lands on it. It
            // comes back as a value rather than as an exception this catch would discard.
            Object result = ja.getPropertyOrNotFound(name);
            return result == JavaUtils.NOT_FOUND ? Terms.UNDEFINED : result;
        } catch (Exception e) {
            return Terms.UNDEFINED;
        }
    }

}
