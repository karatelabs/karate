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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author pthomas3
 */
public class MethodPattern {

    public final String regex;
    public final Method method;
    private final Pattern pattern;

    MethodPattern(Method method, String regex) {
        this.regex = regex;
        this.method = method;
        try {
            pattern = Pattern.compile(regex);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }    

    public List<String> match(String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.lookingAt()) {
            List<String> args = new ArrayList(matcher.groupCount());
            for (int i = 1; i <= matcher.groupCount(); i++) {
                int startIndex = matcher.start(i);
                args.add(startIndex == -1 ? null : matcher.group(i));
            }
            return args;
        } else {
            return null;
        }
    } 
    
    @Override
    public String toString() {
        return "\n" + pattern + " " + method.toGenericString();
    }    

}
