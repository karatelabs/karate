/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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

import com.intuit.karate.StringUtils;
import java.lang.reflect.Method;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class MethodMatch {

    public final Method method;
    private final List<String> args;

    public MethodMatch(Method method, List<String> args) {
        this.method = method;
        this.args = args;
    }
    
    public Object[] convertArgs(Object last) {
        Class[] types = method.getParameterTypes();        
        Object[] result = new Object[types.length];
        int i = 0;
        for (String arg : args) {
            Class type = types[i];
            if (List.class.isAssignableFrom(type)) {
                result[i] = StringUtils.split(arg, ',', false);
            } else if (int.class.isAssignableFrom(type)) {
                result[i] = Integer.valueOf(arg);
            } else { // string
                result[i] = arg;
            }
            i++;
        }
        if (last != null) {
            result[i] = last;
        }
        return result;
    }

    @Override
    public String toString() {
        return method + " " + args;
    }        
    
}
