/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.js;

/**
 * Provides reflection-like awareness for callables about their invocation context.
 * <p>
 * This solves the JavaScript quirk where built-in constructors like Number, String, and Boolean
 * behave differently when called with vs without the 'new' keyword:
 * <ul>
 *   <li>{@code Number(5)} returns primitive 5</li>
 *   <li>{@code new Number(5)} returns a boxed Number object</li>
 * </ul>
 * <p>
 * Callables can check {@code context.getCallInfo()} to determine how they were invoked
 * and return the appropriate value. For normal function calls, {@code getCallInfo()}
 * returns null (no overhead). CallInfo is only created for special invocation contexts.
 * <p>
 * Future extensions could include:
 * <ul>
 *   <li>Tracking {@code .call()} and {@code .apply()} invocations</li>
 *   <li>Supporting proper Date, Promise, or RegExp construction</li>
 *   <li>Enabling better error messages with call-site information</li>
 * </ul>
 *
 * @see Context#getCallInfo()
 */
public class CallInfo {

    /** True if invoked with the 'new' keyword */
    public final boolean constructor;

    /** The function or callable being invoked */
    public final Object called;

    CallInfo(boolean constructor, Object called) {
        this.constructor = constructor;
        this.called = called;
    }

}
