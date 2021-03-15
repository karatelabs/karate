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
