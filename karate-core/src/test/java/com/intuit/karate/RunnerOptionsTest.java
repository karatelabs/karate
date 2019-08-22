package com.intuit.karate;

import com.intuit.karate.cli.MainTest;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class RunnerOptionsTest {

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
        RunnerOptions options = RunnerOptions.parseCommandLine(MainTest.INTELLIJ1);
        assertEquals("^get users and then get first by id$", options.getName());
        assertNull(options.getTags());
        assertEquals(1, options.getFeatures().size());
        assertEquals("/Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature", options.getFeatures().get(0));
        options = RunnerOptions.parseCommandLine(MainTest.ECLIPSE1);
        assertEquals(1, options.getFeatures().size());
        assertEquals("/Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/resources/com/intuit/karate/junit4/demos/users.feature", options.getFeatures().get(0));
    }

}
