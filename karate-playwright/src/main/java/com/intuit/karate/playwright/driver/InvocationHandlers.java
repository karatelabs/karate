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

    static InvocationHandler retryHandler(Object delegate, int count, int interval, Logger logger, PlaywrightDriver driver) {
        driver.retryTimeout((double) interval);
        return (proxy, method, args) -> {
            long start = System.currentTimeMillis();
            try {
                for (int i = 0; i < count; i++) {
                    try {
                        logger.debug("{} - retry #{}", method.getName(), i);

                        Object response = method.invoke(delegate, args);
                        if (response != null) {
                            return response;
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {

                    }
                }
            } finally {
                driver.retryTimeout(null);
            }
            String message = method + ": failed after " + (count - 1) + " retries and " + (System.currentTimeMillis() - start) + " milliseconds";
            logger.warn(message);
            throw new RuntimeException(message);
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
