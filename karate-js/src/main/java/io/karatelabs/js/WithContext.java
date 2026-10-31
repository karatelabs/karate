/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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

/**
 * The object environment record of a {@code with} statement (§9.1.1.2): a name the
 * object has (HasProperty, minus {@code @@unscopables}) is that property, read and
 * written on the object; any other name is the enclosing context's. Bindings a block
 * inside the body declares sit in this context's own store and shadow the object. A
 * closure created in the body keeps this context as its lexical parent, so it sees the
 * object after the statement has completed, as the spec's environment chain does.
 * {@code var} declarations are the function's: their binding is hoisted there, and the
 * initializer's write resolves like any assignment.
 */
final class WithContext extends CoreContext {

    private final ObjectLike object;

    WithContext(CoreContext parent, Node node, ObjectLike object) {
        super(parent.root, parent, parent.depth, node, ContextScope.BLOCK, null);
        this.object = object;
    }

    private BindingSlot own(String key) {
        return bindings == null ? null : bindings.getSlot(key);
    }

    private boolean binds(String key) {
        if (!Terms.in(key, object, this)) {
            return false;
        }
        Object unscopables = object.getMember("@@unscopables");
        return !(unscopables instanceof ObjectLike u && Terms.isTruthy(u.getMember(key)));
    }

    @Override
    BindingSlot resolve(String key) {
        BindingSlot s = own(key);
        if (s != null) {
            return s;
        }
        if (binds(key)) {
            s = new BindingSlot(key, object.getMember(key, object, this), null, true, 0, null);
            s.withBase = object;
            return s;
        }
        if ("arguments".equals(key)) { // the function's object, which is no binding of its context
            Object a = parent.getOrNotFound(key);
            if (a != NOT_FOUND) {
                return new BindingSlot(key, a, null, true, 0, null);
            }
        }
        return parent.resolve(key);
    }

    @Override
    Object get(String key) {
        BindingSlot s = own(key);
        if (s != null) {
            return readSlot(s, key);
        }
        if (binds(key)) {
            return object.getMember(key, object, this);
        }
        return parent.get(key);
    }

    @Override
    Object getOrNotFound(String key) {
        BindingSlot s = own(key);
        if (s != null) {
            return readSlot(s, key);
        }
        if (binds(key)) {
            return object.getMember(key, object, this);
        }
        return parent.getOrNotFound(key);
    }

    @Override
    boolean hasKey(String key) {
        return own(key) != null || binds(key) || parent.hasKey(key);
    }

    @Override
    void update(String key, Object value, Node node) {
        if (own(key) != null) {
            super.update(key, value, node);
        } else if (binds(key)) {
            object.putMember(key, value, this, strict);
        } else {
            parent.update(key, value, node);
        }
    }

    @Override
    void declare(String key, Object value, BindScope scope, boolean initialized) {
        if (scope != null) {
            super.declare(key, value, scope, initialized);
        } else if (binds(key)) {
            object.putMember(key, value, this, strict);
        } else {
            parent.declare(key, value, null, initialized);
        }
    }

    @Override
    void declareVar(String key) {
        parent.declareVar(key);
    }

    @Override
    boolean deleteBinding(String key, Node node) {
        if (own(key) != null) {
            return false;
        }
        if (binds(key)) {
            return PropertyAccess.deleteByKey(object, key, this, node);
        }
        return parent.deleteBinding(key, node);
    }

}
