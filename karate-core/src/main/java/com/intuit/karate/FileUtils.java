/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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

import com.intuit.karate.core.Feature;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FileUtils {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);    

    private FileUtils() {
        // only static methods
    }
    
    public static final String KARATE_VERSION;

    static {
        Properties props = new Properties();
        InputStream stream = FileUtils.class.getResourceAsStream("/karate-meta.properties");
        String value;
        try {
            props.load(stream);
            stream.close();
            value = (String) props.get("karate.version");
        } catch (IOException e) {
            value = "(unknown)";
        }
        KARATE_VERSION = value;
    }    
    
    public static final File WORKING_DIR = new File("").getAbsoluteFile();

    public static StringUtils.Pair parsePathAndTags(String text) {
        int pos = text.indexOf('@');
        if (pos == -1) {
            text = StringUtils.trimToEmpty(text);
            return new StringUtils.Pair(text, null);
        } else {
            String left = StringUtils.trimToEmpty(text.substring(0, pos));
            String right = StringUtils.trimToEmpty(text.substring(pos));
            return new StringUtils.Pair(left, right);
        }
    }

    public static Feature parseFeatureAndCallTag(String path) {
        StringUtils.Pair pair = parsePathAndTags(path);
        Feature feature = Feature.read(pair.left);
        feature.setCallTag(pair.right);
        return feature;
    }

    public static String toString(File file) {
        try {
            return toString(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toString(InputStream is) {
        try {
            return toByteStream(is).toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] toBytes(InputStream is) {
        return toByteStream(is).toByteArray();
    }

    private static ByteArrayOutputStream toByteStream(InputStream is) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        try {
            while ((length = is.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static byte[] toBytes(String string) {
        if (string == null) {
            return null;
        }
        return string.getBytes(StandardCharsets.UTF_8);
    }

    public static void copy(File src, File dest) {
        try {
            writeToFile(dest, toBytes(new FileInputStream(src)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeToFile(File file, byte[] data) {
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            // try with resources, so will be closed automatically
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeToFile(File file, String data) {
        writeToFile(file, data.getBytes(StandardCharsets.UTF_8));
    }

    public static InputStream toInputStream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    public static void deleteDirectory(File file) {
        Path pathToBeDeleted = file.toPath();
        try {
            Files.walk(pathToBeDeleted)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    public static void renameFileIfZeroBytes(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            LOGGER.warn("file not found, previous write operation may have failed: {}", fileName);
        } else if (file.length() == 0) {
            LOGGER.warn("file size is zero bytes, previous write operation may have failed: {}", fileName);
            try {
                File dest = new File(fileName + ".fail");
                file.renameTo(dest);
                LOGGER.warn("renamed zero length file to: {}", dest.getName());
            } catch (Exception e) {
                LOGGER.warn("failed to rename zero length file: {}", e.getMessage());
            }
        }
    }

    // TODO use this <Set> based and tighter routine for feature files above
    private static void walkPath(Path root, Set<String> results, Predicate<Path> predicate) {
        Stream<Path> stream;
        try {
            stream = Files.walk(root);
            for (Iterator<Path> paths = stream.iterator(); paths.hasNext();) {
                Path path = paths.next();
                Path fileName = path.getFileName();
                if (predicate.test(fileName)) {
                    String relativePath = root.relativize(path.toAbsolutePath()).toString();
                    results.add(relativePath);
                }
            }
        } catch (IOException e) { // NoSuchFileException  
            LOGGER.trace("unable to walk path: {} - {}", root, e.getMessage());
        }
    }

    private static final Predicate<Path> IS_JS_FILE = p -> p != null && p.toString().endsWith(".js");
    
    private static final ClassLoader CLASS_LOADER = FileUtils.class.getClassLoader();

    public static Set<String> jsFiles(File baseDir) {
        Set<String> results = new HashSet();
        walkPath(baseDir.toPath().toAbsolutePath(), results, IS_JS_FILE);
        return results;
    }

    public static Set<String> jsFiles(String basePath) {
        Set<String> results = new HashSet();
        try {
            Enumeration<URL> urls = CLASS_LOADER.getResources(basePath);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                Path path = Paths.get(url.toURI());
                walkPath(path, results, IS_JS_FILE);
            }
        } catch (Exception e) {
            LOGGER.warn("unable to scan for js files at: {}", basePath);
        }
        return results;
    }

    public static InputStream resourceAsStream(String resourcePath) {
        InputStream is = CLASS_LOADER.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new RuntimeException("failed to read: " + resourcePath);
        }
        return is;
    }

    public static String getBuildDir() {
        String temp = System.getProperty(Constants.KARATE_OUTPUT_DIR);
        if (temp != null) {
            return temp;
        }
        String command = System.getProperty("sun.java.command", "");
        return command.contains("org.gradle.") ? "build" : "target";
    }

    public static enum OsType {
        WINDOWS,
        MACOSX,
        LINUX,
        UNKNOWN
    }

    public static boolean isOsWindows() {
        return getOsType() == OsType.WINDOWS;
    }

    public static boolean isOsMacOsX() {
        return getOsType() == OsType.MACOSX;
    }

    public static String getOsName() {
        return System.getProperty("os.name");
    }

    public static OsType getOsType() {
        return getOsType(getOsName());
    }

    public static OsType getOsType(String name) {
        if (name == null) {
            name = "unknown";
        } else {
            name = name.toLowerCase();
        }
        if (name.contains("win")) {
            return OsType.WINDOWS;
        } else if (name.contains("mac")) {
            return OsType.MACOSX;
        } else if (name.contains("nix") || name.contains("nux")) {
            return OsType.LINUX;
        } else {
            return OsType.UNKNOWN;
        }
    }

}
