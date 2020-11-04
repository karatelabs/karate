package com.intuit.karate.runtime;

import com.intuit.karate.core.HtmlFeatureReport;
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
class FeatureRuntimeTest {

    static final Logger logger = LoggerFactory.getLogger(FeatureRuntimeTest.class);

    FeatureRuntime fr;

    private FeatureRuntime run(String name) {
        return run(name, null);
    }

    private FeatureRuntime run(String name, String configDir) {
        fr = RuntimeUtils.runFeature("classpath:com/intuit/karate/runtime/" + name, configDir);
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
    void testPrint() {
        run("print.feature");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testFail1() {
        run("fail1.feature");
        assertTrue(fr.result.isFailed());
    }

    @Test
    void testCallOnce() {
        run("callonce-bg.feature");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testCallOnceWithUtilsPresentInKarateConfig() {
        run("callonce-bg.feature", "classpath:com/intuit/karate/runtime");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testCallOnceGlobal() {
        run("callonce-global.feature");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testTags() {
        run("tags.feature");
        assertFalse(fr.result.isFailed());
        match(fr.getResult(), "{ configSource: 'normal', tagNames: ['two=foo,bar', 'one'], tagValues: { one: [], two: ['foo', 'bar'] } }");
    }

    @Test
    void testAbort() {
        run("abort.feature");
        assertFalse(fr.result.isFailed());
        match(fr.getResult(), "{ configSource: 'normal', before: true }");
    }

    @Test
    void testFailApi() {
        run("fail-api.feature");
        assertTrue(fr.result.isFailed());
        match(fr.getResult(), "{ configSource: 'normal', before: true }");
    }

    @Test
    void testCallFeatureFromJs() {
        run("call-js.feature");
        assertFalse(fr.result.isFailed());
        matchContains(fr.getResult(), "{ calledVar: 'hello world' }");
    }

    @Test
    void testCallJsFromFeatureUtilsDefinedInKarateConfig() {
        run("karate-config-fn.feature", "classpath:com/intuit/karate/runtime");
        assertFalse(fr.result.isFailed());
        matchContains(fr.getResult(), "{ helloVar: 'hello world' }");
    }

    @Test
    void testCallByTag() {
        run("call-by-tag.feature");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testCopyAndClone() {
        run("copy.feature");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testMatchEachMagicVariables() {
        run("match-each-magic-variables.feature");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testEvalAndSet() {
        run("eval-and-set.feature");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testExtract() {
        run("extract.feature");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testConfigureInJs() {
        run("configure-in-js.feature");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testTable() {
        run("table.feature");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testSet() {
        run("set.feature");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testSetXml() {
        run("set-xml.feature");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testJsRead() {
        run("js-read.feature");
        assertFalse(fr.result.isFailed());
    }

    @Test
    void testJsMapRepeat() {
        run("js-map-repeat.feature");
        assertFalse(fr.result.isFailed());
    }
    
    @Test
    void testFork() {
        run("fork.feature");
        assertFalse(fr.result.isFailed());        
    }

}
