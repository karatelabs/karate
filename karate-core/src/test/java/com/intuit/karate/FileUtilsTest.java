/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.exception.KarateException;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FileUtilsTest {

    private static final Logger logger = LoggerFactory.getLogger(FileUtilsTest.class);

    @Test
    public void testIsClassPath() {
        assertFalse(FileUtils.isClassPath("foo/bar/baz"));
        assertTrue(FileUtils.isClassPath("classpath:foo/bar/baz"));
    }

    @Test
    public void testIsFilePath() {
        assertFalse(FileUtils.isFilePath("foo/bar/baz"));
        assertTrue(FileUtils.isFilePath("file:/foo/bar/baz"));
    }

    @Test
    public void testIsThisPath() {
        assertFalse(FileUtils.isThisPath("foo/bar/baz"));
        assertTrue(FileUtils.isThisPath("this:/foo/bar/baz"));
    }

    @Test
    public void testIsJsonFile() {
        assertFalse(FileUtils.isJsonFile("foo.txt"));
        assertTrue(FileUtils.isJsonFile("foo.json"));
    }

    @Test
    public void testIsJavaScriptFile() {
        assertFalse(FileUtils.isJavaScriptFile("foo.txt"));
        assertTrue(FileUtils.isJavaScriptFile("foo.js"));
    }

    @Test
    public void testIsYamlFile() {
        assertFalse(FileUtils.isYamlFile("foo.txt"));
        assertTrue(FileUtils.isYamlFile("foo.yaml"));
        assertTrue(FileUtils.isYamlFile("foo.yml"));
    }

    @Test
    public void testIsXmlFile() {
        assertFalse(FileUtils.isXmlFile("foo.txt"));
        assertTrue(FileUtils.isXmlFile("foo.xml"));
    }

    @Test
    public void testIsTextFile() {
        assertFalse(FileUtils.isTextFile("foo.xml"));
        assertTrue(FileUtils.isTextFile("foo.txt"));
    }

    @Test
    public void testIsCsvFile() {
        assertFalse(FileUtils.isCsvFile("foo.txt"));
        assertTrue(FileUtils.isCsvFile("foo.csv"));
    }

    @Test
    public void testIsGraphQlFile() {
        assertFalse(FileUtils.isGraphQlFile("foo.txt"));
        assertTrue(FileUtils.isGraphQlFile("foo.graphql"));
        assertTrue(FileUtils.isGraphQlFile("foo.gql"));
    }

    @Test
    public void testIsFeatureFile() {
        assertFalse(FileUtils.isFeatureFile("foo.txt"));
        assertTrue(FileUtils.isFeatureFile("foo.feature"));
    }

    @Test
    public void testRemovePrefix() {
        assertEquals("baz", FileUtils.removePrefix("foobar:baz"));
        assertEquals("foobarbaz", FileUtils.removePrefix("foobarbaz"));
        assertNull(FileUtils.removePrefix(null));
    }

    @Test
    public void testToStringBytes() {
        final byte[] bytes = {102, 111, 111, 98, 97, 114};
        assertEquals("foobar", FileUtils.toString(bytes));
        assertNull(FileUtils.toString((byte[]) null));
    }

    @Test
    public void testToBytesString() {
        final byte[] bytes = {102, 111, 111, 98, 97, 114};
        assertArrayEquals(bytes, FileUtils.toBytes("foobar"));
        assertNull(FileUtils.toBytes((String) null));
    }

    @Test
    public void testReplaceFileExtension() {
        assertEquals("foo.bar", FileUtils.replaceFileExtension("foo.txt", "bar"));
        assertEquals("foo.baz", FileUtils.replaceFileExtension("foo", "baz"));
    }

    @Test
    public void testWindowsFileNames() {
        String path = "com/intuit/karate/cucumber/scenario.feature";
        String fixed = FileUtils.toPackageQualifiedName(path);
        assertEquals("com.intuit.karate.cucumber.scenario", fixed);
        path = "file:C:\\Users\\Karate\\scenario.feature";
        fixed = FileUtils.toPackageQualifiedName(path);
        assertEquals("Users.Karate.scenario", fixed);
        path = "file:../Karate/scenario.feature";
        fixed = FileUtils.toPackageQualifiedName(path);
        assertEquals("Karate.scenario", fixed);
    }

    @Test
    public void testRenameZeroLengthFile() {
        long time = System.currentTimeMillis();
        String name = "target/" + time + ".json";
        FileUtils.writeToFile(new File(name), "");
        FileUtils.renameFileIfZeroBytes(name);
        File file = new File(name + ".fail");
        assertTrue(file.exists());
    }

    @Test
    public void testScanFile() {
        String relativePath = "classpath:com/intuit/karate/test/test.feature";
        ClassLoader cl = getClass().getClassLoader();
        List<Resource> files = FileUtils.scanForFeatureFilesOnClassPath(cl);
        boolean found = false;
        for (Resource file : files) {
            String actualPath = file.getRelativePath().replace('\\', '/');
            if (actualPath.equals(relativePath)) {
                String temp = FileUtils.toRelativeClassPath(file.getPath(), cl);
                assertEquals(temp, actualPath);
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testScanFileWithLineNumber() {
        String relativePath = "classpath:com/intuit/karate/test/test.feature:3";
        List<Resource> files = FileUtils.scanForFeatureFiles(Collections.singletonList(relativePath), getClass().getClassLoader());
        assertEquals(1, files.size());
        assertEquals(3, files.get(0).getLine());
    }

    @Test
    public void testScanFilePath() {
        String relativePath = "classpath:com/intuit/karate/test";
        List<Resource> files = FileUtils.scanForFeatureFiles(true, relativePath, getClass().getClassLoader());
        assertEquals(1, files.size());
    }

    @Test
    public void testRelativePathForClass() {
        assertEquals("classpath:com/intuit/karate", FileUtils.toRelativeClassPath(getClass()));
    }

    @Test
    public void testGetAllClasspaths() {
        List<URL> urls = FileUtils.getAllClassPathUrls(getClass().getClassLoader());
        for (URL url : urls) {
            logger.debug("url: {}", url);
        }
    }
    
    @Test
    public void testGetClasspathAbsolute() {
        File file = new File("src/test/java/com/intuit/karate/multi-scenario.feature").getAbsoluteFile();
        String scan = "classpath:" + file.getPath();
        List<Resource> resources = FileUtils.scanForFeatureFiles(Collections.singletonList(scan), ClassLoader.getSystemClassLoader());
        assertEquals(1, resources.size());
        assertEquals(file, resources.get(0).getPath().toFile());
    }    

    private static ClassLoader getJarClassLoader() throws Exception {
        File jar = new File("src/test/resources/karate-test.jar");
        assertTrue(jar.exists());
        return new URLClassLoader(new URL[]{jar.toURI().toURL()});
    }

    @Test
    public void testUsingKarateBase() throws Exception {
        String relativePath = "classpath:demo/jar1/caller.feature";
        ClassLoader cl = getJarClassLoader();
        Path path = FileUtils.fromRelativeClassPath(relativePath, cl);
        Resource resource = new Resource(path, relativePath, -1);
        Feature feature = FeatureParser.parse(resource);
        try {
            Map<String, Object> map = Runner.runFeature(feature, null, true);
            fail("we should not have reached here");
        } catch (Exception e) {
            assertTrue(e instanceof KarateException);
        }
    }
    
    @Test
    public void testUsingBadPath() {
        String relativePath = "/foo/bar/feeder.feature";
        try {
            FeatureParser.parse(relativePath);
            fail("we should not have reached here");
        } catch (Exception e) {
            assertEquals("file does not exist: /foo/bar/feeder.feature", e.getMessage());
        }
    }

}
