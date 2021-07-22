package com.intuit.karate.core;

import com.intuit.karate.Match;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.TestUtils;
import com.intuit.karate.report.Report;
import com.intuit.karate.report.SuiteReports;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author pthomas3
 */
class FeatureRuntimeTest {

    static final Logger logger = LoggerFactory.getLogger(FeatureRuntimeTest.class);

    boolean fail;
    FeatureRuntime fr;

    @BeforeEach
    void beforeEach() {
        fail = false;
    }

    private FeatureRuntime run(String name) {
        return run(name, null);
    }

    private FeatureRuntime run(String name, String configDir) {
        fr = TestUtils.runFeature("classpath:com/intuit/karate/core/" + name, configDir);
        if (fail) {
            assertTrue(fr.result.isFailed());
        } else {
            assertFalse(fr.result.isFailed());
        }
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
    void testFail1() {
        fail = true;
        run("fail1.feature");
    }

    @Test
    void testCallOnce() {
        run("callonce-bg.feature");
    }

    @Test
    void testCallOnceWithUtilsPresentInKarateConfig() {
        run("callonce-bg.feature", "classpath:com/intuit/karate/core");
    }

    @Test
    void testCallOnceGlobal() {
        run("callonce-global.feature");
    }

    @Test
    void testTags() {
        run("tags.feature");
        match(fr.result.getVariables(), "{ configSource: 'normal', functionFromKarateBase: '#notnull', tagNames: ['two=foo,bar', 'one'], tagValues: { one: [], two: ['foo', 'bar'] } }");
    }

    @Test
    void testAbort() {
        run("abort.feature");
        match(fr.result.getVariables(), "{ configSource: 'normal', functionFromKarateBase: '#notnull', before: true }");
    }

    @Test
    void testFailApi() {
        fail = true;
        run("fail-api.feature");
        match(fr.result.getVariables(), "{ configSource: 'normal', functionFromKarateBase: '#notnull', before: true }");
    }

    @Test
    void testCallFeatureFromJs() {
        run("call-js.feature");
        matchContains(fr.result.getVariables(), "{ calledVar: 'hello world' }");
    }

    @Test
    void testCallJsFromFeatureUtilsDefinedInKarateConfig() {
        run("karate-config-fn.feature", "classpath:com/intuit/karate/core/");
        matchContains(fr.result.getVariables(), "{ helloVar: 'hello world' }");
    }

    @Test
    void testCallOnceJsFromFeatureUtilsDefinedInKarateConfig() {
        System.setProperty("karate.env", "callonce");
        run("callonce-config.feature", "classpath:com/intuit/karate/core/");
        matchContains(fr.result.getVariables(), "{ foo: 'hello foo' }");
        System.clearProperty("karate.env");
    }

    @Test
    void testKarateJsGetScenario() {
        System.setProperty("karate.env", "getscenario");
        run("karate-config-getscenario.feature", "classpath:com/intuit/karate/core/");
        System.clearProperty("karate.env");
    }

    @Test
    void testKarateJsFromKarateBase() {
        System.setProperty("karate.env", "frombase");
        run("karate-config-frombase.feature", "classpath:com/intuit/karate/core/");
        System.clearProperty("karate.env");
    }

    @Test
    void testCallByTag() {
        run("call-by-tag.feature");
    }

    @Test
    void testCallByTagCalled() {
        run("call-by-tag-called.feature");
        matchContains(fr.result.getVariables(), "{ bar: 3 }"); // last scenario
    }

    @Test
    void testCopyAndClone() {
        run("copy.feature");
    }

    @Test
    void testMatchEachMagicVariables() {
        run("match-each-magic-variables.feature");
    }

    @Test
    void testEvalAndSet() {
        run("eval-and-set.feature");
    }

    @Test
    void testExtract() {
        run("extract.feature");
    }

    @Test
    void testConfigureInJs() {
        run("configure-in-js.feature");
    }

    @Test
    void testTable() {
        run("table.feature");
    }

    @Test
    void testSet() {
        run("set.feature");
    }

    @Test
    void testSetXml() {
        run("set-xml.feature");
    }

    @Test
    void testJsRead() {
        run("jsread/js-read.feature");
    }

    @Test
    void testJsRead2() {
        run("jsread/js-read-2.feature");
    }

    @Test
    void testJsRead3() {
        run("jsread/js-read-3.feature");
    }

    @Test
    void testJsRead4() {
        run("jsread/js-read-4.feature");
    }

    @Test
    void testJsMapRepeat() {
        run("js-map-repeat.feature");
    }

    @Test
    void testCallFeature() {
        run("call-feature.feature");
    }

    @Test
    void testOutlineGenerator() {
        run("outline-generator.feature");
    }

    @Test
    void testToBean() {
        run("to-bean.feature");
    }

    @Test
    void testOutlineBackground() {
        run("outline-background.feature");
    }

    @Test
    void testOutlineConfigJsParallel() {
        Results results = Runner.path("classpath:com/intuit/karate/core/outline-config-js.feature")
                .configDir("src/test/java/com/intuit/karate/core")
                .parallel(2);
        assertEquals(0, results.getFailCount());
    }

    @Test
    void testOutlineConfigJsCallSingleParallel() {
        Results results = Runner.path("classpath:com/intuit/karate/core/outline-config-js.feature")
                .configDir("src/test/java/com/intuit/karate/core")
                .karateEnv("callsingle")
                .parallel(2);
        assertEquals(0, results.getFailCount());
    }

    @Test
    void testCallSingleOutlineExampleByTag() {
        Results results = Runner.path("classpath:com/intuit/karate/core/call-single-tag.feature")
                .configDir("src/test/java/com/intuit/karate/core")
                .karateEnv("callsingletag")
                .tags("@runme")
                .parallel(1);
        assertEquals(0, results.getFailCount());
    }

    @Test
    void testCallArg() {
        run("call-arg.feature");
    }

    @Test
    void testCallArgNull() {
        run("call-arg-null.feature");
    }

    @Test
    void testIgnoreStepFailure() {
        fail = true;
        run("ignore-step-failure.feature");
        Report report = SuiteReports.DEFAULT.featureReport(fr.suite, fr.result);
        report.render("target/report-test");
        // error log will should have logs on all failures
    }

    @Test
    void testKarateFork() {
        run("fork.feature");
    }

    @Test
    void testCsv() {
        run("csv.feature");
    }

    @Test
    void testXmlPretty() {
        run("xml-pretty.feature");
    }

    @Test
    void testMatchStep() {
        run("match-step.feature");
    }

    @Test
    void testCallJsonPath() {
        run("call-jsonpath.feature");
    }

}
