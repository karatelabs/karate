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

public class BindEvent {

    public final BindType type;
    public final String name;
    public final Object value;
    public final Object oldValue;
    public final BindScope scope;
    public final Object target;
    public final Context context;
    public final Node node;

    BindEvent(BindType type, String name, Object value, Object oldValue,
              BindScope scope, Object target, Context context, Node node) {
        this.type = type;
        this.name = name;
        this.value = value;
        this.oldValue = oldValue;
        this.scope = scope;
        this.target = target;
        this.context = context;
        this.node = node;
    }

    // Factory methods for common cases

    static BindEvent declare(String name, Object value, BindScope scope, Context context, Node node) {
        return new BindEvent(BindType.DECLARE, name, value, null, scope, null, context, node);
    }

    static BindEvent assign(String name, Object value, Object oldValue, Context context, Node node) {
        return new BindEvent(BindType.ASSIGN, name, value, oldValue, null, null, context, node);
    }

    static BindEvent delete(String name, Object oldValue, Context context, Node node) {
        return new BindEvent(BindType.DELETE, name, null, oldValue, null, null, context, node);
    }

    static BindEvent propertySet(String name, Object value, Object oldValue, Object target, Context context, Node node) {
        return new BindEvent(BindType.PROPERTY_SET, name, value, oldValue, null, target, context, node);
    }

    static BindEvent propertyDelete(String name, Object oldValue, Object target, Context context, Node node) {
        return new BindEvent(BindType.PROPERTY_DELETE, name, null, oldValue, null, target, context, node);
    }

}
