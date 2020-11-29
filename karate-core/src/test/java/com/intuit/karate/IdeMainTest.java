package com.intuit.karate;

import com.intuit.karate.cli.IdeMain;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class IdeMainTest {

    static final String INTELLIJ1 = "com.intellij.rt.execution.application.AppMain cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --name ^get users and then get first by id$ --glue com.intuit.karate /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature";
    static final String INTELLIJ2 = "cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --glue com.intuit.karate /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos";
    static final String INTELLIJ3 = "cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --name ^create and retrieve a cat$ --glue com.intuit.karate /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature";
    static final String INTELLIJ4 = "cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --name \"^test name$\"";

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
        Main options = IdeMain.parseCommandLine(INTELLIJ1);
        assertEquals("^get users and then get first by id$", options.name);
        assertNull(options.tags);
        assertEquals(1, options.paths.size());
        assertEquals("/Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature", options.paths.get(0));
        options = IdeMain.parseCommandLine(ECLIPSE1);
        assertEquals(1, options.paths.size());
        assertEquals("/Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/resources/com/intuit/karate/junit4/demos/users.feature", options.paths.get(0));
    }

    @Test
    void testParsingCommandLine2() {
        Main options = IdeMain.parseCommandLine(INTELLIJ4);
        assertEquals("^test name$", options.name);
    }
    
    @Test
    void testAbsolutePath() {
        String[] args = new String[]{"/Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/resources/com/intuit/karate/junit4/demos/users.feature"};
        Main options = IdeMain.parseStringArgs(args);
        assertEquals(1, options.paths.size());
        
    }

}
