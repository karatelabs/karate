package com.intuit.karate.runtime;

import com.intuit.karate.AssignType;
import com.intuit.karate.StringUtils;
import com.intuit.karate.data.Json;
import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import com.intuit.karate.match.MatchType;
import com.intuit.karate.match.MatchUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
public class ScenarioEngineTest {

    static final Logger logger = LoggerFactory.getLogger(ScenarioEngineTest.class);

    ScenarioEngine engine = new ScenarioEngine();

    @BeforeEach
    void beforeEach() {
        engine.init();
    }

    private void match(Object before, Object after) {
        Variable actual = new Variable(MatchUtils.parse(before));
        Variable expected = engine.evalEmbeddedExpressions(actual);
        MatchResult mr = Match.that(expected.getValue()).is(MatchType.EQUALS, MatchUtils.parse(after));
        assertTrue(mr.pass, mr.message);
    }

    private void assign(String name, String expression) {
        engine.assign(AssignType.AUTO, name, expression, false);
    }

    private void matchEquals(String lhs, String rhs) {
        assertTrue(engine.match(MatchType.EQUALS, lhs, null, rhs).pass);
    }

    private void matchNotEquals(String lhs, String rhs) {
        assertFalse(engine.match(MatchType.EQUALS, lhs, null, rhs).pass);
    }

    @Test
    void testHelpers() {
        assertTrue(ScenarioEngine.isVariable("foo"));
        assertTrue(ScenarioEngine.isXmlPath("/foo"));
        assertTrue(ScenarioEngine.isXmlPath("//foo"));
        assertTrue(ScenarioEngine.isXmlPathFunction("lower-case('Foo')"));
        assertTrue(ScenarioEngine.isXmlPathFunction("count(/journal/article)"));
        assertTrue(ScenarioEngine.isVariableAndSpaceAndPath("foo count(/journal/article)"));
        assertTrue(ScenarioEngine.isVariableAndSpaceAndPath("foo $"));
    }

    @Test
    void testVariableNameValidation() {
        assertTrue(ScenarioEngine.isValidVariableName("foo"));
        assertTrue(ScenarioEngine.isValidVariableName("foo_bar"));
        assertTrue(ScenarioEngine.isValidVariableName("foo_"));
        assertTrue(ScenarioEngine.isValidVariableName("foo1"));
        assertTrue(ScenarioEngine.isValidVariableName("a"));
        assertTrue(ScenarioEngine.isValidVariableName("a1"));
        // bad
        assertFalse(ScenarioEngine.isValidVariableName("foo.bar"));
        assertFalse(ScenarioEngine.isValidVariableName("foo-bar"));
        assertFalse(ScenarioEngine.isValidVariableName("$foo"));
        assertFalse(ScenarioEngine.isValidVariableName("$foo/bar"));
        assertFalse(ScenarioEngine.isValidVariableName("_foo"));
        assertFalse(ScenarioEngine.isValidVariableName("_foo_"));
        assertFalse(ScenarioEngine.isValidVariableName("0"));
        assertFalse(ScenarioEngine.isValidVariableName("2foo"));
    }

    @Test
    void testParsingVariableAndJsonPath() {
        assertEquals(StringUtils.pair("foo", "$"), ScenarioEngine.parseVariableAndPath("foo"));
        assertEquals(StringUtils.pair("foo", "$.bar"), ScenarioEngine.parseVariableAndPath("foo.bar"));
        assertEquals(StringUtils.pair("foo", "$['bar']"), ScenarioEngine.parseVariableAndPath("foo['bar']"));
        assertEquals(StringUtils.pair("foo", "$[0]"), ScenarioEngine.parseVariableAndPath("foo[0]"));
        assertEquals(StringUtils.pair("foo", "$[0].bar"), ScenarioEngine.parseVariableAndPath("foo[0].bar"));
        assertEquals(StringUtils.pair("foo", "$[0]['bar']"), ScenarioEngine.parseVariableAndPath("foo[0]['bar']"));
        assertEquals(StringUtils.pair("foo", "/bar"), ScenarioEngine.parseVariableAndPath("foo/bar"));
        assertEquals(StringUtils.pair("foo", "/"), ScenarioEngine.parseVariableAndPath("foo/"));
        assertEquals(StringUtils.pair("foo", "/"), ScenarioEngine.parseVariableAndPath("foo /"));
        assertEquals(StringUtils.pair("foo", "/bar"), ScenarioEngine.parseVariableAndPath("foo /bar"));
        assertEquals(StringUtils.pair("foo", "/bar/baz[1]/ban"), ScenarioEngine.parseVariableAndPath("foo/bar/baz[1]/ban"));
    }

    @Test
    void testJsFunction() {
        assertTrue(ScenarioEngine.isJavaScriptFunction("function(){ return { bar: 'baz' } }"));
        assertTrue(ScenarioEngine.isJavaScriptFunction("function() {   \n"
                + "  return { someConfig: 'someValue' }\n"
                + "}"));
        assertTrue(ScenarioEngine.isJavaScriptFunction("function fn(){ return { bar: 'baz' } }"));
        assertEquals("function(){}", ScenarioEngine.fixJavaScriptFunction("function fn(){}"));
    }

    @Test
    void testEmbeddedString() {
        match("hello", "hello");
        match("#(1)", 1);
        match("#(null)", null);
        match("#('foo')", "foo");
        match("##('foo')", "foo");
        match("##(null)", null);
        engine.eval("var bar = null");
        match("##(bar)", null);
    }

    @Test
    void testEmbeddedList() {
        engine.eval("var foo = 3");
        match("[1, 2, '#(foo)']", "[1, 2, 3]");
        engine.eval("var foo = [3, 4]");
        match("[1, 2, '#(foo)']", "[1, 2, [3, 4]]");
        engine.eval("var foo = null");
        match("[1, 2, '#(foo)']", "[1, 2, null]");
        match("[1, 2, '##(foo)']", "[1, 2]");
        match("[1, '##(foo)', 3]", "[1, 3]");
        engine.eval("var bar = null");
        match("['##(foo)', 2, '##(bar)']", "[2]");
    }

    @Test
    void testEmbeddedMap() {
        engine.eval("var foo = 2");
        match("{ a: 1, b: '#(foo)', c: 3}", "{ a: 1, b: 2, c: 3}");
        match("{ a: 1, b: '#(foo)', c: '#(foo)'}", "{ a: 1, b: 2, c: 2}");
        engine.eval("var bar = null");
        match("{ a: 1, b: '#(bar)', c: '#(foo)'}", "{ a: 1, b: null, c: 2}");
        match("{ a: 1, b: '##(bar)', c: '#(foo)'}", "{ a: 1, c: 2}");
        assign("a", "1");
        assign("b", "2");
        assign("myJson", "{ foo: '#(a + b)' }");
        matchEquals("myJson.foo", "3");
        assign("ticket", "{ ticket: 'my-ticket', userId: '12345' }");
        assign("myJson", "{ foo: '#(ticket.userId)' }");
        matchEquals("myJson", "{ foo: '12345' }");
        assign("foo", "{ a: null, b: null }");
        assign("bar", "{ hello: '#(foo.a)', world: '##(foo.b)'  }");
        matchEquals("bar", "{ hello: null }");
    }

    @Test
    void testEmbeddedXml() {
        assign("a", "1");
        assign("b", "2");
        assign("myXml", "<root><foo>#(a + b)</foo></root>");
        Variable value = engine.evalXmlPathOnVariableByName("myXml", "/root/foo");
        match(value.getValue(), "3"); // TODO BREAKING '3' before graal  
        assign("hello", "<hello>world</hello>");
        assign("myXml", "<foo><bar>#(hello)</bar></foo>");
        matchEquals("myXml", "<foo><bar><hello>world</hello></bar></foo>");
        assign("hello", "null");
        assign("myXml", "<foo><bar>#(hello)</bar></foo>");
        matchEquals("myXml", "<foo><bar></bar></foo>");
        matchEquals("myXml", "<foo><bar/></foo>");
        assign("a", "5");
        assign("myXml", "<foo bar=\"#(a)\">#(a)</foo>");
        matchEquals("myXml", "<foo bar=\"5\">5</foo>");
        assign("a", "null");
        assign("myXml", "<foo bar=\"##(a)\">baz</foo>");
        matchEquals("myXml", "<foo>baz</foo>");
        assign("myXml", "<foo><a>hello</a><b>##(a)</b></foo>");
        matchEquals("myXml", "<foo><a>hello</a></foo>");
    }

    @Test
    void testEvalXmlAndXpath() {
        assign("myXml", "<root><foo>bar</foo><hello>world</hello></root>");
        Variable myXml = engine.vars.get("myXml");
        assertTrue(myXml.isXml());
        Variable temp = engine.eval("myXml.root.foo");
        assertEquals("bar", temp.getValue());
        // xml with line breaks
        assign("foo", "<records>\n  <record>a</record>\n  <record>b</record>\n  <record>c</record>\n</records>");
        assign("bar", "foo.records");
        Variable bar = engine.vars.get("bar");
        assertTrue(bar.isMap());
        // match xml using json-path
        matchEquals("bar.record", "['a', 'b', 'c']");
        assertTrue(engine.assertTrue("foo.records.record.length == 3"));
        assign("myXml", "<cat><name>Billie</name><scores><score>2</score><score>5</score></scores></cat>");
        matchEquals("myXml/cat/scores/score[2]", "'5'");
        matchEquals("myXml.cat.scores.score[1]", "'5'");
        // xml with an empty tag, value should be null
        assign("foo", "<records>\n  <record>a</record>\n  <record/>\n</records>");
        assign("bar", "foo.records");
        matchEquals("bar.record", "['a', null]");
        assign("myXml", "<root><foo>bar</foo></root>");
        Variable value = engine.evalXmlPathOnVariableByName("myXml", "/root/foo");
        match(value.getValue(), "bar");
        value = engine.evalKarateExpression("$myXml/root/foo");
        match(value.getValue(), "bar");
        // present / notpresent
        assign("xml", "<root><foo>bar</foo><baz/><ban></ban></root>");
        matchEquals("xml/root/foo", "'bar'");
        matchEquals("xml/root/baz", "''");
        matchEquals("xml/root/ban", "''");
        matchEquals("xml/root/foo", "'#present'");
        matchNotEquals("xml/root/foo", "'#notpresent'");
        matchEquals("xml/root/nope", "'#notpresent'");
        matchNotEquals("xml/root/nope", "'#present'");
        matchEquals("xml/root/nope", "'##string'");
        // xml and assign
        assign("myXml", "<root><foo>bar</foo></root>");
        assign("myStr", "$myXml/root/foo");
        assertTrue(engine.assertTrue("myStr == 'bar'"));
        assign("myXml", "<root><foo><bar>baz</bar></foo></root>");
        assign("myNode", "$myXml/root/foo");
        assign("expected", "<foo><bar>baz</bar></foo>");
        matchEquals("myNode", "expected");
        assign("myXml", "<root><foo><bar>one</bar><bar>two</bar></foo></root>");
        // xpath return json array
        matchEquals("myXml/root/foo/bar", "['one', 'two']");
        assign("myJson", "[{ val: 'one' }, { val: 'two' }]");
        assign("myList", "get myJson $[*].val");
        assign("myXml", "<root><foo><bar>one</bar><bar>two</bar></foo></root>");
        matchEquals("myXml/root/foo/bar", "myList");
        assign("myXml", "<root><foo><bar>baz</bar></foo></root>");
        assign("myMap", "myXml");
        matchEquals("myMap/root/foo", "<foo><bar>baz</bar></foo>");
        assign("myXml", "<root><foo><bar>baz</bar></foo></root>");
        assign("myMap", "myXml");
        assign("temp", "get myXml /root/foo");
        matchEquals("temp", "<foo><bar>baz</bar></foo>");
        assign("temp", "get myMap /root/foo");
        matchEquals("temp", "<foo><bar>baz</bar></foo>");
    }

    @Test
    void testEvalJsonAndJsonPath() {
        assign("myJson", "{ foo: 'bar', baz: [1, 2], ban: { hello: 'world' } }");
        Variable myXml = engine.vars.get("myJson");
        assertTrue(myXml.isMap());
        Variable value = engine.eval("myJson.foo");
        assertEquals("bar", value.getValue());
        value = engine.eval("myJson.baz[1]");
        assertEquals(2, value.<Number>getValue());
        value = engine.eval("myJson.ban.hello");
        assertEquals("world", value.getValue());
        // json-path
        value = engine.evalJsonPathOnVariableByName("myJson", "$.baz[1]");
        assertEquals(2, value.<Number>getValue());
        value = engine.evalJsonPathOnVariableByName("myJson", "$.baz");
        assertTrue(value.isList());
        match(value.getValue(), "[1, 2]");
        value = engine.evalJsonPathOnVariableByName("myJson", "$.ban");
        assertTrue(value.isMap());
        match(value.getValue(), "{ hello: 'world' }");
        value = engine.evalKarateExpression("$myJson.ban");
        match(value.getValue(), "{ hello: 'world' }");
        // tricky json-path
        assign("foo", "{ a: 1, b: 2, c: 3 }");
        assign("bar", "{ 'sp ace': '#(foo.a)', 'hy-phen': '#(foo.b)', 'full.stop': '#(foo.c)' }");
        matchEquals("bar", "{ 'sp ace': 1, 'hy-phen': 2, 'full.stop': 3 }");
        // json-path on LHS
        String json = "[\n"
                + "    {\n"
                + "        \"a\": \"a\",\n"
                + "        \"b\": \"a\",\n"
                + "        \"c\": \"a\",\n"
                + "    },\n"
                + "    {\n"
                + "        \"a\": \"ab\",\n"
                + "        \"b\": \"ab\",\n"
                + "        \"c\": \"ab\",\n"
                + "    }\n"
                + "]";
        assign("response", json);
        matchEquals("response[?(@.b=='ab')]", "'#[1]'");
        assign("json", "{ foo: 'bar' }");
        matchEquals("json.foo", "'bar'");
        matchEquals("json.foo", "'#present'");
        matchNotEquals("json.foo", "'#notpresent'");
        matchEquals("json.nope", "'#notpresent'");
        matchEquals("json.foo", "'#ignore'");
        matchEquals("json.nope", "'#ignore'");
        matchEquals("json.foo", "'#string'");
        matchEquals("json.foo", "'##string'");
        matchNotEquals("json.nope", "'#string'");
        matchEquals("json.nope", "'##string'");
    }

    @Test
    void testMatchObjectsReturnedFromJs() {
        assign("fun", "function(){ return { foo: 'bar' } }");
        assign("json", "{ foo: 'bar' }");
        assign("expected", "fun()");
        matchEquals("json", "fun()");
        matchEquals("expected", "{ foo: 'bar' }");
        assign("fun", "function(){ return [1, 2] }");
        assign("json", "[1, 2]");
        assign("expected", "fun()");
        matchEquals("json", "fun()");
        matchEquals("expected", "[1, 2]");
    }

    @Test
    void testTypeConversion() {
        engine.assign(AssignType.STRING, "myStr", "{ foo: { hello: 'world' } }", false);
        Variable value = engine.vars.get("myStr");
        assertTrue(value.isString());
        // auto converts string to json before json-path
        assign("foo", "$myStr.foo");
        matchEquals("foo", "{ hello: 'world' }");
        // json to string
        engine.assign(AssignType.STRING, "myStr", "{ root: { foo: 'bar' } }", false);
        matchEquals("myStr", "'{\"root\":{\"foo\":\"bar\"}}'");
        // string to json
        assign("myStr", "'{\"root\":{\"foo\":\"bar\"}}'");
        engine.assign(AssignType.JSON, "myJson", "myStr", false);
        value = engine.vars.get("myJson");
        assertTrue(value.isMap());
        matchEquals("myJson", "{ root: { foo: 'bar' } }");
        // json to xml
        engine.assign(AssignType.XML, "myXml", "{ root: { foo: 'bar' } }", false);
        value = engine.vars.get("myXml");
        assertTrue(value.isXml());
        matchEquals("myXml", "<root><foo>bar</foo></root>");
        // string to xml
        assign("myStr", "'<root><foo>bar</foo></root>'");
        engine.assign(AssignType.XML, "myXml", "myStr", false);
        matchEquals("myXml", "<root><foo>bar</foo></root>");
        // xml to string
        engine.assign(AssignType.STRING, "myStr", "<root><foo>bar</foo></root>", false);
        matchEquals("myStr", "'<root><foo>bar</foo></root>'");
        // xml attributes get re-ordered
        engine.assign(AssignType.STRING, "myStr", "<foo><bar bbb=\"2\" aaa=\"1\"/></foo>", false);
        matchEquals("myStr", "'<foo><bar aaa=\"1\" bbb=\"2\"/></foo>'");
        // pojo to json
        assign("myPojo", "new com.intuit.karate.runtime.SimplePojo()");
        value = engine.vars.get("myPojo");
        assertTrue(value.isOther());
        engine.assign(AssignType.JSON, "myJson", "myPojo", false);
        matchEquals("myJson", "{ foo: null, bar: 0 }");
        // pojo to xml
        engine.assign(AssignType.XML, "myXml", "myPojo", false);
        matchEquals("myXml", "<root><foo></foo><bar>0</bar></root>");
    }

    @Test
    void testResponseShortCuts() {
        assign("response", "{ foo: 'bar' }");
        matchEquals("response", "{ foo: 'bar' }");
        matchEquals("$", "{ foo: 'bar' }");
        matchEquals("response.foo", "'bar'");
        matchEquals("$.foo", "'bar'");
        assign("response", "<root><foo>bar</foo></root>");
        matchEquals("response", "<root><foo>bar</foo></root>");
        matchEquals("/", "<root><foo>bar</foo></root>");
        matchEquals("response/", "<root><foo>bar</foo></root>");
        matchEquals("response /", "<root><foo>bar</foo></root>");
    }

    @Test
    void testSetAndRemove() {
        assign("test", "{ foo: 'bar' }");
        engine.set("test", "$.bar", "'baz'");
        matchEquals("test", "{ foo: 'bar', bar: 'baz' }");
        engine.set("test", "$.foo", "null");
        matchEquals("test", "{ foo: null, bar: 'baz' }");
        engine.remove("test", "$.foo");
        matchEquals("test", "{ bar: 'baz' }");
        assign("test", "<root><foo>bar</foo></root>");
        engine.set("test", "/root/baz", "'ban'");
        matchEquals("test", "<root><foo>bar</foo><baz>ban</baz></root>");
        engine.remove("test", "/root/foo");
        matchEquals("test", "<root><baz>ban</baz></root>");
    }

    @Test
    void testSetViaTable() {
        Json json = new Json("[{path: 'bar', value: \"'baz'\" }]");
        engine.set("foo", null, json.asList());
        matchEquals("foo", "{ bar: 'baz' }");
        json = new Json("[{path: 'bar', value: 'null' }]"); // has no effect
        engine.set("foo", null, json.asList());
        matchEquals("foo", "{ bar: 'baz' }");
        json = new Json("[{path: 'bar', value: '(null)' }]"); // has effect
        engine.set("foo", null, json.asList());
        matchEquals("foo", "{ bar: null }");
    }

}
