/*
 * The MIT License
 *
 * Copyright 2023 Karate Labs Inc.
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
package com.intuit.karate.playwright.driver;

import com.intuit.karate.Logger;

import java.io.Closeable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;

public class InvocationHandlers {


    /**
     * Handler that may be used with retry action operations.
     * Retries for actions must be signaled specifically by retry() and are disabled when the operation returns. 
     * 
     * @param delegate
     * @param options
     * @return
     */
    static InvocationHandler retryHandler(Object delegate, Integer count, Integer interval, PlaywrightDriverOptions options) {
        options.enableRetry(count, interval);
        return (proxy, method, args) -> {
            try {
                // no for (int i=0; i<options.getRetryCount()  loop. We will leverage PW's autowait and perform just one call with timeout count * interval)
                return method.invoke(delegate, args);
            } catch (InvocationTargetException ite) {
                throw ite.getCause();
            } finally {
                options.disableRetry();
            }
        };
    }

    static InvocationHandler submitHandler(Object delegate, Runnable waitingForPage) {
        return (proxy, method, args) -> {
            Object response = method.invoke(delegate, args);
            waitingForPage.run();
            if (waitingForPage instanceof Closeable) {
                ((Closeable) waitingForPage).close();
            }
            return response;
        };
    }

}
