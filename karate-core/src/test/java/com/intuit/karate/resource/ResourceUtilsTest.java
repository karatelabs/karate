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
import java.util.Collection;
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

    @Test
    void testFindFilesByExtension() {
        Collection<Resource> list = ResourceUtils.findResourcesByExtension("txt", "src/test/java/com/intuit/karate/resource");
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
        Resource resource = ResourceUtils.getResource("src/test/java/com/intuit/karate/resource/test1.txt");
        assertTrue(resource.isFile());
        assertFalse(resource.isClassPath());
        assertEquals("src/test/java/com/intuit/karate/resource/test1.txt", resource.getRelativePath());
        assertEquals("src/test/java/com/intuit/karate/resource/test1.txt", resource.getPrefixedPath());
        assertEquals("foo", FileUtils.toString(resource.getStream()));
    }

    @Test
    void testResolveFile() {
        Resource temp = ResourceUtils.getResource("src/test/java/com/intuit/karate/resource/test1.txt");
        Resource resource = temp.resolve("test2.log");
        assertTrue(resource.isFile());
        assertFalse(resource.isClassPath());
        assertEquals("src/test/java/com/intuit/karate/resource/test2.log", resource.getRelativePath());
        assertEquals("src/test/java/com/intuit/karate/resource/test2.log", resource.getPrefixedPath());
        assertEquals("bar", FileUtils.toString(resource.getStream()));        
    }

    @Test
    void testFindJarFilesByExtension() {
        Collection<Resource> list = ResourceUtils.findResourcesByExtension("properties", "classpath:cucumber");
        assertEquals(1, list.size());
        Resource resource = list.iterator().next();
        assertFalse(resource.isFile());
        assertTrue(resource.isClassPath());
        assertEquals("cucumber/version.properties", resource.getRelativePath());
        assertEquals("classpath:cucumber/version.properties", resource.getPrefixedPath());
        assertEquals("cucumber-jvm.version=1.2.5", FileUtils.toString(resource.getStream()));
    }

    @Test
    void testGetJarFileByPath() {
        Resource resource = ResourceUtils.getResource("classpath:cucumber/version.properties");
        assertFalse(resource.isFile());
        assertTrue(resource.isClassPath());
        assertEquals("cucumber/version.properties", resource.getRelativePath());
        assertEquals("classpath:cucumber/version.properties", resource.getPrefixedPath());
        assertEquals("cucumber-jvm.version=1.2.5", FileUtils.toString(resource.getStream()));
    }
    
    @Test
    void testResolveJarFile() {
        Resource temp = ResourceUtils.getResource("classpath:cucumber/version.properties");
        Resource resource = temp.resolve("api/cli/USAGE.txt");
        assertFalse(resource.isFile());
        assertTrue(resource.isClassPath());
        assertEquals("cucumber/api/cli/USAGE.txt", resource.getRelativePath());
        assertEquals("classpath:cucumber/api/cli/USAGE.txt", resource.getPrefixedPath());    
    }    

    @Test
    void testFindClassPathFilesByExtension() {
        Collection<Resource> list = ResourceUtils.findResourcesByExtension("txt", "classpath:com/intuit/karate/resource");
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
        Resource resource = ResourceUtils.getResource("classpath:com/intuit/karate/resource/test1.txt");
        assertTrue(resource.isFile());
        assertTrue(resource.isClassPath());
        assertEquals("com/intuit/karate/resource/test1.txt", resource.getRelativePath());
        assertEquals("classpath:com/intuit/karate/resource/test1.txt", resource.getPrefixedPath());
        assertEquals("foo", FileUtils.toString(resource.getStream()));
    }
    
    @Test
    void testResolveClassPathFile() {
        Resource temp = ResourceUtils.getResource("classpath:com/intuit/karate/resource/test1.txt");
        Resource resource = temp.resolve("test2.log");
        assertTrue(resource.isFile());
        assertTrue(resource.isClassPath());
        assertEquals("com/intuit/karate/resource/test2.log", resource.getRelativePath());
        assertEquals("classpath:com/intuit/karate/resource/test2.log", resource.getPrefixedPath());
        assertEquals("bar", FileUtils.toString(resource.getStream()));        
    }  
    
    @Test
    void testResolveRelativeClassPathFile() {
        Resource temp = ResourceUtils.getResource("classpath:com/intuit/karate/resource/dir1/dir1.log");
        Resource resource = temp.resolve("../dir2/dir2.log");
        assertTrue(resource.isFile());
        assertTrue(resource.isClassPath());
        assertEquals("com/intuit/karate/resource/dir2/dir2.log", resource.getRelativePath());
        assertEquals("classpath:com/intuit/karate/resource/dir2/dir2.log", resource.getPrefixedPath());
        assertEquals("bar", FileUtils.toString(resource.getStream()));        
    }     

}
