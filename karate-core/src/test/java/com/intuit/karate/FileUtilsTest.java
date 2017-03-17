package com.intuit.karate;

import java.io.InputStream;
import org.apache.commons.io.IOUtils;

import static org.junit.Assert.*;
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
        assertTrue(s.trim().startsWith("/*"));
    }

    @Test
    public void testExtractingFeaturePathFromCommandLine() {
        String expected = "/Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature";
        String cwd = "/Users/pthomas3/dev/zcode/karate/karate-junit4";
        String intelllij = "com.intellij.rt.execution.application.AppMain cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --name ^get users and then get first by id$ --glue com.intuit.karate /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature";
        String path = FileUtils.getFeaturePath(intelllij, cwd);
        assertEquals(expected, path);
        String eclipse = "com.intuit.karate.StepDefs - cucumber.api.cli.Main /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature --glue classpath: --plugin pretty --monochrome";
        path = FileUtils.getFeaturePath(eclipse, cwd);
        assertEquals(expected, path);
    }
    
}
