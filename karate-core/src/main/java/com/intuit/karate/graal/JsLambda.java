/*
 * The MIT License
 *
 * Copyright 2021 Intuit Inc.
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

import com.intuit.karate.graal.JsValue.SharableMembersAndInstantiable;
import java.util.function.Consumer;
import java.util.function.Function;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class JsLambda extends SharableMembersAndInstantiable implements Consumer, Function, Runnable {

    private static final Logger logger = LoggerFactory.getLogger(JsLambda.class);

    public JsLambda(Value v) {
        super(v);
    }

    @Override
    public void accept(Object arg) {
        JsEngine.execute(this, arg);
    }

    @Override
    public Object apply(Object arg) {
        return JsEngine.execute(this, arg);
    }

    @Override
    public void run() {
        JsEngine.execute(this);
    }

}
