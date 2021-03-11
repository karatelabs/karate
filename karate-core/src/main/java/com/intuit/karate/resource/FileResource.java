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
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;

/**
 *
 * @author pthomas3
 */
public class FileResource implements Resource {

    private final File file;
    private final boolean classPath;
    private final String relativePath;

    public FileResource(File file) {
        this(file, false);
    }

    private FileResource(File file, boolean classpath) {
        this(file, classpath, file.getPath());
    }

    public FileResource(File file, boolean classPath, String relativePath) {
        this.file = file;
        this.classPath = classPath;
        this.relativePath = relativePath.replace('\\', '/');
    }

    @Override
    public boolean isFile() {
        return true;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public URI getUri() {
        return file.toPath().toUri();
    }

    @Override
    public boolean isClassPath() {
        return classPath;
    }

    @Override
    public String getRelativePath() {
        return relativePath;
    }

    @Override
    public Resource resolve(String path) {
        int pos = relativePath.lastIndexOf('/');
        String childPath;
        if (pos == -1) {
            childPath = path;
        } else {
            childPath = relativePath.substring(0, pos) + File.separator + path;
        }
        File child = new File(file.getAbsoluteFile().getParent() + File.separator + path);
        return new FileResource(child, classPath, childPath);
    }

    @Override
    public InputStream getStream() {
        try {
            return new FileInputStream(file);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return file.equals(obj);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }

    @Override
    public String toString() {
        return getPrefixedPath();
    }

}
