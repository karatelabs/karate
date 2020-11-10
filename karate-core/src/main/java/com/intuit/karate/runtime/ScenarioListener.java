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

/**
 *
 * @author pthomas3
 */
public class ScenarioListener implements Consumer, Function {

    private final ScenarioEngine parent;
    private final ScenarioEngine child;
    private final CharSequence source;

    private Value function;

    public ScenarioListener(ScenarioEngine parent, Value function) {
        this.parent = parent;
        this.child = parent.child();
        source = function.getSourceLocation().getCharacters();
    }

    private Value get() {
        if (function != null) {
            return function;
        }
        ScenarioEngine.set(child);
        child.init();
        function = child.attachSource("(" + source + ")");
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

}
