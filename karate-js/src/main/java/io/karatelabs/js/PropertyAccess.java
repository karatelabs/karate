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
            default -> throw new RuntimeException("cannot get from: " + node);
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
            case PAREN_EXPR -> new Object[]{Interpreter.eval(node.get(1), context), null};
            case FN_CALL_EXPR -> new Object[]{Interpreter.eval(node, context), null};
            default -> throw new RuntimeException("cannot call: " + node);
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
            case REF_DOT_EXPR -> setRefDotExpr(node, context, value, trackingNode);
            case REF_BRACKET_EXPR -> setRefBracketExpr(node, context, value, trackingNode);
            default -> throw new RuntimeException("cannot set on: " + node);
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
                Object newValue = applyOperator(oldValue, operator, operand);
                context.update(name, newValue, trackingNode);
                yield newValue;
            }
            case REF_DOT_EXPR -> compoundRefDotExpr(node, context, operator, operand, trackingNode);
            case REF_BRACKET_EXPR -> compoundRefBracketExpr(node, context, operator, operand, trackingNode);
            default -> throw new RuntimeException("cannot apply compound assignment to: " + node);
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
                Object newValue = isIncrement ? Terms.add(oldValue, 1) : new Terms(oldValue, 1).min();
                context.update(name, newValue);
                yield oldValue;
            }
            case REF_DOT_EXPR -> postIncDecRefDotExpr(node, context, isIncrement);
            case REF_BRACKET_EXPR -> postIncDecRefBracketExpr(node, context, isIncrement);
            case LIT_EXPR -> {
                // Handle literals like (x)++ where x is wrapped
                Object oldValue = Interpreter.eval(node, context);
                yield oldValue; // Can't actually modify a literal result
            }
            default -> throw new RuntimeException("cannot apply post inc/dec to: " + node);
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
                Object newValue = isIncrement ? Terms.add(oldValue, 1) : new Terms(oldValue, 1).min();
                context.update(name, newValue);
                yield newValue;
            }
            case REF_DOT_EXPR -> preIncDecRefDotExpr(node, context, isIncrement);
            case REF_BRACKET_EXPR -> preIncDecRefBracketExpr(node, context, isIncrement);
            default -> throw new RuntimeException("cannot apply pre inc/dec to: " + node);
        };
    }

    /**
     * Delete a property. Returns true on success.
     */
    static boolean delete(Node node, CoreContext context) {
                return switch (node.type) {
            case REF_EXPR -> false; // Can't delete variables
            case REF_DOT_EXPR -> deleteRefDotExpr(node, context);
            case REF_BRACKET_EXPR -> deleteRefBracketExpr(node, context);
            default -> false;
        };
    }

    //=== Private implementation methods ===

    private static Object getRefExpr(Node node, CoreContext context, boolean functionCall) {
        String name = node.getText();
        if (context.hasKey(name)) {
            Object result = context.get(name);
            if (functionCall && context.root.bridge != null && result instanceof ExternalAccess ea) {
                return (JsCallable) (c, args) -> ea.construct(args);
            }
            return result;
        }
        throw new RuntimeException(name + " is not defined");
    }

    private static Object getRefDotExpr(Node node, CoreContext context, boolean functionCall) {
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
                            return (JsCallable) (c, args) -> ja.construct(args);
                        }
                        return ja;
                    }
                    object = context.root.bridge.forType(base);
                } else {
                    object = null;
                }
                if (object == null) {
                    throw new RuntimeException("expression: " + node.getFirst().getText() + " - " + e.getMessage());
                }
            }
        } else {
            optional = true;
            if (node.get(1).type == NodeType.REF_BRACKET_EXPR) {
                object = Interpreter.eval(node.getFirst(), context);
                Object index = Interpreter.eval(node.get(1).get(2), context);
                return getByIndex(object, index, optional, context, functionCall);
            } else {
                object = Interpreter.eval(node.getFirst(), context);
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
            if (functionCall && context.root.bridge != null && object instanceof ExternalAccess ea) {
                return (JsCallable) (c, args) -> ea.construct(args);
            }
            return object;
        }

        return getByName(object, name, optional, context, functionCall);
    }

    private static Object getRefBracketExpr(Node node, CoreContext context, boolean functionCall) {
        Object object = Interpreter.eval(node.getFirst(), context);
        Object index = Interpreter.eval(node.get(2), context);
        return getByIndex(object, index, false, context, functionCall);
    }

    private static Object[] getCallableRefDotExpr(Node node, CoreContext context) {
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
                        return new Object[]{(JsCallable) (c, args) -> ja.construct(args), null};
                    }
                    object = context.root.bridge.forType(base);
                } else {
                    object = null;
                }
                if (object == null) {
                    throw new RuntimeException("expression: " + node.getFirst().getText() + " - " + e.getMessage());
                }
            }
            // Handle optional chaining: obj?.method() where obj is null/undefined
            if (optional && (object == null || object == Terms.UNDEFINED)) {
                return new Object[]{Terms.UNDEFINED, null};
            }
        } else {
            optional = true;
            if (node.get(1).type == NodeType.REF_BRACKET_EXPR) {
                object = Interpreter.eval(node.getFirst(), context);
                if (optional && (object == null || object == Terms.UNDEFINED)) {
                    return new Object[]{Terms.UNDEFINED, null};
                }
                Object index = Interpreter.eval(node.get(1).get(2), context);
                return new Object[]{getByIndex(object, index, true, context, true), object};
            } else {
                object = Interpreter.eval(node.getFirst(), context);
                if (optional && (object == null || object == Terms.UNDEFINED)) {
                    return new Object[]{Terms.UNDEFINED, null};
                }
                name = null;
            }
        }

        Object jsValue = Terms.toJsValue(object);
        if (jsValue != null) {
            object = jsValue;
        }

        if (name == null) {
            if (context.root.bridge != null && object instanceof ExternalAccess ea) {
                return new Object[]{(JsCallable) (c, args) -> ea.construct(args), null};
            }
            return new Object[]{object, null};
        }

        return new Object[]{getByName(object, name, optional, context, true), object};
    }

    private static Object[] getCallableRefBracketExpr(Node node, CoreContext context) {
        Object object = Interpreter.eval(node.getFirst(), context);
        Object index = Interpreter.eval(node.get(2), context);
        return new Object[]{getByIndex(object, index, false, context, true), object};
    }

    private static Object getByIndex(Object object, Object index, boolean optional,
                                      CoreContext context, boolean functionCall) {
        if (!functionCall && index instanceof Number n) {
            int i = n.intValue();
            if (object == null || object == Terms.UNDEFINED) {
                if (optional) return Terms.UNDEFINED;
                throw new RuntimeException("cannot read properties of " + object + " (reading '[" + i + "]')");
            }
            if (object instanceof JsArray array) {
                return array.getElement(i);
            }
            if (object instanceof List<?> list) {
                if (i < 0 || i >= list.size()) return Terms.UNDEFINED;
                return list.get(i);
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
                return jsArray.getElement(i);
            }
            if (object instanceof Map || object instanceof ObjectLike) {
                return getByName(object, String.valueOf(index), optional, context, functionCall);
            }
            throw new RuntimeException("get by index [" + i + "] for non-array: " + object);
        }
        return getByName(object, String.valueOf(index), optional, context, functionCall);
    }

    private static Object getByName(Object object, String name, boolean optional,
                                     CoreContext context, boolean functionCall) {
        if (object == null || object == Terms.UNDEFINED) {
            if (context.hasKey(name)) {
                Object result = context.get(name);
                if (functionCall && context.root.bridge != null && result instanceof ExternalAccess ea) {
                    return (JsCallable) (c, args) -> ea.construct(args);
                }
                return result;
            }
            if (optional) return Terms.UNDEFINED;
            throw new RuntimeException("cannot read properties of " + object + " (reading '" + name + "')");
        }

        if (object instanceof JsObject jsObj) {
            if (jsObj.containsKey(name)) {
                return jsObj.getMember(name);
            }
            Object result = jsObj.getMember(name);
            if (isFound(result)) return result;
            return Terms.UNDEFINED;
        } else if (object instanceof JsArray jsArr) {
            Object result = jsArr.getMember(name);
            if (isFound(result)) return result;
            return Terms.UNDEFINED;
        } else if (object instanceof ObjectLike ol) {
            Object result = ol.getMember(name);
            if (isFound(result)) return result;
        } else if (object instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) object;
            if (map.containsKey(name)) return map.get(name);
            Object result = new JsObject(map).getMember(name);
            if (result != null) return result;
        } else if (object instanceof List) {
            ObjectLike ol = Terms.toObjectLike(object);
            if (ol != null) {
                Object result = ol.getMember(name);
                if (isFound(result)) return result;
            }
        } else {
            ObjectLike ol = Terms.toObjectLike(object);
            if (ol != null) {
                Object result = ol.getMember(name);
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

    private static void setRefDotExpr(Node node, CoreContext context, Object value, Node trackingNode) {
        String name;
        Object object;

        if (node.get(1).type == NodeType.TOKEN) {
            name = node.get(2).getText();
            object = Interpreter.eval(node.getFirst(), context);
        } else if (node.get(1).type == NodeType.REF_BRACKET_EXPR) {
            object = Interpreter.eval(node.getFirst(), context);
            Object index = Interpreter.eval(node.get(1).get(2), context);
            setByIndex(object, index, value, context, trackingNode);
            return;
        } else {
            throw new RuntimeException("cannot set on optional call expression");
        }

        setByName(object, name, value, context, trackingNode);
    }

    private static void setRefBracketExpr(Node node, CoreContext context, Object value, Node trackingNode) {
        Object object = Interpreter.eval(node.getFirst(), context);
        Object index = Interpreter.eval(node.get(2), context);
        setByIndex(object, index, value, context, trackingNode);
    }

    private static void setByIndex(Object object, Object index, Object value, CoreContext context, Node trackingNode) {
        if (index instanceof Number n) {
            int i = n.intValue();
            if (object instanceof List) {
                List<Object> list = (List<Object>) object;
                Object oldValue = i < list.size() ? list.get(i) : Terms.UNDEFINED;
                list.set(i, value);
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
        setByName(object, String.valueOf(index), value, context, trackingNode);
    }

    private static void setByName(Object object, String name, Object value, CoreContext context, Node trackingNode) {
        if (name == null) {
            throw new RuntimeException("unexpected set [null]:" + value + " on: " + object);
        }
        if (object == null) {
            context.update(name, value, trackingNode);
        } else if (object instanceof ObjectLike objectLike) {
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
                throw new RuntimeException("cannot set '" + name + "'");
            }
        } else {
            throw new RuntimeException("cannot set '" + name + "'");
        }
    }

    private static void firePropertySet(CoreContext context, String name, Object value, Object oldValue, Object target, Node node) {
        if (context.root.listener != null) {
            context.root.listener.onBind(BindEvent.propertySet(name, value, oldValue, target, context, node));
        }
    }

    //=== Compound operation implementations ===

    private static Object compoundRefDotExpr(Node node, CoreContext context, TokenType operator, Object operand, Node trackingNode) {
        String name;
        Object object;

        if (node.get(1).type == NodeType.TOKEN) {
            name = node.get(2).getText();
            object = Interpreter.eval(node.getFirst(), context);
        } else if (node.get(1).type == NodeType.REF_BRACKET_EXPR) {
            object = Interpreter.eval(node.getFirst(), context);
            Object index = Interpreter.eval(node.get(1).get(2), context);
            return compoundByIndex(object, index, operator, operand, context, trackingNode);
        } else {
            throw new RuntimeException("cannot apply compound assignment to optional call");
        }

        return compoundByName(object, name, operator, operand, context, trackingNode);
    }

    private static Object compoundRefBracketExpr(Node node, CoreContext context, TokenType operator, Object operand, Node trackingNode) {
        Object object = Interpreter.eval(node.getFirst(), context);
        Object index = Interpreter.eval(node.get(2), context);
        return compoundByIndex(object, index, operator, operand, context, trackingNode);
    }

    private static Object compoundByIndex(Object object, Object index, TokenType operator, Object operand, CoreContext context, Node trackingNode) {
        if (index instanceof Number n) {
            int i = n.intValue();
            if (object instanceof List) {
                List<Object> list = (List<Object>) object;
                Object oldValue = i < list.size() ? list.get(i) : Terms.UNDEFINED;
                Object newValue = applyOperator(oldValue, operator, operand);
                list.set(i, newValue);
                firePropertySet(context, String.valueOf(i), newValue, oldValue, object, trackingNode);
                return newValue;
            }
        }
        return compoundByName(object, String.valueOf(index), operator, operand, context, trackingNode);
    }

    private static Object compoundByName(Object object, String name, TokenType operator, Object operand, CoreContext context, Node trackingNode) {
        Object oldValue = getByName(object, name, false, context, false);
        Object newValue = applyOperator(oldValue, operator, operand);
        setByName(object, name, newValue, context, trackingNode);
        return newValue;
    }

    private static Object postIncDecRefDotExpr(Node node, CoreContext context, boolean isIncrement) {
        String name;
        Object object;

        if (node.get(1).type == NodeType.TOKEN) {
            name = node.get(2).getText();
            object = Interpreter.eval(node.getFirst(), context);
        } else if (node.get(1).type == NodeType.REF_BRACKET_EXPR) {
            object = Interpreter.eval(node.getFirst(), context);
            Object index = Interpreter.eval(node.get(1).get(2), context);
            return postIncDecByIndex(object, index, isIncrement, context);
        } else {
            throw new RuntimeException("cannot apply inc/dec to optional call");
        }

        return postIncDecByName(object, name, isIncrement, context);
    }

    private static Object postIncDecRefBracketExpr(Node node, CoreContext context, boolean isIncrement) {
        Object object = Interpreter.eval(node.getFirst(), context);
        Object index = Interpreter.eval(node.get(2), context);
        return postIncDecByIndex(object, index, isIncrement, context);
    }

    private static Object postIncDecByIndex(Object object, Object index, boolean isIncrement, CoreContext context) {
        if (index instanceof Number n) {
            int i = n.intValue();
            if (object instanceof List) {
                List<Object> list = (List<Object>) object;
                Object oldValue = i < list.size() ? list.get(i) : Terms.UNDEFINED;
                Object newValue = isIncrement ? Terms.add(oldValue, 1) : new Terms(oldValue, 1).min();
                list.set(i, newValue);
                firePropertySet(context, String.valueOf(i), newValue, oldValue, object, null);
                return oldValue;
            }
        }
        return postIncDecByName(object, String.valueOf(index), isIncrement, context);
    }

    private static Object postIncDecByName(Object object, String name, boolean isIncrement, CoreContext context) {
        Object oldValue = getByName(object, name, false, context, false);
        Object newValue = isIncrement ? Terms.add(oldValue, 1) : new Terms(oldValue, 1).min();
        setByName(object, name, newValue, context, null);
        return oldValue;
    }

    private static Object preIncDecRefDotExpr(Node node, CoreContext context, boolean isIncrement) {
        String name;
        Object object;

        if (node.get(1).type == NodeType.TOKEN) {
            name = node.get(2).getText();
            object = Interpreter.eval(node.getFirst(), context);
        } else if (node.get(1).type == NodeType.REF_BRACKET_EXPR) {
            object = Interpreter.eval(node.getFirst(), context);
            Object index = Interpreter.eval(node.get(1).get(2), context);
            return preIncDecByIndex(object, index, isIncrement, context);
        } else {
            throw new RuntimeException("cannot apply inc/dec to optional call");
        }

        return preIncDecByName(object, name, isIncrement, context);
    }

    private static Object preIncDecRefBracketExpr(Node node, CoreContext context, boolean isIncrement) {
        Object object = Interpreter.eval(node.getFirst(), context);
        Object index = Interpreter.eval(node.get(2), context);
        return preIncDecByIndex(object, index, isIncrement, context);
    }

    private static Object preIncDecByIndex(Object object, Object index, boolean isIncrement, CoreContext context) {
        if (index instanceof Number n) {
            int i = n.intValue();
            if (object instanceof List) {
                List<Object> list = (List<Object>) object;
                Object oldValue = i < list.size() ? list.get(i) : Terms.UNDEFINED;
                Object newValue = isIncrement ? Terms.add(oldValue, 1) : new Terms(oldValue, 1).min();
                list.set(i, newValue);
                firePropertySet(context, String.valueOf(i), newValue, oldValue, object, null);
                return newValue;
            }
        }
        return preIncDecByName(object, String.valueOf(index), isIncrement, context);
    }

    private static Object preIncDecByName(Object object, String name, boolean isIncrement, CoreContext context) {
        Object oldValue = getByName(object, name, false, context, false);
        Object newValue = isIncrement ? Terms.add(oldValue, 1) : new Terms(oldValue, 1).min();
        setByName(object, name, newValue, context, null);
        return newValue;
    }

    private static boolean deleteRefDotExpr(Node node, CoreContext context) {
        String name;
        Object object;

        if (node.get(1).type == NodeType.TOKEN) {
            name = node.get(2).getText();
            object = Interpreter.eval(node.getFirst(), context);
        } else if (node.get(1).type == NodeType.REF_BRACKET_EXPR) {
            object = Interpreter.eval(node.getFirst(), context);
            Object index = Interpreter.eval(node.get(1).get(2), context);
            return deleteByKey(object, String.valueOf(index), context, node);
        } else {
            return false;
        }

        return deleteByKey(object, name, context, node);
    }

    private static boolean deleteRefBracketExpr(Node node, CoreContext context) {
        Object object = Interpreter.eval(node.getFirst(), context);
        Object index = Interpreter.eval(node.get(2), context);
        return deleteByKey(object, String.valueOf(index), context, node);
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

    private static Object applyOperator(Object oldValue, TokenType operator, Object operand) {
        return switch (operator) {
            case PLUS_EQ -> Terms.add(oldValue, operand);
            case MINUS_EQ -> new Terms(oldValue, operand).min();
            case STAR_EQ -> new Terms(oldValue, operand).mul();
            case SLASH_EQ -> new Terms(oldValue, operand).div();
            case PERCENT_EQ -> new Terms(oldValue, operand).mod();
            case STAR_STAR_EQ -> new Terms(oldValue, operand).exp();
            case GT_GT_EQ -> new Terms(oldValue, operand).bitShiftRight();
            case LT_LT_EQ -> new Terms(oldValue, operand).bitShiftLeft();
            case GT_GT_GT_EQ -> new Terms(oldValue, operand).bitShiftRightUnsigned();
            default -> throw new RuntimeException("unexpected operator: " + operator);
        };
    }

    private static boolean isFound(Object result) {
        return result != null && result != Terms.UNDEFINED;
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
