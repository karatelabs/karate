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
package com.intuit.karate.http;

import com.intuit.karate.FileUtils;
import com.intuit.karate.resource.ResourceUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Set;

/**
 *
 * @author pthomas3
 */
public interface ResourceResolver {

    String root();

    InputStream read(String path);

    Set<String> jsfiles();

    public class ClassPathResourceResolver implements ResourceResolver {

        private final String root;
        private final Set<String> jsFiles;

        public ClassPathResourceResolver(String value) {
            if (value == null) {
                value = "";
            }
            if (!value.isEmpty() && !value.endsWith("/")) {
                value = value + "/";
            }
            root = value;
            jsFiles = ResourceUtils.findJsFilesInClassPath(root);
        }

        @Override
        public String root() {
            return root;
        }

        @Override
        public InputStream read(String path) {
            return ResourceUtils.classPathToStream(root + path);
        }

        @Override
        public Set<String> jsfiles() {
            return jsFiles;
        }

    }

    public class FileSystemResourceResolver implements ResourceResolver {

        private final File baseDir;
        private final String root;
        private final Set<String> jsFiles;

        public FileSystemResourceResolver(String value) {
            if (value == null) {
                value = "";
            }
            if (value.endsWith("/")) {
                root = value;
            } else {
                root = value + "/";
            }
            baseDir = new File(value);
            jsFiles = ResourceUtils.findJsFilesInDirectory(baseDir);
        }

        @Override
        public String root() {
            return root;
        }

        @Override
        public InputStream read(String path) {
            try {
                return new FileInputStream(baseDir.getPath() + File.separator + path);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Set<String> jsfiles() {
            return jsFiles;
        }

    }

}
