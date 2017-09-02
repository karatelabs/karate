package com.intuit.karate;

import com.intuit.karate.cucumber.FeatureFilePath;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;

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
    public void testClassLoading() throws Exception {
        ClassLoader cl = FileUtils.createClassLoader("src/main/java/com/intuit/karate");
        InputStream is = cl.getResourceAsStream("StepDefs.java");
        String s = FileUtils.toString(is);
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
    
    @Test
    public void testParsingFeatureFilePath() {
        String path = "/foo/src/test/java/demo/test.feature";
        File file = new File(path);
        FeatureFilePath ffp = FileUtils.parseFeaturePath(new File(path));
        assertEquals(file, ffp.file);
        logger.debug("search: {}", Arrays.toString(ffp.searchPaths));
    }
    
}
