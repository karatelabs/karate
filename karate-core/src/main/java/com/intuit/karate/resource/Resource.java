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

import java.io.File;
import java.io.InputStream;
import java.net.URI;

/**
 *
 * @author pthomas3
 */
public interface Resource {
    
    public static final String CLASSPATH_COLON = "classpath:";
    public static final String FILE_COLON = "file:";
    public static final String ROOT_COLON = "root:";

    boolean isFile();

    boolean isClassPath();

    File getFile();

    URI getUri();

    String getRelativePath();

    Resource resolve(String path);

    default String getPrefixedPath() {
        return isClassPath() ? CLASSPATH_COLON + getRelativePath() : getRelativePath();
    }

    default String getPrefixedParentPath() {
        return ResourceUtils.getParentPath(getPrefixedPath());
    }

    default String getPackageQualifiedName() {
        String path = getRelativePath();
        if (path.endsWith(".feature")) {
            path = path.substring(0, path.length() - 8);
        }
        if (path.charAt(0) == '/') {
            path = path.substring(1);
        }
        return path.replace('/', '.').replaceAll("\\.[.]+", ".");
    }

    default String getFileNameWithoutExtension() {
        String path = getRelativePath();
        int pos = path.lastIndexOf('.');
        if (pos == -1) {
            return path;
        } else {
            return path.substring(0, pos);
        }
    }

    InputStream getStream();

}
