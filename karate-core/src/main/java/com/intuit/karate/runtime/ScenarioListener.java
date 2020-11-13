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
package com.intuit.karate.runtime;

import com.intuit.karate.graal.JsValue;
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
        logger.debug("init listener for: {}", value);
    }

    private Value function;

    private Value get() {
        if (function != null) {
            return function;
        }
        logger.debug("thread init");
        ScenarioEngine.set(child);
        logger.debug("before listener init");
        child.init();
        logger.debug("after listener init, before attach");
        function = child.attachSource(source);
        logger.debug("after attach");
        return function;
    }

    @Override
    public void accept(Object arg) {
        get().executeVoid(arg);
    }

    @Override
    public Object apply(Object arg) {
        Value result = get().execute(arg);
        return new JsValue(result).getValue();
    }

    @Override
    public void run() {
        get().executeVoid();
    }

}
