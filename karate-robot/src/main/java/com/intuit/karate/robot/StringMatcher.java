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
package com.intuit.karate.robot;

import java.util.function.Predicate;

/**
 *
 * @author pthomas3
 */
public class StringMatcher implements Predicate<String> {
    
    private final String orig;
    private final String text;
    private final Predicate<String> pred;
    
    public StringMatcher(String raw) {
        this.orig = raw;
        if (raw.startsWith("^")) {
            text = raw.substring(1);
            pred = s -> s.contains(text);
        } else if (raw.startsWith("~")) {
            text = raw.substring(1);
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(text);
            pred = s -> pattern.matcher(s).find();
        } else {
            text = raw;
            pred = s -> s.equals(text);            
        }        
    }

    @Override
    public boolean test(String t) {
        return pred.test(t);
    }

    @Override
    public String toString() {
        return orig;
    }        
    
}
