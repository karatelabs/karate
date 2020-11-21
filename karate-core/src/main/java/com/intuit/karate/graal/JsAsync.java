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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import static com.intuit.karate.graal.JsValue.*;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class JsAsync implements Supplier, Function, Consumer, Runnable, Methods.FunVar {

    private static final Logger logger = LoggerFactory.getLogger(JsAsync.class);

    private final Value value;

    public static JsAsync of(Value value) {
        return new JsAsync(value);
    }

    private JsAsync(Value value) {
        this.value = value;
    }

    @Override
    public Object get() {
        synchronized (this) {
            return toJava(value.execute());
        }
    }

    @Override
    public Object apply(Object t) {
        synchronized (this) {
            return toJava(value.execute(fromJava(t)));
        }
    }

    @Override
    public void accept(Object t) {
        logger.debug("before sync");
        synchronized (this) {
            value.executeVoid(fromJava(t));
        }
        logger.debug("after sync");
    }

    @Override
    public void run() {
        synchronized (this) {
            value.executeVoid();
        }
    }

    @Override
    public Object call(Object... args) {
        synchronized (this) {
            for (int i = 0; i < args.length; i++) {
                args[i] = fromJava(args[i]);
            }
            return toJava(value.execute(args));
        }
    }

}
