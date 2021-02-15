package com.intuit.karate.core;

import com.intuit.karate.TestUtils;
import com.intuit.karate.Match;
import com.intuit.karate.report.Report;
import com.intuit.karate.report.SuiteReports;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class FeatureFailRunner {

    static final Logger logger = LoggerFactory.getLogger(FeatureFailRunner.class);

    FeatureRuntime fr;

    private FeatureRuntime run(String name) {
        return run(name, null);
    }

    private FeatureRuntime run(String name, String configDir) {
        fr = TestUtils.runFeature("classpath:com/intuit/karate/core/" + name, configDir);
        return fr;
    }

    private File report() {
        Report report = SuiteReports.DEFAULT.featureReport(fr.suite, fr.result);
        File file = report.render("target/temp");
        logger.debug("saved report: {}", file.getAbsolutePath());
        return file;
    }

    private void match(Object actual, Object expected) {
        Match.Result mr = Match.evaluate(actual).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }

    private void matchContains(Object actual, Object expected) {
        Match.Result mr = Match.evaluate(actual).contains(expected);
        assertTrue(mr.pass, mr.message);
    }

    @Test
    void testFailJs() {
        run("fail-js.feature");
        assertTrue(fr.result.isFailed());
        report();
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
    void testExec() {
        run("exec.feature");
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

    @Test
    void testUsersDoc() {
        run("users-doc.feature");
        assertFalse(fr.result.isFailed());
        report();
    }

    @Test
    void testUiGoogle() {
        run("ui-google.feature");
        assertFalse(fr.result.isFailed());
        report();
    }

}
