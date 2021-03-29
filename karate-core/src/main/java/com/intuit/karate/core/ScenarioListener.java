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
package com.intuit.karate.core;

import com.intuit.karate.graal.JsValue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ScenarioListener implements Consumer, Function, Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ScenarioListener.class);

    private final ScenarioEngine parent;
    private final ScenarioEngine child;
    private final CharSequence source;

    public ScenarioListener(ScenarioEngine parent, Value value) {
        this.parent = parent;
        this.child = parent.child();
        source = value.getSourceLocation().getCharacters();
    }

    private Value function;

    private void init() {
        if (function == null) {
            try { // TODO remove this after all the fixes for #1515
                long startTime = System.currentTimeMillis();
                parent.runtime.ASYNC_SEMAPHORE.tryAcquire(500, TimeUnit.MILLISECONDS);
                logger.debug("[listener-init] async lock waited {} ms", System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                logger.warn("[listener-init] async lock failed: {}", e.getMessage());
            }
            ScenarioEngine.set(child);
            child.init();
            function = child.attachSource(source);
            parent.runtime.ASYNC_SEMAPHORE.release();
        }
    }

    @Override
    public void accept(Object arg) {
        init();
        synchronized (parent.JS.context) {
            function.executeVoid(JsValue.fromJava(arg));
        }
    }

    @Override
    public Object apply(Object arg) {
        init();
        synchronized (parent.JS.context) {
            Value result = function.execute(JsValue.fromJava(arg));
            return new JsValue(result).getValue();
        }
    }

    @Override
    public void run() {
        init();
        synchronized (parent.JS.context) {
            function.executeVoid();
        }
    }

}
