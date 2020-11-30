package com.intuit.karate.core;

import com.intuit.karate.TestUtils;
import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class FeatureRuntimeRunner {

    static final Logger logger = LoggerFactory.getLogger(FeatureRuntimeRunner.class);

    FeatureRuntime fr;

    private FeatureRuntime run(String name) {
        return run(name, null);
    }

    private FeatureRuntime run(String name, String configDir) {
        fr = TestUtils.runFeature("classpath:com/intuit/karate/core/" + name, configDir);
        return fr;
    }

    private File report() {
        File file = HtmlFeatureReport.saveFeatureResult("target/temp", fr.result);
        logger.debug("saved report: {}", file.getAbsolutePath());
        return file;
    }

    private void match(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }

    private void matchContains(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).contains(expected);
        assertTrue(mr.pass, mr.message);
    }

    @Test
    void testFailJs() {
        run("fail-js.feature");
        assertTrue(fr.result.isFailed());
    }

    @Test
    void testCallSingleFail() {
        System.setProperty("karate.config.dir", "classpath:com/intuit/karate/core");
        System.setProperty("karate.env", "csfail");
        run("call-single-fail.feature");
        assertTrue(fr.result.isFailed());
        report();
    }

    @Test
    void testFork() {
        run("fork.feature");
    }

    @Test
    void testForkListener() {
        run("fork-listener.feature");
        assertFalse(fr.result.isFailed());
    }

}
