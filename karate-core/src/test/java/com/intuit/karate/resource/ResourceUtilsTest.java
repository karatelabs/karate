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
import com.intuit.karate.Match;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class ResourceUtilsTest {

    static final Logger logger = LoggerFactory.getLogger(ResourceUtilsTest.class);
    
    static File wd = FileUtils.WORKING_DIR;

    @Test
    void testFindFilesByExtension() {
        Collection<Resource> list = ResourceUtils.findResourcesByExtension(wd, "txt", "src/test/java/com/intuit/karate/resource");
        assertEquals(1, list.size());
        Resource resource = list.iterator().next();
        assertTrue(resource.isFile());
        assertFalse(resource.isClassPath());
        assertEquals("src/test/java/com/intuit/karate/resource/test1.txt", resource.getRelativePath());
        assertEquals("src/test/java/com/intuit/karate/resource/test1.txt", resource.getPrefixedPath());
        assertEquals("foo", FileUtils.toString(resource.getStream()));
    }

    @Test
    void testGetFileByPath() {
        Resource resource = ResourceUtils.getResource(wd, "src/test/java/com/intuit/karate/resource/test1.txt");
        assertTrue(resource.isFile());
        assertFalse(resource.isClassPath());
        assertEquals("src/test/java/com/intuit/karate/resource/test1.txt", resource.getRelativePath());
        assertEquals("src/test/java/com/intuit/karate/resource/test1.txt", resource.getPrefixedPath());
        assertEquals("foo", FileUtils.toString(resource.getStream()));
    }

    @Test
    void testResolveFile() {
        Resource temp = ResourceUtils.getResource(wd, "src/test/java/com/intuit/karate/resource/test1.txt");
        Resource resource = temp.resolve("test2.log");
        assertTrue(resource.isFile());
        assertFalse(resource.isClassPath());
        assertEquals("src/test/java/com/intuit/karate/resource/test2.log", resource.getRelativePath());
        assertEquals("src/test/java/com/intuit/karate/resource/test2.log", resource.getPrefixedPath());
        assertEquals("bar", FileUtils.toString(resource.getStream()));
    }

    @Test
    void testResolveRelativeFile() {
        Resource temp = ResourceUtils.getResource(wd, "src/test/java/com/intuit/karate/resource/dir1/dir1.log");
        Resource resource = temp.resolve("../dir2/dir2.log");
        assertTrue(resource.isFile());
        assertFalse(resource.isClassPath());
        assertEquals("src/test/java/com/intuit/karate/resource/dir1/../dir2/dir2.log", resource.getRelativePath());
        assertEquals("src/test/java/com/intuit/karate/resource/dir1/../dir2/dir2.log", resource.getPrefixedPath());
        assertEquals("src.test.java.com.intuit.karate.resource.dir1.dir2.dir2.log", resource.getPackageQualifiedName());
        assertEquals("bar", FileUtils.toString(resource.getStream()));
    }

    @Test
    void testFindJarFilesByExtension() {
        Collection<Resource> list = ResourceUtils.findResourcesByExtension(wd, "properties", "classpath:cucumber");
        Resource resource = null;
        for (Resource temp : list) {
            if ("cucumber/version.properties".equals(temp.getRelativePath())) {
                resource = temp;
                break;
            }
        }
        assertFalse(resource.isFile());
        assertTrue(resource.isClassPath());
        assertEquals("cucumber/version.properties", resource.getRelativePath());
        assertEquals("classpath:cucumber/version.properties", resource.getPrefixedPath());
        assertEquals("cucumber-jvm.version=1.2.5", FileUtils.toString(resource.getStream()));
    }

    @Test
    void testGetJarFileByPath() {
        Resource resource = ResourceUtils.getResource(wd, "classpath:cucumber/version.properties");
        assertFalse(resource.isFile());
        assertTrue(resource.isClassPath());
        assertEquals("cucumber/version.properties", resource.getRelativePath());
        assertEquals("classpath:cucumber/version.properties", resource.getPrefixedPath());
        assertEquals("cucumber-jvm.version=1.2.5", FileUtils.toString(resource.getStream()));
    }

    @Test
    void testResolveJarFile() {
        Resource temp = ResourceUtils.getResource(wd, "classpath:cucumber/version.properties");
        Resource resource = temp.resolve("api/cli/USAGE.txt");
        assertFalse(resource.isFile());
        assertTrue(resource.isClassPath());
        assertEquals("cucumber/api/cli/USAGE.txt", resource.getRelativePath());
        assertEquals("classpath:cucumber/api/cli/USAGE.txt", resource.getPrefixedPath());
    }

    @Test
    void testFindClassPathFilesByExtension() {
        Collection<Resource> list = ResourceUtils.findResourcesByExtension(wd, "txt", "classpath:com/intuit/karate/resource");
        assertEquals(1, list.size());
        Resource resource = list.iterator().next();
        assertTrue(resource.isFile());
        assertTrue(resource.isClassPath());
        assertEquals("com/intuit/karate/resource/test1.txt", resource.getRelativePath());
        assertEquals("classpath:com/intuit/karate/resource/test1.txt", resource.getPrefixedPath());
        assertEquals("foo", FileUtils.toString(resource.getStream()));
    }

    @Test
    void testGetClassPathFileByPath() {
        Resource resource = ResourceUtils.getResource(wd, "classpath:com/intuit/karate/resource/test1.txt");
        assertTrue(resource.isFile());
        assertTrue(resource.isClassPath());
        assertEquals("com/intuit/karate/resource/test1.txt", resource.getRelativePath());
        assertEquals("classpath:com/intuit/karate/resource/test1.txt", resource.getPrefixedPath());
        assertEquals("foo", FileUtils.toString(resource.getStream()));
    }

    @Test
    void testResolveClassPathFile() {
        Resource temp = ResourceUtils.getResource(wd, "classpath:com/intuit/karate/resource/test1.txt");
        Resource resource = temp.resolve("test2.log");
        assertTrue(resource.isFile());
        assertTrue(resource.isClassPath());
        assertEquals("com/intuit/karate/resource/test2.log", resource.getRelativePath());
        assertEquals("classpath:com/intuit/karate/resource/test2.log", resource.getPrefixedPath());
        assertEquals("bar", FileUtils.toString(resource.getStream()));
    }

    @Test
    void testResolveRelativeClassPathFile() {
        Resource temp = ResourceUtils.getResource(new File(""), "classpath:com/intuit/karate/resource/dir1/dir1.log");
        Resource resource = temp.resolve("../dir2/dir2.log");
        assertTrue(resource.isFile());
        assertTrue(resource.isClassPath());
        assertEquals("com/intuit/karate/resource/dir1/../dir2/dir2.log", resource.getRelativePath());
        assertEquals("classpath:com/intuit/karate/resource/dir1/../dir2/dir2.log", resource.getPrefixedPath());
        assertEquals("bar", FileUtils.toString(resource.getStream()));
    }

    @Test
    void testGetFeatureWithLineNumber() {
        String path = "classpath:com/intuit/karate/resource/test.feature:6";
        List<Feature> features = ResourceUtils.findFeatureFiles(new File(""), Collections.singletonList(path));
        assertEquals(1, features.size());
        assertEquals(6, features.get(0).getCallLine());
    }

    @Test
    void testClassPathToFileThatExists() {
        File file = ResourceUtils.classPathToFile("com/intuit/karate/resource/test1.txt");
        assertTrue(file.exists());
    }

    @Test
    void testClassPathToFileThatDoesNotExist() {
        File file = ResourceUtils.classPathToFile("com/intuit/karate/resource/nope.txt");
        assertNull(file);
    }
    
    @Test
    void testFindJsFilesFromFileSystem() {
        Set<String> files = ResourceUtils.findJsFilesInDirectory(new File("src/test/java/demo").getAbsoluteFile());
        assertEquals(4, files.size());
        Match.that(new ArrayList(files)).contains("['api/demo.js', 'api/cats.js', 'api/payments.js', 'api/render.js']");
    }
    
    @Test
    void testFindJsFilesFromClassPath() {
        Set<String> files = ResourceUtils.findJsFilesInClassPath("demo");
        assertEquals(4, files.size());
        Match.that(new ArrayList(files)).contains("['/api/demo.js', '/api/cats.js', '/api/payments.js', '/api/render.js']");
    }    

}
