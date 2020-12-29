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
package com.intuit.karate.resource;

import com.intuit.karate.FileUtils;
import java.io.File;
import java.util.Set;

/**
 *
 * @author pthomas3
 */
public class DefaultResourceResolver implements ResourceResolver {

    private final boolean classpath;
    private final String root;
    private final Set<String> jsFiles;

    public DefaultResourceResolver(String root) {
        if (root == null) {
            root = "";
        }
        classpath = root.startsWith("classpath:");
        root = ResourceUtils.removePrefix(root);
        if (!root.isEmpty() && !root.endsWith("/")) {
            root = root + "/";
        }
        this.root = root;
        if (classpath) {
            jsFiles = ResourceUtils.findJsFilesInClassPath(root);
        } else {
            jsFiles = ResourceUtils.findJsFilesInDirectory(new File(root));
        }
    }

    @Override
    public Resource read(String path) {
        if (path.startsWith("classpath:") || path.startsWith("file:")) {
            // use path as-is
        } else {
            path = (classpath ? "classpath:" : "") + root + ResourceUtils.removePrefix(path);
        }
        return ResourceUtils.getResource(FileUtils.WORKING_DIR, path);
    }

    @Override
    public Set<String> jsfiles() {
        return jsFiles;
    }

}
