package com.intuit.karate.core.runner;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Suite;
import com.intuit.karate.report.ReportUtils;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureRuntime;
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
        Feature feature = Feature.read("classpath:com/intuit/karate/core/runner/" + name);
        FeatureRuntime fr = FeatureRuntime.of(new Suite(), feature);
        fr.run();
        File file = ReportUtils.saveJunitXml("target", fr.result, null);
        return FileUtils.toString(file);
    }

    @Test
    void testFailureInCalledShouldFailTest() throws Exception {
        String contents = resultXml("caller.feature");
        assertTrue(contents.contains("did not evaluate to 'true': input != 4"));
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
