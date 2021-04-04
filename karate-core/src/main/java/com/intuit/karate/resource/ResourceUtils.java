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
import com.intuit.karate.core.Feature;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ResourceList;
import io.github.classgraph.ScanResult;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

    public static List<Feature> findFeatureFiles(File workingDir, List<String> paths) {
        List<Feature> features = new ArrayList();
        if (paths == null || paths.isEmpty()) {
            return features;
        }
        if (paths.size() == 1) {
            String path = paths.get(0);
            int pos = path.indexOf(".feature:");
            int line;
            if (pos != -1) { // line number has been appended
                line = Integer.valueOf(path.substring(pos + 9));
                path = path.substring(0, pos + 8);
            } else {
                line = -1;
            }
            if (path.endsWith(".feature")) {
                Resource resource = getResource(workingDir, path);
                Feature feature = Feature.read(resource);
                feature.setCallLine(line);
                features.add(feature);
                return features;
            }
        }
        Collection<Resource> resources = findResourcesByExtension(workingDir, "feature", paths);
        for (Resource resource : resources) {
            features.add(Feature.read(resource));
        }
        return features;
    }

    private static final ScanResult SCAN_RESULT = new ClassGraph().acceptPaths("/").scan(1);

    public static Resource getResource(File workingDir, String path) {
        if (path.startsWith(Resource.CLASSPATH_COLON)) {
            path = removePrefix(path);
            File file = classPathToFile(path);
            if (file != null) {
                return new FileResource(file, true, path);
            }
            List<Resource> resources = new ArrayList<>();
            synchronized (SCAN_RESULT) {
                ResourceList rl = SCAN_RESULT.getResourcesWithPath(path);
                if (rl == null) {
                    rl = ResourceList.emptyList();
                }
                rl.forEachByteArrayIgnoringIOException((res, bytes) -> {
                    URI uri = res.getURI();
                    if ("file".equals(uri.getScheme())) {
                        File found = Paths.get(uri).toFile();
                        resources.add(new FileResource(found, true, res.getPath()));
                    } else {
                        resources.add(new JarResource(bytes, res.getPath(), uri));
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
            Path relativePath = workingDir.toPath().relativize(file.getAbsoluteFile().toPath());
            return new FileResource(file, false, relativePath.toString());
        }
    }

    public static Collection<Resource> findResourcesByExtension(File workingDir, String extension, String path) {
        return findResourcesByExtension(workingDir, extension, Collections.singletonList(path));
    }

    public static List<Resource> findResourcesByExtension(File workingDir, String extension, List<String> paths) {
        List<Resource> results = new ArrayList();
        List<File> fileRoots = new ArrayList();
        List<String> pathRoots = new ArrayList();
        for (String path : paths) {
            if (path.endsWith("." + extension)) {
                results.add(getResource(workingDir, path));
            } else if (path.startsWith(Resource.CLASSPATH_COLON)) {
                pathRoots.add(removePrefix(path));
            } else {
                fileRoots.add(new File(removePrefix(path)));
            }
        }
        if (!fileRoots.isEmpty()) {
            results.addAll(findFilesByExtension(workingDir, extension, fileRoots));
        } else if (results.isEmpty() && !pathRoots.isEmpty()) {
            String[] searchPaths = pathRoots.toArray(new String[pathRoots.size()]);
            try (ScanResult scanResult = new ClassGraph().acceptPaths(searchPaths).scan(1)) {
                ResourceList rl = scanResult.getResourcesWithExtension(extension);
                rl.forEachByteArrayIgnoringIOException((res, bytes) -> {
                    URI uri = res.getURI();
                    if ("file".equals(uri.getScheme())) {
                        File file = Paths.get(uri).toFile();
                        results.add(new FileResource(file, true, res.getPath()));
                    } else {
                        results.add(new JarResource(bytes, res.getPath(), uri));
                    }
                });
            }
        }
        return results;
    }

    private static List<Resource> findFilesByExtension(File workingDir, String extension, List<File> files) {
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
        return results.stream()
                .map(f -> {
                    Path relativePath = workingDir.toPath().relativize(f.getAbsoluteFile().toPath());
                    return new FileResource(f, false, relativePath.toString());
                })
                .collect(Collectors.toList());
    }

    public static File getFileRelativeTo(Class clazz, String path) {
        Path dirPath = getPathContaining(clazz);
        File file = new File(dirPath + File.separator + path);
        if (file.exists()) {
            return file;
        }
        try {
            URL relativePath = clazz.getClassLoader().getResource(toPathFromClassPathRoot(clazz) + File.separator + path);
            return Paths.get(relativePath.toURI()).toFile();
        } catch (Exception e) {
            throw new RuntimeException("cannot find " + path + " relative to " + clazz + ", " + e.getMessage());
        }
    }

    public static Path getPathContaining(Class clazz) {
        String relative = toPathFromClassPathRoot(clazz);
        URL url = clazz.getClassLoader().getResource(relative);
        try {
            return Paths.get(url.toURI());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static File getDirContaining(Class clazz) {
        Path path = getPathContaining(clazz);
        return path.toFile();
    }

    public static String toPathFromClassPathRoot(Class clazz) {
        Package p = clazz.getPackage();
        String relative = "";
        if (p != null) {
            relative = p.getName().replace('.', '/');
        }
        return relative;
    }

    protected static String removePrefix(String text) {
        if (text.startsWith(Resource.CLASSPATH_COLON) || text.startsWith(Resource.FILE_COLON)) {
            return text.substring(text.indexOf(':') + 1);
        } else {
            return text;
        }
    }

    public static String getParentPath(String relativePath) {
        int pos = relativePath.lastIndexOf('/');
        return pos == -1 ? relativePath : relativePath.substring(0, pos + 1);        
    }
    
    private static final ClassLoader CLASS_LOADER = ResourceUtils.class.getClassLoader();

    public static InputStream classPathResourceToStream(String path) {
        return CLASS_LOADER.getResourceAsStream(path);
    }
    
    public static String classPathResourceToString(String path) {
        return FileUtils.toString(classPathResourceToStream(path));
    }

    public static File classPathToFile(String path) {
        URL url = CLASS_LOADER.getResource(path);
        if (url == null || !"file".equals(url.getProtocol())) {
            return null;
        }
        try {
            return Paths.get(url.toURI()).toFile();
        } catch (URISyntaxException e) {
            return null;
        }
    }
    
    public static File classPathOrFile(String path) {
        File temp = classPathToFile(path);
        if (temp != null) {
            return temp;
        }
        temp = new File(path);
        return temp.exists() ? temp : null;
    }

    public static Set<String> findJsFilesInDirectory(File dir) {
        List<Resource> resources = findFilesByExtension(dir.getAbsoluteFile(), "js", Collections.singletonList(dir));
        Set<String> set = new HashSet(resources.size());
        for (Resource res : resources) {
            set.add(res.getRelativePath());
        }
        return set;
    }

    public static Set<String> findJsFilesInClassPath(String path) {
        String searchPath;
        if (path.startsWith(Resource.CLASSPATH_COLON)) {
            searchPath = path;
            path = removePrefix(path);
        } else {
            searchPath = Resource.CLASSPATH_COLON + path;
        }
        Resource root = getResource(FileUtils.WORKING_DIR, searchPath);
        File rootFile = root.isFile() ? root.getFile() : FileUtils.WORKING_DIR;
        Collection<Resource> resources = findResourcesByExtension(rootFile, "js", searchPath);
        Set<String> set = new HashSet(resources.size());
        int pos = path.length();
        for (Resource res : resources) {
            set.add(res.getRelativePath().substring(pos));
        }
        return set;
    }

}
