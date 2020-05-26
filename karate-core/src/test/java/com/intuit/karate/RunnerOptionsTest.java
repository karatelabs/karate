package com.intuit.karate;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class RunnerOptionsTest {
    
    private static final String INTELLIJ1 = "com.intellij.rt.execution.application.AppMain cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --name ^get users and then get first by id$ --glue com.intuit.karate /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature";
    private static final String INTELLIJ2 = "cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --glue com.intuit.karate /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos";
    private static final String INTELLIJ3 = "cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --name ^create and retrieve a cat$ --glue com.intuit.karate /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature";
    private static final String INTELLIJ4 = "cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --name \"^test name$\"";

    private static final String ECLIPSE1 = "com.intuit.karate.StepDefs - cucumber.api.cli.Main /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/resources/com/intuit/karate/junit4/demos/users.feature --glue classpath: --plugin pretty --monochrome";    

    @Test
    public void testArgs() {
        RunnerOptions options = RunnerOptions.parseStringArgs(new String[]{});
        assertNull(options.features);
        assertNull(options.tags);
        assertNull(options.name);
        options = RunnerOptions.parseStringArgs(new String[]{"--name", "foo"});
        assertNull(options.features);
        assertNull(options.tags);
        assertEquals("foo", options.name);
        options = RunnerOptions.parseStringArgs(new String[]{"--tags", "~@ignore"});
        assertNull(options.features);
        assertEquals("~@ignore", options.tags.get(0));
        assertNull(options.name);
        options = RunnerOptions.parseStringArgs(new String[]{"--tags", "~@ignore", "foo.feature"});
        assertEquals("foo.feature", options.features.get(0));
        assertEquals("~@ignore", options.tags.get(0));
        assertNull(options.name);
    }

    @Test
    public void testParsingCommandLine() {
        RunnerOptions options = RunnerOptions.parseCommandLine(INTELLIJ1);
        assertEquals("^get users and then get first by id$", options.getName());
        assertNull(options.getTags());
        assertEquals(1, options.getFeatures().size());
        assertEquals("/Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature", options.getFeatures().get(0));
        options = RunnerOptions.parseCommandLine(ECLIPSE1);
        assertEquals(1, options.getFeatures().size());
        assertEquals("/Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/resources/com/intuit/karate/junit4/demos/users.feature", options.getFeatures().get(0));
    }
    
    @Test
    public void testParsingCommandLine2() {
        RunnerOptions options = RunnerOptions.parseCommandLine(INTELLIJ4);
        assertEquals("^test name$", options.getName());
    }    

}
