package com.intuit.karate;

import com.intuit.karate.core.Feature;
import java.io.File;

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
    void testRenameZeroLengthFile() {
        long time = System.currentTimeMillis();
        String name = "target/" + time + ".json";
        FileUtils.writeToFile(new File(name), "");
        FileUtils.renameFileIfZeroBytes(name);
        File file = new File(name + ".fail");
        assertTrue(file.exists());
    }
    
    @Test
    void testUsingBadPath() {
        String relativePath = "/foo/bar/feeder.feature";
        try {
            Feature.read(relativePath);
            fail("we should not have reached here");
        } catch (Exception e) {
            assertEquals(e.getMessage(), "not found: /foo/bar/feeder.feature");
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
