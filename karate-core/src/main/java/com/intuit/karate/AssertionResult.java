/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class AssertionResult {

    public final String message;
    public final boolean pass;

    public static final AssertionResult PASS = new AssertionResult(true, null);

    private AssertionResult(boolean pass, String message) {
        this.pass = pass;
        this.message = message;
    }

    public static AssertionResult fail(String message) {
        return new AssertionResult(false, message);
    }

    @Override
    public String toString() {
        return pass ? "passed" : "assertion failed: " + message;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap(2);
        map.put("pass", pass);
        map.put("message", message);
        return map;
    }

}
