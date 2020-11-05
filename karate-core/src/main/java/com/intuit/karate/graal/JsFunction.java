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

import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class JsFunction {
    
    private static final Logger logger = LoggerFactory.getLogger(JsFunction.class);

    public final String source;
    public final Value value;

    public JsFunction(Value value) {
        this(value, value.toString());
    }

    public JsFunction(Value value, String source) {
        this.value = value;
        this.source = source;
    }

    public JsValue execute(JsEngine je, Object... args) {
        Value toInvoke;
        try {
            toInvoke = Value.asValue(value);
        } catch (Exception e) {
            logger.trace("FUNCTION context switch: {}", e.getMessage());
            toInvoke = je.evalForValue("(" + source + ")");            
        }
        for (int i = 0; i < args.length; i++) {
            args[i] = JsValue.fromJava(args[i]);
        }        
        Value result = toInvoke.execute(args);
        return new JsValue(result);
    }

    @Override
    public String toString() {
        return source;
    }        

}
