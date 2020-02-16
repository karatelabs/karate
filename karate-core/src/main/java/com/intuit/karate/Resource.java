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

import com.intuit.karate.core.ScenarioContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Resource {

    private final boolean file;
    private final Path path;
    private final int line;
    private final String relativePath;
    private final String packageQualifiedName;

    public static final Resource EMPTY = new Resource(Paths.get(""), "", -1);

    public Resource(File file, String relativePath) {
        this(file.toPath(), relativePath, -1);
    }

    public Resource(Path path, String relativePath, int line) {
        this.path = path;
        this.line = line;
        file = !path.toUri().getScheme().equals("jar");
        if (relativePath == null) {
            this.relativePath = FileUtils.toRelativeClassPath(path, Thread.currentThread().getContextClassLoader());
        } else {
            this.relativePath = relativePath;
        }
        packageQualifiedName = FileUtils.toPackageQualifiedName(this.relativePath);
    }

    public Resource(URL url) {
        this(FileUtils.urlToPath(url, null));
    }

    public Resource(Path path) {
        this(path, null, -1);
    }

    public Resource(ScenarioContext sc, String relativePath) {
        String strippedPath = FileUtils.removePrefix(relativePath);
        URL url = sc.getResource(strippedPath);
        if (url != null) {
            this.path = FileUtils.urlToPath(url, strippedPath);
        } else {
            this.path = new File(strippedPath).toPath();
        }
        this.line = -1;
        file = !path.toUri().getScheme().equals("jar");
        this.relativePath = relativePath;
        packageQualifiedName = FileUtils.toPackageQualifiedName(relativePath);
    }

    public String getFileNameWithoutExtension() {
        return FileUtils.removeFileExtension(path.getFileName().toString());
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

    public int getLine() {
        return line;
    }

    private static final Map<String, byte[]> STREAM_CACHE = new HashMap();

    public InputStream getStream() {
        try {
            if (file) {
                return new FileInputStream(path.toFile());
            } else {
                byte[] bytes = STREAM_CACHE.get(relativePath);
                if (bytes != null) {
                    return new ByteArrayInputStream(bytes);
                }
                synchronized (STREAM_CACHE) {
                    bytes = STREAM_CACHE.get(relativePath); // re-try
                    if (bytes != null) {
                        return new ByteArrayInputStream(bytes);
                    }
                    // since the nio newInputStream has concurrency problems :(
                    // plus a performance boost for karate-base.js if in JAR
                    InputStream tempStream = Files.newInputStream(path);
                    bytes = FileUtils.toBytes(tempStream);
                    STREAM_CACHE.put(relativePath, bytes);
                    return new ByteArrayInputStream(bytes);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getAsString() {
        return FileUtils.toString(getStream());
    }

    @Override
    public String toString() {
        return relativePath;
    }

}
