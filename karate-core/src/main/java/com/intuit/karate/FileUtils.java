/*
 * The MIT License
 *
 * Copyright 2022 Karate Labs Inc.
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
import com.intuit.karate.core.FeatureCall;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
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

    public static final boolean KARATE_TELEMETRY;
    public static final String KARATE_VERSION;
    public static final String KARATE_META;
    public static final String USER_UUID;
    public static final String USER_HASH;

    static {
        Properties props = new Properties();
        InputStream stream = FileUtils.class.getResourceAsStream("/karate-meta.properties");
        String version;
        try {
            props.load(stream);
            stream.close();
            version = (String) props.get("karate.version");
        } catch (IOException e) {
            version = "(unknown)";
        }
        KARATE_VERSION = version;
        KARATE_META = System.getenv("KARATE_META");
        String telemetryEnv = System.getenv("KARATE_TELEMETRY"); // "true" / "false"
        KARATE_TELEMETRY = telemetryEnv == null ? true : telemetryEnv.trim().equals("true");
        String userHome = System.getProperty("user.home", "");
        String uuid;
        String hash;
        try {
            File uuidFile = new File(userHome + File.separator + ".karate" + File.separator + "uuid.txt");
            if (uuidFile.exists()) {
                uuid = toString(uuidFile);
            } else {
                uuid = UUID.randomUUID().toString();
                writeToFile(uuidFile, uuid);
            }
            hash = checksum(userHome) + "";
        } catch (Exception e) {
            hash = "unknown";
            uuid = "unknown";
        }
        USER_HASH = hash;
        USER_UUID = uuid;
    }

    public static final File WORKING_DIR = new File("").getAbsoluteFile();

    public static long checksum(String src) {
        byte[] bytes = src.getBytes(UTF_8);
        Checksum crc32 = new CRC32();
        crc32.update(bytes, 0, bytes.length);
        return crc32.getValue();
    }

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

    public static FeatureCall parseFeatureAndCallTag(String path) {
        StringUtils.Pair pair = parsePathAndTags(path);
        Feature feature = Feature.read(pair.left);
        return new FeatureCall(feature, pair.right, -1, null);
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
            return toByteStream(is).toString(UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] toBytes(File file) {
        try {
            return toBytes(new FileInputStream(file));
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
            writeToFile(dest, toBytes(new FileInputStream(src)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeToFile(File file, byte[] data) {
        try {
            File parent = file.getAbsoluteFile().getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
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
        writeToFile(file, data.getBytes(UTF_8));
    }

    public static InputStream toInputStream(String text) {
        return new ByteArrayInputStream(text.getBytes(UTF_8));
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
