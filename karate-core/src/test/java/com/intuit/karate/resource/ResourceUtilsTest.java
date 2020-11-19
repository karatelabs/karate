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
import java.io.File;
import java.util.List;
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
        List<Resource> list = ResourceUtils.findFilesByExtension("txt", new File("src/test/java/com/intuit/karate/resource"));
        assertEquals(1, list.size());
        Resource resource = list.iterator().next();
        assertTrue(resource.isFile());
        assertFalse(resource.isClassPath());
        assertEquals("src/test/java/com/intuit/karate/resource/test1.txt", resource.getRelativePath());
        assertEquals("src/test/java/com/intuit/karate/resource/test1.txt", resource.getPrefixedPath());
        assertEquals("foo", FileUtils.toString(resource.getStream()));
    }

    @Test
    void testFindJarFilesByExtension() {
        List<Resource> list = ResourceUtils.findResourcesByExtension("properties", "cucumber");
        assertEquals(1, list.size());
        Resource resource = list.iterator().next();
        assertFalse(resource.isFile());
        assertTrue(resource.isClassPath());        
        assertEquals("cucumber/version.properties", resource.getRelativePath());
        assertEquals("classpath:cucumber/version.properties", resource.getPrefixedPath());
        assertEquals("cucumber-jvm.version=1.2.5", FileUtils.toString(resource.getStream()));
    }
    
    @Test
    void testFindClassPathFilesByExtension() {
        List<Resource> list = ResourceUtils.findResourcesByExtension("txt", "com/intuit/karate/resource");
        assertEquals(1, list.size());
        Resource resource = list.iterator().next();
        assertTrue(resource.isFile());
        assertTrue(resource.isClassPath());        
        assertEquals("com/intuit/karate/resource/test1.txt", resource.getRelativePath());
        assertEquals("classpath:com/intuit/karate/resource/test1.txt", resource.getPrefixedPath());
        assertEquals("foo", FileUtils.toString(resource.getStream()));
    }    

}
