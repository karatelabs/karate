package com.intuit.karate;

import com.intuit.karate.cli.IdeMain;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

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
        options = IdeMain.parseStringArgs(new String[]{"--tags", "~@skipme"});
        assertNull(options.paths);
        assertEquals("~@skipme", options.tags.get(0));
        assertNull(options.name);
        options = IdeMain.parseStringArgs(new String[]{"--tags", "~@skipme", "foo.feature"});
        assertEquals("foo.feature", options.paths.get(0));
        assertEquals("~@skipme", options.tags.get(0));
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
    void testParsingCommandLineReportFormats() {
        Main options = IdeMain.parseIdeCommandLine("cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome -e local -f html,json,cucumber:json,junit:xml -g /dev/config/dir /dev/test/todos.feature:27");
        System.out.println();
        assertIterableEquals(options.formats, new ArrayList<String>() {
            {
                add("html");
                add("json");
                add("cucumber:json");
                add("junit:xml");
            }
        });
        Main options2 = IdeMain.parseIdeCommandLine("cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome -e local -f json,cucumber:json,junit:xml -g /dev/config/dir /dev/test/todos.feature:27");
        assertIterableEquals(options2.formats, new ArrayList<String>() {
            {
                add("json");
                add("cucumber:json");
                add("junit:xml");
            }
        });
        Main options3 = IdeMain.parseIdeCommandLine("cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome -e local -f ~html,json,cucumber:json,junit:xml -g /dev/config/dir /dev/test/todos.feature:27");
        assertIterableEquals(options3.formats, new ArrayList<String>() {
            {
                add("~html");
                add("json");
                add("cucumber:json");
                add("junit:xml");
            }
        });
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
            Main options = Main.parseKarateOptionsAndQuotePath(line);
            assertEquals(1, options.paths.size());
            assertEquals("/tmp/name with spaces.feature", options.paths.get(0));
        }

        String line = "-g C:\\test_cases\\config -e dev01 -H com.intuit.karate.RuntimeHook,com.intuit.karate.RuntimeHook /tmp/name with spaces.feature ";
        Main options = Main.parseKarateOptionsAndQuotePath(line);
        assertEquals(1, options.paths.size());
        assertEquals("C:\\test_cases\\config", options.configDir);
        assertEquals("dev01", options.env);
        assertEquals("/tmp/name with spaces.feature", options.paths.get(0));
    }

}
