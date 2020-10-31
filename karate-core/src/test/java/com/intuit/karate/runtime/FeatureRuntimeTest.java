package com.intuit.karate.runtime;

import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
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
        fr = RuntimeUtils.runFeature("classpath:com/intuit/karate/runtime/" + name);
        return fr;
    }
    
    private void match(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).isEqualTo(expected);
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
    void testCallOnceBg() {
        run("callonce-bg.feature");
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

}
