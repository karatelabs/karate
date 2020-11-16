package com.intuit.karate;

import com.intuit.karate.core.Feature;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class FileUtilsTest {
    
    static final Logger logger = LoggerFactory.getLogger(FileUtilsTest.class);
    
    @Test
    void testIsJsonFile() {
        assertFalse(FileUtils.isJsonFile("foo.txt"));
        assertTrue(FileUtils.isJsonFile("foo.json"));
    }
    
    @Test
    void testIsJavaScriptFile() {
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
    void testIsXmlFile() {
        assertFalse(FileUtils.isXmlFile("foo.txt"));
        assertTrue(FileUtils.isXmlFile("foo.xml"));
    }
    
    @Test
    void testIsTextFile() {
        assertFalse(FileUtils.isTextFile("foo.xml"));
        assertTrue(FileUtils.isTextFile("foo.txt"));
    }
    
    @Test
    void testIsCsvFile() {
        assertFalse(FileUtils.isCsvFile("foo.txt"));
        assertTrue(FileUtils.isCsvFile("foo.csv"));
    }
    
    @Test
    void testIsGraphQlFile() {
        assertFalse(FileUtils.isGraphQlFile("foo.txt"));
        assertTrue(FileUtils.isGraphQlFile("foo.graphql"));
        assertTrue(FileUtils.isGraphQlFile("foo.gql"));
    }
    
    @Test
    void testIsFeatureFile() {
        assertFalse(FileUtils.isFeatureFile("foo.txt"));
        assertTrue(FileUtils.isFeatureFile("foo.feature"));
    }
    
    @Test
    void testRemovePrefix() {
        assertEquals("baz", FileUtils.removePrefix("foobar:baz"));
        assertEquals("foobarbaz", FileUtils.removePrefix("foobarbaz"));
        assertNull(FileUtils.removePrefix(null));
    }
    
    @Test
    void testToStringBytes() {
        final byte[] bytes = {102, 111, 111, 98, 97, 114};
        assertEquals("foobar", FileUtils.toString(bytes));
        assertNull(FileUtils.toString((byte[]) null));
    }
    
    @Test
    void testToBytesString() {
        final byte[] bytes = {102, 111, 111, 98, 97, 114};
        assertArrayEquals(bytes, FileUtils.toBytes("foobar"));
        assertNull(FileUtils.toBytes((String) null));
    }
    
    @Test
    void testReplaceFileExtension() {
        assertEquals("foo.bar", FileUtils.replaceFileExtension("foo.txt", "bar"));
        assertEquals("foo.baz", FileUtils.replaceFileExtension("foo", "baz"));
    }
    
    @Test
    void testWindowsFileNames() {
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
    void testRenameZeroLengthFile() {
        long time = System.currentTimeMillis();
        String name = "target/" + time + ".json";
        FileUtils.writeToFile(new File(name), "");
        FileUtils.renameFileIfZeroBytes(name);
        File file = new File(name + ".fail");
        assertTrue(file.exists());
    }
    
    @Test
    void testScanFile() {
        String relativePath = "classpath:com/intuit/karate/test/file-utils-test.feature";
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
    void testScanFileWithLineNumber() {
        String relativePath = "classpath:com/intuit/karate/test/file-utils-test.feature:3";
        List<Resource> files = FileUtils.scanForFeatureFiles(Collections.singletonList(relativePath), getClass().getClassLoader());
        assertEquals(1, files.size());
        assertEquals(3, files.get(0).getLine());
    }
    
    @Test
    void testScanFilePath() {
        String relativePath = "classpath:com/intuit/karate/test";
        List<Resource> files = FileUtils.scanForFeatureFiles(true, relativePath, getClass().getClassLoader());
        assertEquals(1, files.size());
    }
    
    @Test
    void testRelativePathForClass() {
        assertEquals("classpath:com/intuit/karate", FileUtils.toRelativeClassPath(getClass()));
    }
    
    @Test
    void testGetAllClasspaths() {
        List<URL> urls = FileUtils.getAllClassPathUrls(getClass().getClassLoader());
        for (URL url : urls) {
            logger.debug("url: {}", url);
        }
    }
    
    @Test
    void testGetClasspathAbsolute() {
        File file = new File("src/test/java/com/intuit/karate/core/runner/multi-scenario.feature").getAbsoluteFile();
        String scan = "classpath:" + file.getPath();
        List<Resource> resources = FileUtils.scanForFeatureFiles(Collections.singletonList(scan), ClassLoader.getSystemClassLoader());
        assertEquals(1, resources.size());
        assertEquals(file, resources.get(0).getPath().toFile());
    }
    
    static ClassLoader getJarClassLoader() throws Exception {
        File jar = new File("src/test/resources/karate-test.jar");
        assertTrue(jar.exists());
        return new URLClassLoader(new URL[]{jar.toURI().toURL()});
    }
    
    @Test
    void testUsingKarateBase() throws Exception {
        String relativePath = "classpath:demo/jar1/caller.feature";
        ClassLoader cl = getJarClassLoader();
        Path path = FileUtils.fromRelativeClassPath(relativePath, cl);
        Resource resource = new Resource(path, relativePath, -1, cl);
        Feature feature = Feature.read(resource);
        try {
            Runner.runFeature(feature, null, true);
            fail("we should not have reached here");
        } catch (Exception e) {
            assertTrue(e instanceof KarateException);
        }
    }
    
    @Test
    void testUsingBadPath() {
        String relativePath = "/foo/bar/feeder.feature";
        try {
            Feature.read(relativePath);
            fail("we should not have reached here");
        } catch (Exception e) {
            assertEquals(e.getCause().getClass(), java.io.FileNotFoundException.class);
        }
    }
    
    @Test
    void testDeleteDirectory() throws Exception {
        new File("target/foo/bar").mkdirs();
        FileUtils.writeToFile(new File("target/foo/hello.txt"), "hello world");
        FileUtils.writeToFile(new File("target/foo/bar/world.txt"), "hello again");
        assertTrue(new File("target/foo/hello.txt").exists());
        assertTrue(new File("target/foo/bar/world.txt").exists());
        FileUtils.deleteDirectory(new File("target/foo"));
        assertFalse(new File("target/foo/hello.txt").exists());
        assertFalse(new File("target/foo/bar/world.txt").exists());        
    }
    
}
