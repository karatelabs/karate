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
package io.karatelabs.driver;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Driver} with no browser behind it: {@code script(String)} records the JS it was
 * handed and answers with {@link #scriptResult}, every wait that answers with an element
 * answers with {@link #waitForResult}, and every other method runs its real interface default (so the
 * element operations still compose down to script calls) or returns a zero value.
 */
class DriverStub implements InvocationHandler {

    final List<String> scripts = new ArrayList<>();
    final List<String> calls = new ArrayList<>();

    Object scriptResult;
    Element waitForResult;

    final Driver driver = (Driver) Proxy.newProxyInstance(
            Driver.class.getClassLoader(), new Class<?>[]{Driver.class}, this);

    // the action defaults consult the options (e.g. the pending-submit hash) before returning
    private final DriverOptions options = (DriverOptions) Proxy.newProxyInstance(
            DriverOptions.class.getClassLoader(), new Class<?>[]{DriverOptions.class},
            (proxy, method, args) -> null);

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        calls.add(name);
        if ("script".equals(name) && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == String.class) {
            scripts.add((String) args[0]);
            return scriptResult;
        }
        if (name.startsWith("wait") && method.getReturnType() == Element.class) {
            return waitForResult;
        }
        if ("getOptions".equals(name)) {
            return options;
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        Class<?> returnType = method.getReturnType();
        return returnType.isPrimitive() && returnType != void.class
                ? Array.get(Array.newInstance(returnType, 1), 0) : null;
    }

}
