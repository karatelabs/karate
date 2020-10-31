package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class FeatureReuseTest {

    static final Logger logger = LoggerFactory.getLogger(FeatureReuseTest.class);

    static String resultXml(String name) {
        Feature feature = FeatureParser.parse("classpath:com/intuit/karate/core/" + name);
        FeatureResult result = Engine.executeFeatureSync(null, feature, null, null);
        File file = Engine.saveResultXml("target", result, null);
        return FileUtils.toString(file);
    }

    @Test
    void testFailureInCalledShouldFailTest() throws Exception {
        String contents = resultXml("caller.feature");
        assertTrue(contents.contains("assert evaluated to false: input != 4"));
    }

    @Test
    void testArgumentsPassedForSharedScope() throws Exception {
        String contents = resultXml("caller-shared.feature");
        assertTrue(contents.contains("passed"));
    }

    @Test
    void testCallerTwo() throws Exception {
        String contents = resultXml("caller_2.feature");
        assertTrue(contents.contains("passed"));
    }

}
