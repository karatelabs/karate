package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Match;
import static com.intuit.karate.TestUtils.*;
import com.intuit.karate.http.ResourceType;
import java.io.File;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class ScenarioRuntimeTest {

    static final Logger logger = LoggerFactory.getLogger(ScenarioRuntimeTest.class);

    @BeforeEach
    void beforeEach() {
        fail = false;
    }

    boolean fail;
    ScenarioRuntime sr;

    Object get(String name) {
        return sr.engine.vars.get(name).getValue();
    }

    ScenarioRuntime run(String... lines) {
        sr = runScenario(null, lines);
        if (fail) {
            assertTrue(sr.result.isFailed());
        } else {
            assertFalse(sr.result.isFailed());
        }
        return sr;
    }

    private void matchVar(String name, Object expected) {
        match(get(name), expected);
    }

    @Test
    void testDefAndMatch() {
        run(
                "def a = 1 + 2",
                "match a == 3"
        );
        assertEquals(3, get("a"));
        fail = true;
        run(
                "def a = 1 + 2",
                "match a == 4"
        );
    }

    @Test
    void testConfigAndEnv() {
        System.clearProperty("karate.env");
        System.clearProperty("karate.config.dir");
        run("def foo = configSource");
        matchVar("foo", "normal");
        System.setProperty("karate.config.dir", "src/test/java/com/intuit/karate/core");
        run(
                "def foo = configSource",
                "def bar = karate.env"
        );
        matchVar("foo", "custom");
        matchVar("bar", null);
        System.setProperty("karate.env", "dev");
        run(
                "def foo = configSource",
                "def bar = karate.env"
        );
        matchVar("foo", "custom-env");
        matchVar("bar", "dev");
        // reset for other tests    
        System.clearProperty("karate.env");
        System.clearProperty("karate.config.dir");
    }

    @Test
    void testFunctionsFromGlobalConfig() {
        System.setProperty("karate.config.dir", "src/test/java/com/intuit/karate/core");
        run(
                "def foo = configUtilsJs.someText",
                "def bar = configUtilsJs.someFun()",
                "def res = call read('called2.feature')"
        );
        matchVar("foo", "hello world");
        matchVar("bar", "hello world");
        Match.that(get("res")).contains("{ calledBar: 'hello world' }");
        System.clearProperty("karate.env");
        System.clearProperty("karate.config.dir");
    }

    @Test
    void testReadFunction() {
        run(
                "def foo = read('data.json')",
                "def bar = karate.readAsString('data.json')"
        );
        matchVar("foo", "{ hello: 'world' }");
        Variable bar = sr.engine.vars.get("bar");
        Match.that(bar.getValue()).isEqualTo("{ hello: 'world' }");
        // fixed for windows
        assertEquals(((String) bar.getValue()).trim(), "{ \"hello\": \"world\" }");
    }

    @Test
    void testCallJsFunction() {
        run(
                "def fun = function(a){ return a + 1 }",
                "def foo = call fun 2"
        );
        matchVar("foo", 3);
    }

    @Test
    void testCallJsFunctionFromFile() {
        run(
                "def nextId = call read('increment.js')",
                "def res1 = nextId()",
                "def res2 = nextId()"
        );
        matchVar("res1", 1);
        matchVar("res2", 2);
        matchVar("_curId", 2);
    }

    @Test
    void testCallKarateFeature() {
        run(
                "def b = 'bar'",
                "def res = call read('called1.feature')"
        );
        matchVar("res", "{ a: 1, b: 'bar', foo: { hello: 'world' }, configSource: 'normal', __arg: null, __loop: -1 }");
        run(
                "def b = 'bar'",
                "def res = call read('called1.feature') { foo: 'bar' }"
        );
        matchVar("res", "{ a: 1, b: 'bar', foo: { hello: 'world' }, configSource: 'normal', __arg: { foo: 'bar' }, __loop: -1 }");
        run(
                "def b = 'bar'",
                "def res = call read('called1.feature') [{ foo: 'bar' }]"
        );
        matchVar("res", "[{ a: 1, b: 'bar', foo: { hello: 'world' }, configSource: 'normal', __arg: { foo: 'bar' }, __loop: 0 }]");
        run(
                "def b = 'bar'",
                "def fun = function(i){ if (i == 1) return null; return { index: i } }",
                "def res = call read('called1.feature') fun"
        );
        matchVar("res", "[{ a: 1, b: 'bar', foo: { hello: 'world' }, configSource: 'normal', __arg: { index: 0 }, __loop: 0, index: 0, fun: '#ignore' }]");
    }

    @Test
    void testCallOnce() {
        run(
                "def uuid = function(){ return java.util.UUID.randomUUID() + '' }",
                "def first = callonce uuid",
                "def second = callonce uuid"
        );
        matchVar("first", get("second"));
    }

    @Test
    void testCallSingle() {
        run(
                "def first = karate.callSingle('uuid.js')",
                "def second = karate.callSingle('uuid.js')"
        );
        matchVar("first", get("second"));
    }

    @Test
    void testCallSingleThatReturnsJson() {
        run(
                "def res = karate.callSingle('called3.js')"
        );
        matchVar("res", "{ varA: '2', varB: '3' }");
    }

    @Test
    void testKarateCallThatReturnsJson() {
        run(
                "def res = karate.call('called3.js')"
        );
        matchVar("res", "{ varA: '2', varB: '3' }");
    }

    @Test
    void testCallSingleWithinJs() {
        run(
                "def res = karate.call('called3-caller1.js')"
        );
        matchVar("res", "2");
    }

    @Test
    void testKarateCallWithinJs() {
        run(
                "def res = karate.call('called3-caller2.js')"
        );
        matchVar("res", "2");
    }

    @Test
    void testCallFromJs() {
        run(
                "def res = karate.call('called1.feature')"
        );
        matchVar("res", "{ a: 1, foo: { hello: 'world' }, configSource: 'normal', __arg: null, __loop: -1 }");
    }

    @Test
    void testCallWithJsonArgument() {
        run(
                "def fun = function(arg){ return [arg.first, arg.second] }",
                "def res = call fun { first: 'foo', second: 'bar' }"
        );
        matchVar("res", "['foo', 'bar']");
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
        // fixed for windows
        assertEquals(((String) get("fooXml")).trim(), "<hello>world</hello>");
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
        matchVar("bar", "{ hello: 'world' }");
        Object fooXml = get("fooXml");
        assertTrue(Match.that(fooXml).isXml());
        matchVar("fooXml", "<foo>bar</foo>");
        matchVar("baz", "{ a: 1 }");
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
        matchVar("fooKeys", "['a', 'b', 'c']");
        matchVar("fooVals", "[1, 2, 3]");
    }

    @Test
    void testMatch() {
        run(
                "def foo = { a: 1 }",
                "def mat1 = karate.match(foo, {a: 2})",
                "def mat2 = karate.match('foo == { a: 1 }')"
        );
        matchVar("mat1", "{ pass: false, message: '#notnull' }");
        matchVar("mat2", "{ pass: true, message: '#null' }");
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
        matchVar("res1", "{ value: 'a10b21c32' }");
        matchVar("res2", "{ value: 'a0b1c2' }");
    }

    @Test
    void testMap() {
        run(
                "def foo = [{ a: 1 }, { a: 2 }, { a: 3 }]",
                "def fun = function(x){ return x.a }",
                "def res = karate.map(foo, fun)"
        );
        matchVar("res", "[1, 2, 3]");
        run(
                "def foo = [{ a: 1 }, { a: 2 }, { a: 3 }]",
                "def res = karate.map(foo, x => x.a)"
        );
        matchVar("res", "[1, 2, 3]");
        run(
                "def foo = [{ a: 1 }, { a: 2 }, { a: 3 }]",
                "def fun = (x, i) => `${x.a}${i}`",
                "def res1 = karate.map(foo, fun)",
                "def res2 = foo.map(x => x.a)"
        );
        matchVar("res1", "['10', '21', '32']");
        matchVar("res2", "[1, 2, 3]");
    }

    @Test
    void testFilter() {
        run(
                "def foo = [{ a: 0 }, { a: 1 }, { a: 2 }]",
                "def res = karate.filter(foo, x => x.a > 0)"
        );
        matchVar("res", "[{ a: 1 }, { a: 2 }]");
    }

    @Test
    void testFilterKeys() {
        run(
                "def foo = { a: 1, b: 2, c: 3 }",
                "def res1 = karate.filterKeys(foo, 'a')",
                "def res2 = karate.filterKeys(foo, 'a', 'c')",
                "def res3 = karate.filterKeys(foo, ['b', 'c'])",
                "def res4 = karate.filterKeys(foo, { a: 2, c: 5})"
        );
        matchVar("res1", "{ a: 1 }");
        matchVar("res2", "{ a: 1, c: 3 }");
        matchVar("res3", "{ b: 2, c: 3 }");
        matchVar("res4", "{ a: 1, c: 3 }");
    }

    @Test
    void testRepeat() {
        run(
                "def res1 = karate.repeat(3, i => i + 1 )",
                "def res2 = karate.repeat(3, i => ({ a: 1 }))",
                "def res3 = karate.repeat(3, i => ({ a: i + 1 }))"
        );
        matchVar("res1", "[1, 2, 3]");
        matchVar("res2", "[{ a: 1 }, { a: 1 }, { a: 1 }]");
        matchVar("res3", "[{ a: 1 }, { a: 2 }, { a: 3 }]");
    }

    @Test
    void testMapWithKey() {
        run(
                "def foo = [1, 2, 3]",
                "def res = karate.mapWithKey(foo, 'val')"
        );
        matchVar("res", "[{ val: 1 }, { val: 2 }, { val: 3 }]");
    }

    @Test
    void testMergeAndAppend() {
        run(
                "def foo = { a: 1 }",
                "def res1 = karate.merge(foo, { b: 2 })",
                "def bar = [1, 2]",
                "def res2 = karate.append(bar, [3, 4])",
                "def res3 = [1, 2]",
                "karate.appendTo('res3', [3, 4])",
                "def res4 = [1, 2]",
                "karate.appendTo(res4, [3, 4])" // append to variable reference !
        );
        matchVar("res1", "{ a: 1, b: 2 }");
        matchVar("res2", "[1, 2, 3, 4]");
        matchVar("res3", "[1, 2, 3, 4]");
        matchVar("res4", "[1, 2, 3, 4]");
    }

    @Test
    void testJsonPath() {
        run(
                "def foo = { a: 1, b: { a: 2 } }",
                "def res1 = karate.jsonPath(foo, '$..a')"
        );
        matchVar("res1", "[1, 2]");
    }

    @Test
    void testLowerCase() {
        run(
                "def foo = { HELLO: 'WORLD' }",
                "def res1 = karate.lowerCase(foo)"
        );
        matchVar("res1", "{ hello: 'world' }");
    }

    @Test
    void testXmlPath() {
        run(
                "def foo = <bar><a><b>c</b></a></bar>",
                "def res1 = karate.xmlPath(foo, '/bar/a')"
        );
        matchVar("res1", "<a><b>c</b></a>");
    }

    @Test
    void testToBean() {
        run(
                "def foo = { foo: 'hello', bar: 5 }",
                "def res1 = karate.toBean(foo, 'com.intuit.karate.core.SimplePojo')"
        );
        SimplePojo sp = (SimplePojo) get("res1");
        assertEquals(sp.getFoo(), "hello");
        assertEquals(sp.getBar(), 5);
    }

    @Test
    void testToJson() {
        run(
                "def SP = Java.type('com.intuit.karate.core.SimplePojo')",
                "def pojo = new SP()",
                "def res1 = karate.toJson(pojo)",
                "def res2 = karate.toJson(pojo, true)"
        );
        matchVar("res1", "{ bar: 0, foo: null }");
        matchVar("res2", "{ bar: 0 }");
    }

    @Test
    void testToBeanAdvanced() {
        run(
                "def pojoType = 'com.intuit.karate.core.SimplePojo'",
                "def Pojo = Java.type(pojoType)",
                "def toPojo = function(x){ return karate.toBean(x, pojoType) }",
                "def toJson = function(x){ return karate.toJson(x, true) }",
                "def bean = new Pojo()",
                "bean.foo = 'hello'",
                "def pojo = toPojo({ bar: 5 })",
                "def json = toJson(pojo)"
        );
        matchVar("json", "{ bar: 5 }");
        SimplePojo bean = (SimplePojo) get("bean");
        assertEquals(0, bean.getBar());
        assertEquals("hello", bean.getFoo());
        SimplePojo pojo = (SimplePojo) get("pojo");
        assertEquals(5, pojo.getBar());
        assertNull(pojo.getFoo());
    }

    @Test
    void testToCsv() {
        run(
                "def foo = [{a: 1, b: 2}, { a: 3, b: 4 }]",
                "def res = karate.toCsv(foo)"
        );
        // fixed for windows
        match(((String) get("res")).replaceAll("[\r\n]+", "@"), "a,b@1,2@3,4@");
    }

    @Test
    void testEval() {
        run(
                "def foo = karate.eval('() => 1 + 2')",
                "def bar = foo()"
        );
        assertTrue(sr.engine.vars.get("foo").isJsFunction());
        matchVar("bar", 3);
    }

    @Test
    void testFromString() {
        run(
                "def foo = karate.fromString('{ hello: \"world\" }')",
                "def bar = karate.typeOf(foo)"
        );
        assertTrue(sr.engine.vars.get("foo").isMap());
        matchVar("bar", "map");
    }

    @Test
    void testEmbed() {
        run(
                "karate.embed('<h1>hello world</h1>', 'text/html')"
        );
        List<StepResult> results = sr.result.getStepResults();
        assertEquals(1, results.size());
        List<Embed> embeds = results.get(0).getEmbeds();
        assertEquals(1, embeds.size());
        assertEquals(embeds.get(0).getAsString(), "<h1>hello world</h1>");
        assertEquals(embeds.get(0).getResourceType(), ResourceType.HTML);
    }

    @Test
    void testStepLog() {
        run(
                "print 'hello world'"
        );
        List<StepResult> results = sr.result.getStepResults();
        assertEquals(1, results.size());
        String log = results.get(0).getStepLog();
        assertTrue(log.contains("[print] hello world"));
    }

    @Test
    void testWrite() {
        run(
                "def file = karate.write('hello world', 'runtime-test.txt')"
        );
        File file = (File) get("file");
        assertEquals(file.getParentFile().getName(), "target");
        assertEquals(file.getName(), "runtime-test.txt");
        assertEquals(FileUtils.toString(file), "hello world");
    }

    @Test
    void testJavaClassAsVariable() {
        run(
                "def Utils = Java.type('com.intuit.karate.core.MockUtils')",
                "def res = Utils.testBytes"
        );
        assertEquals(get("res"), MockUtils.testBytes);
    }

    @Test
    void testCallJsFunctionShared() {
        run(
                "def myFn = function(x){ return { myVar: x } }",
                "call myFn 'foo'"
        );
        assertEquals(get("myVar"), "foo");
    }

    @Test
    void testCallJsFunctionSharedJson() {
        run(
                "def myFn = function(x){ return { myVar: x.foo } }",
                "call myFn { foo: 'bar' }"
        );
        assertEquals(get("myVar"), "bar");
    }

    @Test
    void testSelfValidationWithVariables() {
        run(
                "def date = { month: 3 }",
                "def min = 1",
                "def max = 12",
                "match date == { month: '#? _ >= min && _ <= max' }"
        );
        assertFalse(sr.isFailed());
    }

    @Test
    void testReadAndMatchBytes() {
        run(
                "bytes data = read('karate-logo.png')",
                "match data == read('karate-logo.png')"
        );
        assertFalse(sr.isFailed());
    }

    @Test
    void testJsonEmbeddedExpressionFailuresAreNotBlockers() {
        run(
                "def expected = { a: '#number', b: '#(_$.a * 2)' }",
                "def actual = [{a: 1, b: 2}, {a: 2, b: 4}]",
                "match each actual == expected"
        );
        assertFalse(sr.isFailed());
    }

    @Test
    void testXmlEmbeddedExpressionFailuresAreNotBlockers() {
        run(
                "def expected = <foo att='#(bar)'>#(bar)</foo>",
                "def actual = <foo att=\"test\">test</foo>",
                "def bar = 'test'",
                "match actual == expected"
        );
        assertFalse(sr.isFailed());
    }

    @Test
    void testMatchEachMagicVariablesDontLeak() {
        run(
                "def actual = [{a: 1, b: 2}, {a: 2, b: 4}]",
                "match each actual == { a: '#number', b: '#(_$.a * 2)' }",
                "def res = { b: '#(_$.a * 2)' }"
        );
        assertFalse(sr.isFailed());
        matchVar("res", "{ b: '#string' }");
    }

    @Test
    void testMatchMagicVariables() {
        run(
                "def temperature = { celsius: 100, fahrenheit: 212 }",
                "match temperature contains { fahrenheit: '#($.celsius * 1.8 + 32)' }"
        );
        assertFalse(sr.isFailed());
    }

    @Test
    void testMatchSchema() {
        run(
                "def dogSchema = { id: '#string', color: '#string' }",
                "def schema = { id: '#string', name: '#string', dog: '##(dogSchema)' }",
                "def response1 = { id: '123', name: 'foo' }",
                "match response1 == schema",
                "def response2 = { id: '123', name: 'foo', dog: { id: '456', color: 'brown' } }",
                "match response2 == schema"
        );
        assertFalse(sr.isFailed());
    }

    @Test
    void testMatchSchemaMagicVariables() {
        run(
                "def response = { odds: [1, 2], count: 2 }",
                "match response == { odds: '#[$.count]', count: '#number' }"
        );
        assertFalse(sr.isFailed());
    }

    @Test
    void testJavaInteropStatic() {
        run(
                "def Utils = Java.type('com.intuit.karate.core.StaticUtils')",
                "def array = ['a', 'b', 'c']",
                "def res = Utils.concat(array)"
        );
        matchVar("res", "abc");
    }

    @Test
    void testJavaInteropBase64() {
        run(
                "def Base64 = Java.type('java.util.Base64')",
                "def res = Base64.encoder.encodeToString('hello'.getBytes())"
        );
        matchVar("res", java.util.Base64.getEncoder().encodeToString("hello".getBytes()));
    }

    @Test
    void testTypeConversionCsvEmpty() {
        run(
                "csv temp = ''"
        );
        matchVar("temp", "[]");
    }

    @Test
    void testJavaInteropParameters() {
        run(
                "def Utils = Java.type('com.intuit.karate.core.StaticUtils')",
                "def res1 = Utils.fromInt(2)",
                "def res2 = Utils.fromDouble(2)",
                "def res3 = Utils.fromDouble(2.0)",
                "def res4 = Utils.fromNumber(2.0)"
        );
        matchVar("res1", "value is 2");
        matchVar("res2", "value is 2.0");
        matchVar("res3", "value is 2.0");
        matchVar("res4", "value is 2.0");
    }

}
