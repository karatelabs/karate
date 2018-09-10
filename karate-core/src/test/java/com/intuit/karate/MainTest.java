package com.intuit.karate;

import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class MainTest {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MainTest.class);

    @Test
    public void testParsingCommandLine() {
        KarateOptions options = KarateOptions.parseCommandLine(IdeUtilsTest.INTELLIJ1);
        assertEquals("^get users and then get first by id$", options.getName());
        assertNull(options.getTags());
        assertEquals(1, options.getFeatures().size());
        assertEquals("/Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature", options.getFeatures().get(0));
        options = KarateOptions.parseCommandLine(IdeUtilsTest.ECLIPSE1);
        assertEquals(1, options.getFeatures().size());
        assertEquals("/Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/resources/com/intuit/karate/junit4/demos/users.feature", options.getFeatures().get(0));
    }

}
