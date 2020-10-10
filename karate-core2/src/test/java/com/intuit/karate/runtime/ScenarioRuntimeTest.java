package com.intuit.karate.runtime;

import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import static com.intuit.karate.runtime.RuntimeUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class ScenarioRuntimeTest {

    static final Logger logger = LoggerFactory.getLogger(ScenarioRuntimeTest.class);

    ScenarioRuntime sr;

    Object get(String name) {
        return sr.engine.vars.get(name).getValue();
    }

    ScenarioRuntime run(String... lines) {
        sr = runScenario(lines);
        return sr;
    }

    private void matchVarEquals(String name, Object expected) {
        MatchResult mr = Match.that(get(name)).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }

    @Test
    void testDefAndMatch() {
        run(
                "def a = 1 + 2",
                "match a == 3"
        );
        assertEquals(3, get("a"));
        assertFalse(sr.result.isFailed());
        run(
                "def a = 1 + 2",
                "match a == 4"
        );
        assertTrue(sr.result.isFailed());
    }

    @Test
    void testConfig() {
        System.setProperty("karate.env", "");
        System.setProperty("karate.config.dir", "");
        run("def foo = configSource");
        matchVarEquals("foo", "normal");
        System.setProperty("karate.config.dir", "src/test/java/com/intuit/karate/runtime");
        run("def foo = configSource");
        matchVarEquals("foo", "custom");
        System.setProperty("karate.env", "dev");
        run("def foo = configSource");
        matchVarEquals("foo", "custom-env");
        // reset for other tests    
        System.setProperty("karate.env", "");
        System.setProperty("karate.config.dir", "");
    }

    @Test
    void testReadFunction() {
        run(
                "def foo = karate.read('data.json')"
        );
        matchVarEquals("foo", "{ hello: 'world' }");
    }

    @Test
    void testCallJsFunction() {
        run(
                "def fun = function(a){ return a + 1 }",
                "def foo = call fun 2"
        );
        matchVarEquals("foo", 3);
    }

    @Test
    void testCallKarateFeature() {
        run(
                "def b = 'bar'",
                "def res = call read('called1.feature')"
        );
        matchVarEquals("res", "{ a: 1, b: 'bar', foo: { hello: 'world' }, configSource: 'normal', __arg: null, __loop: -1 }");
        run(
                "def b = 'bar'",
                "def res = call read('called1.feature') { foo: 'bar' }"
        );  
        matchVarEquals("res", "{ a: 1, b: 'bar', foo: { hello: 'world' }, configSource: 'normal', __arg: { foo: 'bar' }, __loop: -1 }");
        run(
                "def b = 'bar'",
                "def res = call read('called1.feature') [{ foo: 'bar' }]"
        );  
        matchVarEquals("res", "[{ a: 1, b: 'bar', foo: { hello: 'world' }, configSource: 'normal', __arg: { foo: 'bar' }, __loop: 0 }]"); 
        run(
                "def b = 'bar'",
                "def fun = function(i){ if (i == 1) return null; return { index: i } }",
                "def res = call read('called1.feature') fun"
        );  
        matchVarEquals("res", "[{ a: 1, b: 'bar', foo: { hello: 'world' }, configSource: 'normal', __arg: { index: 0 }, __loop: 0, index: 0, fun: '#ignore' }]");         
    }
    
    @Test
    void testCallOnce() {
        run(
                "def uuid = function(){ return java.util.UUID.randomUUID() + '' }",
                "def first = callonce uuid",
                "def second = callonce uuid"                
        );
        matchVarEquals("first", get("second"));
    }

}
