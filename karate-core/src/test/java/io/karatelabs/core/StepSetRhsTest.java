package io.karatelabs.core;

import org.junit.jupiter.api.Test;

import static io.karatelabs.core.TestUtils.*;

/**
 * V1 RHS semantics for the `set` keyword. Sister suite to the def/match coverage
 * in StepDataTypesTest / StepMatchTest added by the #2813 fix — `set` was missed
 * by that commit and routed only through the JS engine, which broke `#(...)`
 * and hyphenated keys.
 */
class StepSetRhsTest {

    @Test
    void testSetFullReplacementWithEmbeddedExpr() {
        // `set foo = { ... }` with unquoted #(id) — works in v1, regressed until fixed
        ScenarioRuntime sr = run("""
                * def id = 123
                * def foo = { id: 0, name: 'old' }
                * set foo = { id: #(id), name: 'new' }
                * match foo == { id: 123, name: 'new' }
                """);
        assertPassed(sr);
    }

    @Test
    void testSetFullReplacementWithHyphenatedKeys() {
        // hyphenated keys — works in v1's relaxed JSON, fails as JS subtraction
        ScenarioRuntime sr = run("""
                * def headers = {}
                * set headers = { Accept: 'application/json', Content-Type: 'application/json', Idempotency-Key: 'abc-123' }
                * match headers['Content-Type'] == 'application/json'
                * match headers['Idempotency-Key'] == 'abc-123'
                """);
        assertPassed(sr);
    }

    @Test
    void testSetFullReplacementParenWrappedForcesJs() {
        // paren-wrap escape hatch for forcing JS / ES6 evaluation, same as `def`
        ScenarioRuntime sr = run("""
                * def id = 123
                * def name = 'sample'
                * def foo = { id: 0, name: 'old' }
                * set foo = ({ id, name })
                * match foo == { id: 123, name: 'sample' }
                """);
        assertPassed(sr);
    }

    @Test
    void testSetJsonPathWithEmbeddedExpr() {
        // path-set with #(...) substitution in the value — also goes through Karate JSON now
        ScenarioRuntime sr = run("""
                * def id = 123
                * def foo = { meta: { id: 0, name: 'old' } }
                * set foo.meta = { id: #(id), name: 'new' }
                * match foo.meta == { id: 123, name: 'new' }
                """);
        assertPassed(sr);
    }

    @Test
    void testSetFullReplacementArrayLiteralWithEmbeddedExpr() {
        // arrays as well as objects — `[` is the other branch of looksLikeJson
        ScenarioRuntime sr = run("""
                * def a = 1
                * def b = 2
                * def arr = []
                * set arr = [ #(a), #(b), 3 ]
                * match arr == [1, 2, 3]
                """);
        assertPassed(sr);
    }
}
