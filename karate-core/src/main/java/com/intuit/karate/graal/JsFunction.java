/*
 * The MIT License
 *
 * Copyright 2023 Karate Labs Inc.
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
package com.intuit.karate.graal;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 *
 * @author peter
 */
public abstract class JsFunction implements ProxyObject {

    public static final Object LOCK = new Object();

    protected final Value value;

    protected JsFunction(Value v) {
        this.value = v;
    }

    public Value getValue() {
        return value;
    }

    @Override
    public void putMember(String key, Value value) {
        this.value.putMember(key, new JsValue(value).value);
    }

    @Override
    public boolean hasMember(String key) {
        return value.hasMember(key);
    }

    @Override
    public Object getMemberKeys() {
        return value.getMemberKeys().toArray(new String[0]);
    }

    @Override
    public Object getMember(String key) {
        return new JsValue(value.getMember(key)).value;
    }

    @Override
    public boolean removeMember(String key) {
        return value.removeMember(key);
    }

    protected static class Executable extends JsFunction implements ProxyExecutable {

        protected Executable(Value value) {
            super(value);
        }

        @Override
        public Object execute(Value... args) {
            Object[] newArgs = new Object[args.length];
            for (int i = 0; i < newArgs.length; i++) {
                newArgs[i] = JsValue.fromJava(args[i]);
            }
            synchronized (LOCK) {
                return new JsValue(value.execute(newArgs)).value;
            }
        }

    }

    protected static class Instantiable extends Executable implements ProxyInstantiable {

        protected Instantiable(Value value) {
            super(value);
        }

        @Override
        public Object newInstance(Value... args) {
            Object[] newArgs = new Object[args.length];
            for (int i = 0; i < newArgs.length; i++) {
                newArgs[i] = JsValue.fromJava(args[i]);
            }
            synchronized (LOCK) {
                return new JsValue(value.execute(newArgs)).value;
            }
        }

    }

}
