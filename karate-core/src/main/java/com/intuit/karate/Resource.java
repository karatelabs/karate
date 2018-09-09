/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 *
 * @author pthomas3
 */
public class Resource {

    private final boolean file;
    private final Path path;
    private final String relativePath;
    private final String packageQualifiedName;

    public Resource(File file, String relativePath) {
        this(file.toPath(), relativePath);
    }

    public Resource(Path path, String relativePath) {
        this.path = path;
        file = !path.toUri().getScheme().equals("jar");
        this.relativePath = relativePath;
        packageQualifiedName = FileUtils.toPackageQualifiedName(relativePath);
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getPackageQualifiedName() {
        return packageQualifiedName;
    }        

    public Path getPath() {
        return path;
    }

    public InputStream getStream() {
        try {
            if (file) {
                return new FileInputStream(path.toFile());
            } else {
                return Files.newInputStream(path);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getAsString() {
        if (file) {
            return FileUtils.toString(path.toFile());
        } else {
            return FileUtils.toString(getStream());
        }
    }

    @Override
    public String toString() {
        return relativePath;
    }

}
