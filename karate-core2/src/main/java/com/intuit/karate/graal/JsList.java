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

import java.util.Collections;
import java.util.List;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

/**
 *
 * @author pthomas3
 */
public class JsList implements ProxyArray {

    public static final JsList EMPTY = new JsList(Collections.EMPTY_LIST);

    private final List list;

    public JsList(List list) {
        this.list = list;
    }

    public List getList() {
        return list;
    }

    @Override
    public Object get(long index) {
        return JsValue.fromJava(list.get((int) index));
    }

    @Override
    public void set(long index, Value value) {
        if (index >= list.size()) {
            list.add(null); // support js push()
        }
        list.set((int) index, JsValue.toJava(value));
    }

    @Override
    public long getSize() {
        return list.size();
    }

    @Override
    public boolean remove(long index) {
        list.remove((int) index);
        return true;
    }

    @Override
    public String toString() {
        return list.toString();
    }

}
