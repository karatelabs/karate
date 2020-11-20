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

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ResourceList;
import io.github.classgraph.ScanResult;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ResourceUtils {

    private static final Logger logger = LoggerFactory.getLogger(ResourceUtils.class);

    private ResourceUtils() {
        // only static methods
    }

    public static Resource getResource(String path) {
        if (path.startsWith("classpath:")) {
            List<Resource> resources = new ArrayList();
            try (ScanResult scanResult = new ClassGraph().acceptPaths("/").scan()) {
                ResourceList rl = scanResult.getResourcesWithPath(removePrefix(path));
                rl.forEachByteArrayIgnoringIOException((res, bytes) -> {
                    URI uri = res.getURI();
                    if ("file".equals(uri.getScheme())) {
                        File file = Paths.get(uri).toFile();
                        resources.add(new FileResource(file, true, res.getPath()));
                    } else {
                        resources.add(new JarResource(bytes, res.getPath()));
                    }
                });
            }
            if (resources.isEmpty()) {
                throw new RuntimeException("not found: " + path);
            }
            return resources.get(0);
        } else {
            File file = new File(removePrefix(path));
            if (!file.exists()) {
                throw new RuntimeException("not found: " + path);
            }
            return new FileResource(file);
        }
    }

    public static Collection<Resource> findResourcesByExtension(String extension, String... paths) {
        Set<Resource> set = new HashSet(); // de-dupe
        List<File> fileRoots = new ArrayList();
        List<String> pathRoots = new ArrayList();
        for (String path : paths) {
            if (path.startsWith("classpath:")) {
                pathRoots.add(removePrefix(path));
            } else {
                fileRoots.add(new File(removePrefix(path)));
            }
        }
        if (!fileRoots.isEmpty()) {
            set.addAll(findFilesByExtension(extension, fileRoots));
        } else {
            String[] searchPaths;
            if (pathRoots.isEmpty()) {
                searchPaths = new String[]{"/"}; // optimize, don't include class files in scan
            } else {
                searchPaths = pathRoots.toArray(new String[pathRoots.size()]);
            }
            try (ScanResult scanResult = new ClassGraph().acceptPaths(searchPaths).scan()) {
                ResourceList rl = scanResult.getResourcesWithExtension(extension);
                rl.forEachByteArrayIgnoringIOException((res, bytes) -> {
                    URI uri = res.getURI();
                    if ("file".equals(uri.getScheme())) {
                        File file = Paths.get(uri).toFile();
                        set.add(new FileResource(file, true, res.getPath()));
                    } else {
                        set.add(new JarResource(bytes, res.getPath()));
                    }
                });
            }
        }
        return set;
    }

    private static List<Resource> findFilesByExtension(String extension, List<File> files) {
        List<File> results = new ArrayList();
        for (File base : files) {
            Path searchPath = base.toPath();
            Stream<Path> stream;
            try {
                stream = Files.walk(searchPath);
                for (Iterator<Path> paths = stream.iterator(); paths.hasNext();) {
                    Path path = paths.next();
                    String fileName = path.getFileName().toString();
                    if (fileName.endsWith("." + extension)) {
                        results.add(path.toFile());
                    }
                }
            } catch (IOException e) { // NoSuchFileException  
                logger.trace("unable to walk path: {} - {}", searchPath, e.getMessage());
            }
        }
        return results.stream().map(f -> new FileResource(f)).collect(Collectors.toList());
    }

    private static String removePrefix(String text) {
        int pos = text.indexOf(':');
        return pos == -1 ? text : text.substring(pos + 1);
    }

}
