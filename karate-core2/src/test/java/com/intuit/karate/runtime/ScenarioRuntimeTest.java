package com.intuit.karate.runtime;

import com.intuit.karate.match.Match;
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
    void testReadFunction() {
        run(
                "def foo = karate.read('data.json')"
        );
        Match.that(get("foo")).isEqualTo("{ hello: 'world' }");
    }

    @Test
    void testCallJsFunction() {
        run(
                "def fun = function(a){ return a + 1 }",
                "def foo = call fun 2"
        );
        Match.that(get("foo")).isEqualTo(3);
    }

    @Test
    void testCallKarateFeature() {
        run(
                "def res = call read('called1.feature')"                
        );
        Match.that(get("res")).isEqualTo("{ a: 1, foo: { hello: 'world' } }");
    }

}
