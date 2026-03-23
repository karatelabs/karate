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
package io.karatelabs.markup;

import io.karatelabs.common.Resource;

/**
 * Resource resolver that resolves paths relative to a configured root.
 * The root can be a file system path or a classpath location.
 */
public class RootResourceResolver implements ResourceResolver {

    private final boolean classpath;
    final String root;

    /**
     * Creates a resolver with the specified root path.
     * Supports "classpath:" prefix for classpath resources.
     *
     * @param root the root path (e.g., "src/main", "classpath:resources")
     */
    public RootResourceResolver(String root) {
        if (root == null) {
            root = "";
        }
        classpath = root.startsWith(Resource.CLASSPATH_COLON);
        root = Resource.removePrefix(root);
        String SLASH = "/";
        if (!root.isEmpty() && !root.endsWith(SLASH)) {
            root = root + SLASH;
        }
        this.root = root;
    }

    @Override
    public Resource resolve(String path, Resource caller) {
        if (path.startsWith(Resource.CLASSPATH_COLON)) {
            return Resource.path(path);
        }
        String basePath = classpath ? Resource.CLASSPATH_COLON + root : root;
        if (path.startsWith(Resource.THIS_COLON) && caller != null) {
            return caller.resolve(path.substring(Resource.THIS_COLON.length()));
        }
        return Resource.path(basePath + (path.charAt(0) == '/' ? path.substring(1) : path));
    }

}
