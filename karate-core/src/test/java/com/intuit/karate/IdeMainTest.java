package com.intuit.karate;

import com.intuit.karate.cli.IdeMain;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class IdeMainTest {
    
    static final org.slf4j.Logger logger = LoggerFactory.getLogger(IdeMainTest.class);

    static final String INTELLIJ1 = "cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --name ^get users and then get first by id$ --glue com.intuit.karate /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature";
    static final String INTELLIJ4 = "cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --name \"^test name$\"";
    static final String INTELLIJ5 = "cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --glue com.intuit.karate /Users/pthomas3/dev/zcode/karate/karate-demo/src/test/java/demo/cats/syntax-demo.feature";
    static final String INTELLIJ6 = "cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --glue com.intuit.karate /Users/pthomas3/dev/zcode/temp/my co test/src/test/java/examples/users/users.feature";

    static final String ECLIPSE1 = "com.intuit.karate.ScenarioActions - cucumber.api.cli.Main /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/resources/com/intuit/karate/junit4/demos/users.feature --glue classpath: --plugin pretty --monochrome";

    @Test
    void testArgs() {
        Main options = IdeMain.parseStringArgs(new String[]{});
        assertNull(options.paths);
        assertNull(options.tags);
        assertNull(options.name);
        options = IdeMain.parseStringArgs(new String[]{"--name", "foo"});
        assertNull(options.paths);
        assertNull(options.tags);
        assertEquals("foo", options.name);
        options = IdeMain.parseStringArgs(new String[]{"--tags", "~@ignore"});
        assertNull(options.paths);
        assertEquals("~@ignore", options.tags.get(0));
        assertNull(options.name);
        options = IdeMain.parseStringArgs(new String[]{"--tags", "~@ignore", "foo.feature"});
        assertEquals("foo.feature", options.paths.get(0));
        assertEquals("~@ignore", options.tags.get(0));
        assertNull(options.name);
    }

    @Test
    void testParsingCommandLine() {
        Main options = IdeMain.parseIdeCommandLine(INTELLIJ1);
        assertEquals("^get users and then get first by id$", options.name);
        assertNull(options.tags);
        assertEquals(1, options.paths.size());
        assertEquals("/Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature", options.paths.get(0));
        options = IdeMain.parseIdeCommandLine(ECLIPSE1);
        assertEquals(1, options.paths.size());
        assertEquals("/Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/resources/com/intuit/karate/junit4/demos/users.feature", options.paths.get(0));
    }

    @Test
    void testParsingCommandLine2() {
        Main options = IdeMain.parseIdeCommandLine(INTELLIJ4);
        assertEquals("^test name$", options.name);
    }
    
    @Test
    void testParsingCommandLine5() {
        Main options = IdeMain.parseIdeCommandLine(INTELLIJ5);
        assertEquals(1, options.paths.size());
        assertEquals("/Users/pthomas3/dev/zcode/karate/karate-demo/src/test/java/demo/cats/syntax-demo.feature", options.paths.get(0));
    } 
    
    @Test
    void testParsingCommandLine6() {
        Main options = IdeMain.parseIdeCommandLine(INTELLIJ6);
        assertEquals(1, options.paths.size());
        assertEquals("/Users/pthomas3/dev/zcode/temp/my co test/src/test/java/examples/users/users.feature", options.paths.get(0));
    }    
    
    @Test
    void testAbsolutePath() {
        String[] args = new String[]{"/Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/resources/com/intuit/karate/junit4/demos/users.feature"};
        Main options = IdeMain.parseStringArgs(args);
        assertEquals(1, options.paths.size());
    }

    @Test
    void testPartialKarateOptionsFromSystemProperties() {
        String line = "--tags @e2e";
        Main options = Main.parseKarateOptions(line);
        assertEquals(1, options.tags.size());
        assertEquals("@e2e", options.tags.get(0));
    }

    @Test
    void testParseKarateOptionAndQuotePath() {
        final String[] lines = new String[]{
                "/tmp/name with spaces.feature",
                " /tmp/name with spaces.feature ",
                "-H com.intuit.karate.RuntimeHook /tmp/name with spaces.feature",
                " -H com.intuit.karate.RuntimeHook /tmp/name with spaces.feature ",
                "-H com.intuit.karate.RuntimeHook \"/tmp/name with spaces.feature\"",
                " -H com.intuit.karate.RuntimeHook \"/tmp/name with spaces.feature\" ",
                "-H com.intuit.karate.RuntimeHook '/tmp/name with spaces.feature'",
                "-H com.intuit.karate.RuntimeHook -H com.intuit.karate.RuntimeHook /tmp/name with spaces.feature ",
                "-H com.intuit.karate.RuntimeHook,com.intuit.karate.RuntimeHook /tmp/name with spaces.feature "
        };

        for (String line : lines) {
            Main options = Main.parseKarateOptionAndQuotePath(line);
            assertEquals(1, options.paths.size());
            assertEquals("/tmp/name with spaces.feature", options.paths.get(0));
        }
    }
}
