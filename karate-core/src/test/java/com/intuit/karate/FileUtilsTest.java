package com.intuit.karate;

import java.io.File;
import java.util.List;

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
    public void testWindowsFileNames() {
    	String path = "com/intuit/karate/cucumber/scenario.feature";
    	String fixed = FileUtils.toPackageQualifiedName(path);
    	assertEquals("com.intuit.karate.cucumber.scenario", fixed);
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
        String relativePath = "classpath:com/intuit/karate/ui/test.feature";
        List<FileResource> files = FileUtils.scanForFeatureFilesOnClassPath();
        boolean found = false;
        for (FileResource file : files) {
            if (file.relativePath.equals(relativePath)) {
                File tempFile = FileUtils.fromRelativeClassPath(relativePath);                
                assertEquals(tempFile, file.file);
                String temp = FileUtils.toRelativeClassPath(file.file);
                assertEquals(temp, file.relativePath);
                found = true;
                break;
            }
        }
        assertTrue(found);
    }
    
    @Test
    public void testScanFilePath() {
        String relativePath = "classpath:com/intuit/karate/ui";
        List<FileResource> files = FileUtils.scanForFeatureFiles(true, relativePath);
        assertEquals(2, files.size());
    }    
    
    @Test
    public void testRelativePathForClass() {
        assertEquals("classpath:com/intuit/karate", FileUtils.toRelativeClassPath(getClass()));
    }
    
    @Test
    public void testGetAllClasspaths() {
        FileUtils.getAllClassPaths();
    }
    
}
