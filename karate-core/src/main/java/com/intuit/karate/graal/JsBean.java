/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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

import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 *
 * @author pthomas3
 */
public class JsBean implements ProxyObject {

    private final Value wrapped;

    public JsBean(Value wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public Object getMember(String key) {
        Value getter = findGetter(key);
        if (getter != null && getter.canExecute()) {
            return getter.execute();
        } else {
            return wrapped.getMember(key);
        }
    }

    @Override
    public Object getMemberKeys() {
        List<String> members = new ArrayList<>();
        for (String key : wrapped.getMemberKeys()) {
            if (findGetter(key) != null) {
                members.add(key);
            } else {
                members.add(key);
            }
        }
        return members;
    }

    @Override
    public boolean hasMember(String key) {
        return findGetter(key) != null || wrapped.hasMember(key);
    }

    @Override
    public void putMember(String key, Value value) {
        Value setter = findSetter(key);
        if (setter != null && setter.canExecute()) {
            setter.execute(value);
        } else {
            wrapped.putMember(key, value);
        }
    }

    private Value findGetter(String key) {
        Value getter = wrapped.getMember("get" + firstLetterUpperCase(key));
        if (getter == null) {
            getter = wrapped.getMember("is" + firstLetterUpperCase(key));
        }
        return getter;
    }

    private Value findSetter(String key) {
        return wrapped.getMember("set" + firstLetterUpperCase(key));
    }

    private static String firstLetterUpperCase(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1, name.length());
    }

}
