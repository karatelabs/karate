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
package io.karatelabs.common;


import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FileUtils {

    private FileUtils() {
        // only static methods
    }

    public static final File WORKING_DIR = new File("").getAbsoluteFile();

    /**
     * System property to explicitly set the build/output directory.
     */
    public static final String KARATE_OUTPUT_DIR = "karate.output.dir";

    /**
     * Detect the build directory based on the build tool being used.
     * Returns "build" for Gradle projects, "target" for Maven projects.
     * <p>
     * Detection order (first match wins):
     * <ol>
     *   <li>System property {@code karate.output.dir} - explicit override</li>
     *   <li>Build files: {@code build.gradle}, {@code build.gradle.kts} → "build"</li>
     *   <li>Build files: {@code pom.xml} (without Gradle files) → "target"</li>
     *   <li>Existing directories: {@code build/} (without target/) → "build"</li>
     *   <li>JVM command line contains "org.gradle." → "build"</li>
     *   <li>Gradle test worker system property → "build"</li>
     *   <li>Default: "target" (Maven convention)</li>
     * </ol>
     *
     * @return "build" for Gradle, "target" for Maven
     */
    public static String getBuildDir() {
        // 1. Explicit override via system property
        String explicit = System.getProperty(KARATE_OUTPUT_DIR);
        if (explicit != null && !explicit.isEmpty()) {
            return explicit;
        }

        // 2. Check for Gradle build files (most reliable)
        File gradleFile = new File(WORKING_DIR, "build.gradle");
        File gradleKtsFile = new File(WORKING_DIR, "build.gradle.kts");
        File settingsGradle = new File(WORKING_DIR, "settings.gradle");
        File settingsGradleKts = new File(WORKING_DIR, "settings.gradle.kts");
        boolean hasGradleFiles = gradleFile.exists() || gradleKtsFile.exists()
                || settingsGradle.exists() || settingsGradleKts.exists();

        if (hasGradleFiles) {
            return "build";
        }

        // 3. Check for Maven pom.xml (only if no Gradle files)
        File pomFile = new File(WORKING_DIR, "pom.xml");
        if (pomFile.exists()) {
            return "target";
        }

        // 4. Check for existing build directories (handles multi-module projects)
        File buildDir = new File(WORKING_DIR, "build");
        File targetDir = new File(WORKING_DIR, "target");
        if (buildDir.isDirectory() && !targetDir.isDirectory()) {
            return "build";
        }
        if (targetDir.isDirectory() && !buildDir.isDirectory()) {
            return "target";
        }

        // 5. Check JVM command line for Gradle (v1 approach)
        String command = System.getProperty("sun.java.command", "");
        if (command.contains("org.gradle.")) {
            return "build";
        }

        // 6. Check for Gradle test worker (when running via Gradle test task)
        if (System.getProperty("org.gradle.test.worker") != null) {
            return "build";
        }

        // 7. Default to Maven convention
        return "target";
    }

    public static String toString(File file) {
        try {
            return Files.readString(file.toPath(), UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String toString(URL url) {
        return toString(toBytes(url));
    }

    public static byte[] toBytes(URL url) {
        try (InputStream is = url.openStream()) {
            ByteArrayOutputStream os = FileUtils.toByteStream(is);
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String toString(InputStream is) {
        try {
            return toByteStream(is).toString(UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] toBytes(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] toBytes(InputStream is) {
        return toByteStream(is).toByteArray();
    }

    public static ByteArrayOutputStream toByteStream(InputStream is) {
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
        return new String(bytes, UTF_8);
    }

    public static byte[] toBytes(String string) {
        if (string == null) {
            return null;
        }
        return string.getBytes(UTF_8);
    }

    public static void copy(File src, File dest) {
        try {
            Files.copy(src.toPath(), dest.toPath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeToFile(File file, byte[] data) {
        try {
            Path path = file.toPath();
            // Create parent directories if they don't exist
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.write(path, data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeToFile(File file, String data) {
        writeToFile(file, data.getBytes(UTF_8));
    }

    public static InputStream toInputStream(String text) {
        return new ByteArrayInputStream(text.getBytes(UTF_8));
    }

}
