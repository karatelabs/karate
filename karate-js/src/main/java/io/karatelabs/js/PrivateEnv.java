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

import java.util.HashMap;
import java.util.Map;

/**
 * The spec's PrivateEnvironment: the {@code #name} to {@link PrivateName} bindings
 * introduced by one class evaluation, chained to the environment that enclosed it.
 * A function captures the environment in effect where it was declared
 * ({@link JsFunctionNode#privateEnv}) and re-installs it on its call frame, so
 * resolution is lexical and a callback invoked from inside a class method does not
 * inherit that class's private names.
 * <p>
 * Only classes that actually declare private names allocate one, so ordinary code
 * carries a null reference and pays nothing.
 */
final class PrivateEnv {

    private final PrivateEnv parent;
    private final Map<String, PrivateName> names = new HashMap<>(4);

    PrivateEnv(PrivateEnv parent) {
        this.parent = parent;
    }

    PrivateName declare(String name, PrivateName.Kind kind) {
        PrivateName existing = names.get(name);
        if (existing != null) { // a get/set pair shares one name (parser rejects other duplicates)
            return existing;
        }
        PrivateName pn = new PrivateName(name, kind);
        names.put(name, pn);
        return pn;
    }

    PrivateName resolve(String name) {
        for (PrivateEnv e = this; e != null; e = e.parent) {
            PrivateName pn = e.names.get(name);
            if (pn != null) {
                return pn;
            }
        }
        return null;
    }

}
