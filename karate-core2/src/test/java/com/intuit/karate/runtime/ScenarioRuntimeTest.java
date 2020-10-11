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
                "def foo = read('data.json')",
                "def bar = karate.readAsString('data.json')"
        );
        matchVarEquals("foo", "{ hello: 'world' }");
        Variable bar = sr.engine.vars.get("bar");
        Match.that(bar.getValue()).isString();
        assertEquals(bar.getValue(), "{ \"hello\": \"world\" }\n");
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

    @Test
    void testToString() {
        run(
                "def foo = { hello: 'world' }",
                "def fooStr = karate.toString(foo)",
                "def fooPretty = karate.pretty(foo)",
                "def fooXml = karate.prettyXml(foo)"
        );
        assertEquals(get("fooStr"), "{\"hello\":\"world\"}");
        assertEquals(get("fooPretty"), "{\n  \"hello\": \"world\"\n}\n");
        assertEquals(get("fooXml"), "<hello>world</hello>\n");
    }

    @Test
    void testGetSetAndRemove() {
        run(
                "karate.set('foo', 1)",
                "karate.set('bar', { hello: 'world' })",
                "karate.set({ a: 2, b: 'hey' })",
                "karate.setXml('fooXml', '<foo>bar</foo>')",
                "copy baz = bar",
                "karate.set('baz', '$.a', 1)",
                "karate.remove('baz', '$.hello')",
                "copy bax = fooXml",
                "karate.setXml('bax', '/foo', '<a>1</a>')",
                "def getFoo = karate.get('foo')",
                "def getNull = karate.get('blah')",
                "def getDefault = karate.get('blah', 'foo')",
                "def getPath = karate.get('bar.hello')"
        );
        assertEquals(get("foo"), 1);
        assertEquals(get("a"), 2);
        assertEquals(get("b"), "hey");
        matchVarEquals("bar", "{ hello: 'world' }");
        Object fooXml = get("fooXml");
        Match.that(fooXml).isXml();
        Match.that(fooXml).isEqualTo("<foo>bar</foo>");
        matchVarEquals("baz", "{ a: 1 }");
        Match.that(get("bax")).isEqualTo("<foo><a>1</a></foo>");
        assertEquals(get("getFoo"), 1);
        assertEquals(get("getNull"), null);
        assertEquals(get("getDefault"), "foo");
        assertEquals(get("getPath"), "world");
    }

    @Test
    void testCollections() {
        run(
                "def foo = { a: 1, b: 2, c: 3 }",
                "def fooSize = karate.sizeOf(foo)",
                "def bar = [1, 2, 3]",
                "def barSize = karate.sizeOf(bar)",
                "def fooKeys = karate.keysOf(foo)",
                "def fooVals = karate.valuesOf(foo)"
        );
        assertEquals(get("fooSize"), 3);
        assertEquals(get("barSize"), 3);
        matchVarEquals("fooKeys", "['a', 'b', 'c']");
        matchVarEquals("fooVals", "[1, 2, 3]");
    }

    @Test
    void testMatch() {
        run(
                "def foo = { a: 1 }",
                "def mat1 = karate.match(foo, {a: 2})",
                "def mat2 = karate.match('foo == { a: 1 }')"
        );
        matchVarEquals("mat1", "{ pass: false, message: '#notnull' }");
        matchVarEquals("mat2", "{ pass: true, message: '#null' }");
    }

    @Test
    void testForEach() {
        run(
                "def foo = { a: 1, b: 2, c: 3 }",
                "def res1 = { value: '' }",
                "def fun = function(k, v, i){ res1.value += k + v + i }",
                "karate.forEach(foo, fun)",
                "def foo = ['a', 'b', 'c']",
                "def res2 = { value: '' }",
                "def fun = function(v, i){ res2.value += v + i }",
                "karate.forEach(foo, fun)"
        );
        matchVarEquals("res1", "{ value: 'a10b21c32' }");
        matchVarEquals("res2", "{ value: 'a0b1c2' }");
    }

    @Test
    void testMap() {
        run(
                "def foo = [{ a: 1 }, { a: 2 }, { a: 3 }]",
                "def fun = function(x){ return x.a }",
                "def res = karate.map(foo, fun)"
        );
        matchVarEquals("res", "[1, 2, 3]");
        run(
                "def foo = [{ a: 1 }, { a: 2 }, { a: 3 }]",
                "def fun = x => x.a",
                "def res = karate.map(foo, fun)"
        );
        matchVarEquals("res", "[1, 2, 3]");
        run(
                "def foo = [{ a: 1 }, { a: 2 }, { a: 3 }]",
                "def fun = (x, i) => `${x.a}${i}`",
                "def res1 = karate.map(foo, fun)",
                "def res2 = foo.map(x => x.a)"
        );
        matchVarEquals("res1", "['10', '21', '32']");
        matchVarEquals("res2", "[1, 2, 3]");
    }

}
