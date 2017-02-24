package com.intuit.karate;

import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class FileUtilsTest {
    
    @Test
    public void testClassLoading() throws Exception {
        ClassLoader cl = FileUtils.createClassLoader("src/main/java/com/intuit/karate");
        InputStream is = cl.getResourceAsStream("StepDefs.java");
        String s = IOUtils.toString(is, "utf-8");
        assertTrue(s.trim().startsWith("package "));
    }     
    
}
