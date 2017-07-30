package com.intuit.karate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.*;
import org.w3c.dom.Document;

/**
 *
 * @author pthomas3
 */
public class ScriptTest {

    private static final Logger logger = LoggerFactory.getLogger(ScriptTest.class);

    private ScriptContext getContext() {
        String featureDir = FileUtils.getDirContaining(getClass()).getPath();
        ScriptEnv env = ScriptEnv.init("dev", new File(featureDir));
        return new ScriptContext(env, null, null);
    }

    private AssertionResult matchJsonObject(Object act, Object exp, ScriptContext context) {
        return Script.matchNestedObject('.', "$", MatchType.EQUALS, null, act, exp, context);
    }

    @Test
    public void testParsingTextType() {
        assertTrue(Script.isVariableAndJsonPath("foo.bar"));
        assertFalse(Script.isVariableAndJsonPath("foo.bar()"));
        assertFalse(Script.isVariableAndXmlPath("foo.bar"));
        assertTrue(Script.isVariableAndXmlPath("foo/bar"));
        assertFalse(Script.isVariableAndJsonPath("foo/bar"));
        assertTrue(Script.isVariableAndXmlPath("foo/"));
        assertFalse(Script.isVariableAndXmlPath("foo"));
        assertTrue(Script.isVariable("foo"));
        assertFalse(Script.isVariableAndJsonPath("foo"));
        assertFalse(Script.isVariableAndXmlPath("foo"));
        assertTrue(Script.isJavaScriptFunction("function(){ return { bar: 'baz' } }"));
        assertFalse(Script.isVariableAndXmlPath("read('../syntax/for-demos.js')"));
        assertTrue(Script.isXmlPath("/foo"));
        assertTrue(Script.isXmlPath("//foo"));
        assertTrue(Script.isXmlPathFunction("lower-case('Foo')"));
        assertTrue(Script.isXmlPathFunction("count(/journal/article)"));
        assertTrue(Script.isVariableAndSpaceAndPath("foo count(/journal/article)"));
        assertTrue(Script.isVariableAndSpaceAndPath("foo $"));
    }

    @Test
    public void testEvalPrimitives() {
        ScriptContext ctx = getContext();
        ctx.vars.put("foo", "bar");
        ctx.vars.put("a", 1);
        ctx.vars.put("b", 2);
        String expression = "foo + 'baz'";
        ScriptValue value = Script.evalInNashorn(expression, ctx);
        assertEquals(ScriptValue.Type.STRING, value.getType());
        assertEquals("barbaz", value.getValue());
        value = Script.evalInNashorn("a + b", ctx);
        assertEquals(ScriptValue.Type.PRIMITIVE, value.getType());
        assertEquals(3.0, value.getValue());
    }

    @Test
    public void testEvalMapsAndLists() {
        ScriptContext ctx = getContext();
        Map<String, Object> testMap = new HashMap<>();
        testMap.put("foo", "bar");
        testMap.put("baz", 5);
        List<Integer> testList = new ArrayList<>();
        testList.add(1);
        testList.add(2);
        testMap.put("myList", testList);
        ctx.vars.put("myMap", testMap);
        String expression = "myMap.foo + myMap.baz";
        ScriptValue value = Script.evalInNashorn(expression, ctx);
        assertEquals(ScriptValue.Type.STRING, value.getType());
        assertEquals("bar5", value.getValue());
        value = Script.evalInNashorn("myMap.myList[0] + myMap.myList[1]", ctx);
        assertEquals(ScriptValue.Type.PRIMITIVE, value.getType());
        assertEquals(3.0, value.getValue());
    }

    @Test
    public void testEvalJsonDocuments() {
        ScriptContext ctx = getContext();
        DocumentContext doc = JsonUtils.toJsonDoc("{ foo: 'bar', baz: [1, 2], ban: { hello: 'world' } }");
        ctx.vars.put("myJson", doc);
        ScriptValue value = Script.evalInNashorn("myJson.foo", ctx);
        assertEquals("bar", value.getValue());
        value = Script.evalInNashorn("myJson.baz[1]", ctx);
        assertEquals(2, value.getValue());
        value = Script.evalInNashorn("myJson.ban.hello", ctx);
        assertEquals("world", value.getValue());
    }

    @Test
    public void testEvalXmlDocuments() {
        ScriptContext ctx = getContext();
        Document doc = XmlUtils.toXmlDoc("<root><foo>bar</foo><hello>world</hello></root>");
        ctx.vars.put("myXml", doc);
        ScriptValue value = Script.evalInNashorn("myXml.root.foo", ctx);
        assertEquals("bar", value.getValue());
    }

    @Test
    public void testAssignXmlWithLineBreaksAndMatchJson() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "<records>\n  <record>a</record>\n  <record>b</record>\n  <record>c</record>\n</records>", ctx);
        Script.assign("bar", "foo.records", ctx);
        ScriptValue value = ctx.vars.get("bar");
        assertTrue(value.getType() == ScriptValue.Type.MAP);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "bar.record", null, "['a', 'b', 'c']", ctx).pass);
        assertTrue(Script.assertBoolean("foo.records.record.length == 3", ctx).pass);
    }

    @Test
    public void testAssignXmlWithLineBreaksAndNullElements() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "<records>\n  <record>a</record>\n  <record/>\n</records>", ctx);
        Script.assign("bar", "foo.records", ctx);
        ScriptValue value = ctx.vars.get("bar");
        assertTrue(value.getType() == ScriptValue.Type.MAP);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "bar.record", null, "['a', null]", ctx).pass);
    }

    @Test
    public void testJsonPathOnVarsByName() {
        ScriptContext ctx = getContext();
        DocumentContext doc = JsonUtils.toJsonDoc("{ foo: 'bar', baz: [1, 2], ban: { hello: 'world' } }");
        ctx.vars.put("myJson", doc);
        ScriptValue value = Script.evalJsonPathOnVarByName("myJson", "$.foo", ctx);
        assertEquals("bar", value.getValue());
        value = Script.eval("myJson.foo", ctx);
        assertEquals("bar", value.getValue());
        value = Script.evalJsonPathOnVarByName("myJson", "$.baz[1]", ctx);
        assertEquals(2, value.getValue());
        value = Script.eval("myJson.baz[1]", ctx);
        assertEquals(2, value.getValue());
        value = Script.evalJsonPathOnVarByName("myJson", "$.baz", ctx);
        assertEquals(ScriptValue.Type.LIST, value.getType());
        value = Script.evalJsonPathOnVarByName("myJson", "$.ban", ctx);
        assertEquals(ScriptValue.Type.MAP, value.getType());
    }

    @Test
    public void testXmlPathOnVarsByName() {
        ScriptContext ctx = getContext();
        Document doc = XmlUtils.toXmlDoc("<root><foo>bar</foo></root>");
        ctx.vars.put("myXml", doc);
        ScriptValue value = Script.evalXmlPathOnVarByName("myXml", "/root/foo", ctx);
        assertEquals(ScriptValue.Type.STRING, value.getType());
        assertEquals("bar", value.getAsString());
        value = Script.eval("myXml/root/foo", ctx);
        assertEquals("bar", value.getAsString());
    }

    @Test
    public void testEvalXmlEmbeddedExpressions() {
        ScriptContext ctx = getContext();
        ctx.vars.put("a", 1);
        ctx.vars.put("b", 2);
        Document doc = XmlUtils.toXmlDoc("<root><foo>#(a + b)</foo></root>");
        Script.evalXmlEmbeddedExpressions(doc, ctx);
        ctx.vars.put("myXml", doc);
        ScriptValue value = Script.evalXmlPathOnVarByName("myXml", "/root/foo", ctx);
        assertEquals(ScriptValue.Type.STRING, value.getType());
        assertEquals("3.0", value.getAsString());
    }
    
    @Test
    public void testEvalXmlEmbeddedExpressionsThatReturnChunks() {
        ScriptContext ctx = getContext();
        Script.assign("hello", "<hello>world</hello>", ctx);
        Script.assign("xml", "<foo><bar>#(hello)</bar></foo>", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "xml", null, "<foo><bar><hello>world</hello></bar></foo>", ctx).pass);
    }

    @Test
    public void testEvalXmlEmbeddedExpressionsThatReturnNull() {
        ScriptContext ctx = getContext();
        Script.assign("hello", "null", ctx);
        Script.assign("xml", "<foo><bar>#(hello)</bar></foo>", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "xml", null, "<foo><bar></bar></foo>", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "xml", null, "<foo><bar/></foo>", ctx).pass);
    }   
    
    @Test
    public void testEvalXmlEmbeddedExpressionsInAttributes() {
        ScriptContext ctx = getContext();
        ctx.vars.put("a", 5);
        String xml = "<foo bar=\"#(a)\">#(a)</foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        Script.evalXmlEmbeddedExpressions(doc, ctx);
        String result = XmlUtils.toString(doc);
        logger.debug("result: {}", result);
        assertTrue(result.endsWith("<foo bar=\"5\">5</foo>"));
    }
    
    @Test
    public void testEvalXmlEmbeddedOptionalExpressionsInAttributes() {
        ScriptContext ctx = getContext();
        Script.assign("a", "null", ctx);
        Script.assign("xml", "<foo bar=\"##(a)\">baz</foo>", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "xml", null, "<foo>baz</foo>", ctx).pass);
    }

    @Test
    public void testEvalXmlEmbeddedOptionalExpressions() {
        ScriptContext ctx = getContext();
        Script.assign("a", "null", ctx);
        Script.assign("xml", "<foo><a>hello</a><b>##(a)</b></foo>", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "xml", null, "<foo><a>hello</a></foo>", ctx).pass);
    }   

    @Test
    public void testEvalJsonEmbeddedExpressions() {
        ScriptContext ctx = getContext();
        ctx.vars.put("a", 1);
        ctx.vars.put("b", 2);
        DocumentContext doc = JsonUtils.toJsonDoc("{ foo: '#(a + b)' }");
        Script.evalJsonEmbeddedExpressions(doc, ctx);
        ctx.vars.put("myJson", doc);
        ScriptValue value = Script.evalJsonPathOnVarByName("myJson", "$.foo", ctx);
        assertEquals(ScriptValue.Type.PRIMITIVE, value.getType());
        assertEquals(3.0, value.getValue());
    }

    @Test
    public void testEvalEmbeddedExpressionsWithJsonPath() {
        ScriptContext ctx = getContext();
        String ticket = "{ ticket: 'my-ticket', userId: '12345' }";
        ctx.vars.put("ticket", JsonUtils.toJsonDoc(ticket));
        String json = "{ foo: '#(ticket.userId)' }";
        DocumentContext doc = JsonUtils.toJsonDoc(json);
        Script.evalJsonEmbeddedExpressions(doc, ctx);
        String result = doc.jsonString();
        logger.debug("result: {}", result);
        assertEquals("{\"foo\":\"12345\"}", result);
    }
    
    @Test
    public void testEvalEmbeddedExpressionsWithJsonPathsWhichAreTricky() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "{ a: 1, b: 2, c: 3 }", ctx);
        Script.assign("bar", "{ 'sp ace': '#(foo.a)', 'hy-phen': '#(foo.b)', 'full.stop': '#(foo.c)' }", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "bar", null, "{ 'sp ace': 1, 'hy-phen': 2, 'full.stop': 3 }", ctx).pass);
    }

    @Test
    public void testEvalEmbeddedOptionalExpressions() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "{ a: null, b: null }", ctx);
        Script.assign("bar", "{ hello: '#(foo.a)', world: '##(foo.b)'  }", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "bar", null, "{ hello: null }", ctx).pass);
    }

    @Test
    public void testVariableNameValidation() {
        assertTrue(Script.isValidVariableName("foo"));
        assertTrue(Script.isValidVariableName("foo_bar"));
        assertTrue(Script.isValidVariableName("foo_"));
        assertTrue(Script.isValidVariableName("foo1"));
        assertTrue(Script.isValidVariableName("a"));
        assertTrue(Script.isValidVariableName("a1"));
        // bad
        assertFalse(Script.isValidVariableName("foo.bar"));
        assertFalse(Script.isValidVariableName("foo-bar"));
        assertFalse(Script.isValidVariableName("$foo"));
        assertFalse(Script.isValidVariableName("$foo/bar"));
        assertFalse(Script.isValidVariableName("_foo"));
        assertFalse(Script.isValidVariableName("_foo_"));
        assertFalse(Script.isValidVariableName("0"));
        assertFalse(Script.isValidVariableName("2foo"));
    }

    @Test
    public void testMatchMapObjects() {
        ScriptContext ctx = getContext();
        Map<String, Object> left = new HashMap<>();
        left.put("foo", "bar");
        Map<String, Object> right = new HashMap<>();
        right.put("foo", "bar");
        assertTrue(matchJsonObject(left, right, ctx).pass);
        right.put("baz", "#ignore");
        assertTrue(matchJsonObject(left, right, ctx).pass);
        left.put("baz", Arrays.asList(1, 2, 3));
        right.put("baz", Arrays.asList(1, 2, 3));
        assertTrue(matchJsonObject(left, right, ctx).pass);
        left.put("baz", Arrays.asList(1, 2));
        assertFalse(matchJsonObject(left, right, ctx).pass);
        Map<String, Object> leftChild = new HashMap<>();
        leftChild.put("a", 1);
        Map<String, Object> rightChild = new HashMap<>();
        rightChild.put("a", 1);
        left.put("baz", leftChild);
        right.put("baz", rightChild);
        assertTrue(matchJsonObject(left, right, ctx).pass);
        List<Map> leftList = new ArrayList<>();
        leftList.add(leftChild);
        List<Map> rightList = new ArrayList<>();
        rightList.add(rightChild);
        left.put("baz", leftList);
        right.put("baz", rightList);
        assertTrue(matchJsonObject(left, right, ctx).pass);
        rightChild.put("a", 2);
        assertFalse(matchJsonObject(left, right, ctx).pass);
        rightChild.put("a", "#ignore");
        assertTrue(matchJsonObject(left, right, ctx).pass);
    }

    @Test
    public void testMatchListObjects() {
        List left = new ArrayList();
        List right = new ArrayList();
        Map<String, Object> leftChild = new HashMap<>();
        leftChild.put("a", 1);
        left.add(leftChild);
        Map<String, Object> rightChild = new HashMap<>();
        rightChild.put("a", 1);
        right.add(rightChild);
        assertTrue(matchJsonObject(left, right, null).pass);
    }

    @Test
    public void testMatchJsonPath() {
        DocumentContext doc = JsonPath.parse("{ foo: 'bar', baz: { ban: [1, 2, 3]} }");
        ScriptContext ctx = getContext();
        ctx.vars.put("myJson", doc);
        ScriptValue myJson = ctx.vars.get("myJson");
        assertTrue(Script.matchJsonPath(MatchType.EQUALS, myJson, "$.foo", "'bar'", ctx).pass);
        assertTrue(Script.matchJsonPath(MatchType.EQUALS, myJson, "$.baz", "{ ban: [1, 2, 3]} }", ctx).pass);
        assertTrue(Script.matchJsonPath(MatchType.EQUALS, myJson, "$.baz.ban[1]", "2", ctx).pass);
        assertTrue(Script.matchJsonPath(MatchType.EQUALS, myJson, "$.baz", "{ ban: [1, '#ignore', 3]} }", ctx).pass);
    }

    @Test
    public void testMatchJsonPathThatReturnsList() {
        DocumentContext doc = JsonPath.parse("{ foo: [{ bar: 1}, {bar: 2}, {bar: 3}]}");
        ScriptContext ctx = getContext();
        ctx.vars.put("json", doc);
        Script.assign("list", "json.foo", ctx);
        ScriptValue list = ctx.vars.get("list");
        assertTrue(Script.matchJsonPath(MatchType.EQUALS, list, "$[0]", "{ bar: 1}", ctx).pass);
        assertTrue(Script.matchJsonPath(MatchType.EQUALS, list, "$[0].bar", "1", ctx).pass);
    }

    @Test
    public void testMatchAllJsonPath() {
        DocumentContext doc = JsonPath.parse("{ foo: [{bar: 1, baz: 'a'}, {bar: 2, baz: 'b'}, {bar:3, baz: 'c'}]}");
        ScriptContext ctx = getContext();
        ctx.vars.put("myJson", doc);
        ScriptValue myJson = ctx.vars.get("myJson");
        assertTrue(Script.matchJsonPath(MatchType.EQUALS, myJson, "$.foo", "[{bar: 1, baz: 'a'}, {bar: 2, baz: 'b'}, {bar:3, baz: 'c'}]", ctx).pass);
        assertTrue(Script.matchJsonPath(MatchType.CONTAINS, myJson, "$.foo", "[{bar: 1, baz: 'a'}, {bar: 2, baz: 'b'}, {bar:3, baz: 'c'}]", ctx).pass);
        assertFalse(Script.matchJsonPath(MatchType.NOT_CONTAINS, myJson, "$.foo", "[{bar: 1, baz: 'a'}]", ctx).pass);
        assertTrue(Script.matchJsonPath(MatchType.NOT_CONTAINS, myJson, "$.foo", "[{bar: 9, baz: 'z'}, {bar: 99, baz: 'zz'}]", ctx).pass);
        assertTrue(Script.matchJsonPath(MatchType.CONTAINS_ONLY, myJson, "$.foo", "[{bar: 1, baz: 'a'}, {bar: 2, baz: 'b'}, {bar:3, baz: 'c'}]", ctx).pass);
        // shuffle
        assertTrue(Script.matchJsonPath(MatchType.CONTAINS_ONLY, myJson, "$.foo", "[{bar: 2, baz: 'b'}, {bar:3, baz: 'c'}, {bar: 1, baz: 'a'}]", ctx).pass);
        assertFalse(Script.matchJsonPath(MatchType.CONTAINS_ONLY, myJson, "$.foo", "[{bar: 1, baz: 'a'}, {bar: 2, baz: 'b'}]", ctx).pass);
        assertTrue(Script.matchJsonPath(MatchType.EACH_EQUALS, myJson, "$.foo", "{bar:'#number', baz:'#string'}", ctx).pass);
        assertTrue(Script.matchJsonPath(MatchType.EACH_CONTAINS, myJson, "$.foo", "{bar:'#number'}", ctx).pass);
        assertTrue(Script.matchJsonPath(MatchType.EACH_CONTAINS, myJson, "$.foo", "{baz:'#string'}", ctx).pass);
        assertTrue(Script.matchJsonPath(MatchType.EACH_NOT_CONTAINS, myJson, "$.foo", "{baz:'z'}", ctx).pass);
        assertFalse(Script.matchJsonPath(MatchType.EACH_NOT_CONTAINS, myJson, "$.foo", "{baz:'a'}", ctx).pass);
        assertFalse(Script.matchJsonPath(MatchType.EACH_EQUALS, myJson, "$.foo", "{bar:'#? _ < 3',  baz:'#string'}", ctx).pass);
    }

    @Test
    public void testMatchJsonObjectReturnedFromJs() {
        ScriptContext ctx = getContext();
        Script.assign("fun", "function(){ return { foo: 'bar' } }", ctx);
        Script.assign("json", "{ foo: 'bar' }", ctx);
        Script.assign("expected", "fun()", ctx);
        assertTrue(Script.matchNamed("json", null, "expected", ctx).pass);
        assertTrue(Script.matchNamed("json", null, "fun()", ctx).pass);
    }

    @Test
    public void testMatchJsonArrayReturnedFromJs() {
        ScriptContext ctx = getContext();
        Script.assign("fun", "function(){ return [ 'foo', 'bar', 'baz' ] }", ctx);
        Script.assign("json", "[ 'foo', 'bar', 'baz' ]", ctx);
        Script.assign("expected", "fun()", ctx);
        assertTrue(Script.matchNamed("json", null, "expected", ctx).pass);
        assertTrue(Script.matchNamed("json", null, "fun()", ctx).pass);
    }

    @Test
    public void testMatchJsonPathOnResponse() {
        DocumentContext doc = JsonPath.parse("{ foo: 'bar' }");
        ScriptContext ctx = getContext();
        ctx.vars.put("response", doc);
        assertTrue(Script.matchNamed("$", null, "{ foo: 'bar' }", ctx).pass);
        assertTrue(Script.matchNamed("$.foo", null, "'bar'", ctx).pass);
    }

    private final String ACTUAL = "{\"id\":{\"domain\":\"ACS\",\"type\":\"entityId\",\"value\":\"bef90f66-bb57-4fea-83aa-a0acc42b0426\"},\"primaryId\":\"bef90f66-bb57-4fea-83aa-a0acc42b0426\",\"created\":{\"on\":\"2016-02-28T05:56:48.485+0000\"},\"lastUpdated\":{\"on\":\"2016-02-28T05:56:49.038+0000\"},\"organization\":{\"id\":{\"domain\":\"ACS\",\"type\":\"entityId\",\"value\":\"631fafe9-8822-4c82-b4a4-8735b202c16c\"},\"created\":{\"on\":\"2016-02-28T05:56:48.486+0000\"},\"lastUpdated\":{\"on\":\"2016-02-28T05:56:49.038+0000\"}},\"clientState\":\"ACTIVE\"}";
    private final String EXPECTED = "{\"id\":{\"domain\":\"ACS\",\"type\":\"entityId\",\"value\":\"#ignore\"},\"primaryId\":\"#ignore\",\"created\":{\"on\":\"#ignore\"},\"lastUpdated\":{\"on\":\"#ignore\"},\"organization\":{\"id\":{\"domain\":\"ACS\",\"type\":\"entityId\",\"value\":\"#ignore\"},\"created\":{\"on\":\"#ignore\"},\"lastUpdated\":{\"on\":\"#ignore\"}},\"clientState\":\"ACTIVE\"}";

    @Test
    public void testMatchTwoJsonDocsWithIgnores() {
        DocumentContext actual = JsonPath.parse(ACTUAL);
        DocumentContext expected = JsonPath.parse(EXPECTED);
        ScriptContext ctx = getContext();
        ctx.vars.put("actual", actual);
        ctx.vars.put("expected", expected);
        ScriptValue act = ctx.vars.get("actual");
        assertTrue(Script.matchJsonPath(MatchType.EQUALS, act, "$", "expected", ctx).pass);
    }

    @Test
    public void testMatchXmlPathThatReturnsTextNode() {
        ScriptContext ctx = getContext();
        Document doc = XmlUtils.toXmlDoc("<root><foo>bar</foo><hello>world</hello></root>");
        ctx.vars.put("myXml", doc);
        ScriptValue myXml = ctx.vars.get("myXml");
        assertTrue(Script.matchXmlPath(MatchType.EQUALS, myXml, "/root/foo", "'bar'", ctx).pass);
        assertTrue(Script.matchXmlPath(MatchType.EQUALS, myXml, "/root/hello", "'world'", ctx).pass);
    }

    @Test
    public void testMatchXmlPathThatReturnsXmlChunk() {
        ScriptContext ctx = getContext();
        Document doc = XmlUtils.toXmlDoc("<root><foo><bar>baz</bar></foo></root>");
        ctx.vars.put("myXml", doc);
        ScriptValue myXml = ctx.vars.get("myXml");
        assertTrue(Script.matchXmlPath(MatchType.EQUALS, myXml, "/root/foo", "<foo><bar>baz</bar></foo>", ctx).pass);
    }

    @Test
    public void testAssignAndMatchXmlText() {
        ScriptContext ctx = getContext();
        Script.assign("myXml", "<root><foo>bar</foo></root>", ctx);
        Script.assign("myStr", "myXml/root/foo", ctx);
        assertTrue(Script.assertBoolean("myStr == 'bar'", ctx).pass);
    }

    @Test
    public void testAssignAndMatchXmlChunk() {
        ScriptContext ctx = getContext();
        Script.assign("myXml", "<root><foo><bar>baz</bar></foo></root>", ctx);
        Script.assign("myChunk", "myXml/root/foo", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "myChunk", null, "<foo><bar>baz</bar></foo>", ctx).pass);
    }

    @Test
    public void testAssignAndMatchXmlChunkByVariableReference() {
        ScriptContext ctx = getContext();
        Script.assign("myXml", "<root><foo><bar>baz</bar></foo></root>", ctx);
        Script.assign("myChunk", "myXml/root/foo", ctx);
        Script.assign("expected", "<foo><bar>baz</bar></foo>", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "myChunk", null, "expected", ctx).pass);
    }

    @Test
    public void testAssignAndMatchXmlPathChunk() {
        ScriptContext ctx = getContext();
        Script.assign("myXml", "<root><foo><bar>baz</bar></foo></root>", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "myXml/root/foo", null, "<foo><bar>baz</bar></foo>", ctx).pass);
    }

    @Test
    public void testAssignAndMatchXmlPathThatReturnsNodeListAgainstJsonArray() {
        ScriptContext ctx = getContext();
        Script.assign("myXml", "<root><foo><bar>one</bar><bar>two</bar></foo></root>", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "myXml/root/foo/bar", null, "['one', 'two']", ctx).pass);
    }

    @Test
    public void testAssignAndMatchXmlPathThatReturnsNodeListAgainstList() {
        ScriptContext ctx = getContext();
        Script.assign("myJson", "[{ val: 'one' }, { val: 'two' }]", ctx);
        Script.assign("myList", "get myJson $[*].val", ctx);
        Script.assign("myXml", "<root><foo><bar>one</bar><bar>two</bar></foo></root>", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "myXml/root/foo/bar", null, "myList", ctx).pass);
    }

    @Test
    public void testMatchXmlPathAutoConvertingFromMap() {
        ScriptContext ctx = getContext();
        Script.assign("myXml", "<root><foo><bar>baz</bar></foo></root>", ctx);
        Script.assign("myMap", "myXml", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "myMap/root/foo", null, "<foo><bar>baz</bar></foo>", ctx).pass);
    }

    @Test
    public void testEvalXmlPathAutoConvertingFromMap() {
        ScriptContext ctx = getContext();
        Script.assign("myXml", "<root><foo><bar>baz</bar></foo></root>", ctx);
        Script.assign("myMap", "myXml", ctx);
        Script.assign("temp", "get myXml /root/foo", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "temp", null, "<foo><bar>baz</bar></foo>", ctx).pass);
        Script.assign("temp", "get myMap /root/foo", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "temp", null, "<foo><bar>baz</bar></foo>", ctx).pass);
    }

    @Test
    public void testAssignXmlPathThatReturnsListThenMatch() {
        ScriptContext ctx = getContext();
        Script.assign("response", XmlUtilsTest.TEACHERS_XML, ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "//teacher[@department='science']/subject", null, "['math', 'physics']", ctx).pass);
        Script.assign("subjects", "//teacher[@department='science']/subject", ctx);
        assertTrue(Script.matchNamed(MatchType.CONTAINS, "subjects", null, "['physics', 'math']", ctx).pass);
        Script.assign("teachers", "response", ctx); // becomes a map
        Script.assign("subjects", "get teachers //teacher[@department='science']/subject", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "subjects", null, "['math', 'physics']", ctx).pass);
    }
    
    @Test
    public void testRunningJsonPathOnStringAutoConvertsStringToJson() {
        ScriptContext ctx = getContext();
        Script.assignString("response", "{ foo: { hello: 'world' } }", ctx);
        Script.assign("foo", "response.foo", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ hello: 'world' }", ctx).pass);
    }

    @Test
    public void testCastJsonToString() {
        ScriptContext ctx = getContext();
        Script.assign("myJson", "{ root: { foo: 'bar' } }", ctx);
        Script.assignString("myString", "myJson", ctx);
        ScriptValue value = ctx.vars.get("myString");
        assertEquals(ScriptValue.Type.STRING, value.getType());
        assertEquals("{\"root\":{\"foo\":\"bar\"}}", value.getAsString());
    }

    @Test
    public void testCastStringToJson() {
        ScriptContext ctx = getContext();
        Script.assign("myString", "{\"root\":{\"foo\":\"bar\"}}", ctx);
        Script.assignJson("myJson", "myString", ctx);
        ScriptValue value = ctx.vars.get("myJson");
        assertEquals(ScriptValue.Type.JSON, value.getType());
        assertEquals("{\"root\":{\"foo\":\"bar\"}}", value.getAsString());
    }

    @Test
    public void testCastJsonToXml() {
        ScriptContext ctx = getContext();
        Script.assign("myJson", "{ root: { foo: 'bar' } }", ctx);
        Script.assignXml("myXml", "myJson", ctx);
        ScriptValue value = ctx.vars.get("myXml");
        assertEquals(ScriptValue.Type.XML, value.getType());
        assertEquals("<root><foo>bar</foo></root>", value.getAsString());
    }

    @Test
    public void testCastStringToXml() {
        ScriptContext ctx = getContext();
        Script.assignString("myString", "<root><foo>bar</foo></root>", ctx);
        Script.assignXml("myXml", "myString", ctx);
        ScriptValue value = ctx.vars.get("myXml");
        assertEquals(ScriptValue.Type.XML, value.getType());
        assertEquals("<root><foo>bar</foo></root>", value.getAsString());
    }

    @Test
    public void testCastXmlToString() {
        ScriptContext ctx = getContext();
        Script.assign("myXml", "<root><foo>bar</foo></root>", ctx);
        Script.assignXmlString("myString", "myXml", ctx);
        ScriptValue value = ctx.vars.get("myString");
        assertEquals(ScriptValue.Type.STRING, value.getType());
        assertEquals("<root><foo>bar</foo></root>", value.getValue());
    }
    
    @Test
    public void testCastPojoToJson() {
        ScriptContext ctx = getContext();
        Script.assign("pojo", "new com.intuit.karate.SimplePojo()", ctx);
        Script.assignJson("json", "pojo", ctx);
        assertTrue(Script.matchNamed("json", null, "{ foo: null, bar: 0 }", ctx).pass);
    }
    
    @Test
    public void testCastPojoToXml() {
        ScriptContext ctx = getContext();
        Script.assign("pojo", "new com.intuit.karate.SimplePojo()", ctx);
        Script.assignXml("xml", "pojo", ctx);
        assertTrue(Script.matchNamed("xml", null, "<root><foo></foo><bar>0</bar></root>", ctx).pass);
    }    

    @Test
    public void testXmlShortCutsForResponse() {
        ScriptContext ctx = getContext();
        Script.assign("response", "<root><foo>bar</foo></root>", ctx);
        assertTrue(Script.matchNamed("response", "/", "<root><foo>bar</foo></root>", ctx).pass);
        assertTrue(Script.matchNamed("response/", null, "<root><foo>bar</foo></root>", ctx).pass);
        assertTrue(Script.matchNamed("response", null, "<root><foo>bar</foo></root>", ctx).pass);
        assertTrue(Script.matchNamed("/", null, "<root><foo>bar</foo></root>", ctx).pass);
    }

    @Test
    public void testMatchXmlButUsingJsonPath() {
        ScriptContext ctx = getContext();
        Document doc = XmlUtils.toXmlDoc("<cat><name>Billie</name><scores><score>2</score><score>5</score></scores></cat>");
        ctx.vars.put("myXml", doc);
        assertTrue(Script.matchNamed("myXml/cat/scores/score[2]", null, "'5'", ctx).pass);
        // lenient primitive equality check
        assertTrue(Script.matchNamed("myXml/cat/scores/score[2]", null, "5", ctx).pass);
        // using json path for xml !
        assertTrue(Script.matchNamed("myXml.cat.scores.score[1]", null, "'5'", ctx).pass);
        // lenient primitive equality check
        assertTrue(Script.matchNamed("myXml.cat.scores.score[1]", null, "5", ctx).pass);
    }

    @Test
    public void testMatchXmlRepeatedElements() {
        ScriptContext ctx = getContext();
        String xml = "<foo><bar>baz1</bar><bar>baz2</bar></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        ctx.vars.put(ScriptValueMap.VAR_RESPONSE, doc);
        ScriptValue response = ctx.vars.get(ScriptValueMap.VAR_RESPONSE);
        assertTrue(Script.matchXmlPath(MatchType.EQUALS, response, "/", "<foo><bar>baz1</bar><bar>baz2</bar></foo>", ctx).pass);
        assertTrue(Script.matchXmlPath(MatchType.EQUALS, response, "/foo/bar[2]", "'baz2'", ctx).pass);
        assertTrue(Script.matchXmlPath(MatchType.EQUALS, response, "/foo/bar[1]", "'baz1'", ctx).pass);
    }

    @Test
    public void testMatchXmlAttributeErrorReporting() {
        ScriptContext ctx = getContext();
        Script.assign("xml", "<hello foo=\"bar\">world</hello>", ctx);
        ScriptValue xml = ctx.vars.get("xml");
        assertTrue(Script.matchXmlPath(MatchType.EQUALS, xml, "/", "<hello foo=\"bar\">world</hello>", ctx).pass);
        AssertionResult ar = Script.matchXmlPath(MatchType.EQUALS, xml, "/", "<hello foo=\"baz\">world</hello>", ctx);
        assertFalse(ar.pass);
        assertTrue(ar.message.contains("/hello/@foo"));
    }

    @Test
    public void testAssigningAndCallingFunctionThatUpdatesVars() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "function(){ return { bar: 'baz' } }", ctx);
        ScriptValue testFoo = ctx.vars.get("foo");
        assertEquals(ScriptValue.Type.JS_FUNCTION, testFoo.getType());
        Script.callAndUpdateConfigAndAlsoVarsIfMapReturned(false, "foo", null, ctx);
        ScriptValue testBar = ctx.vars.get("bar");
        assertEquals("baz", testBar.getValue());
    }

    @Test
    public void testAssigningAndCallingFunctionThatCanBeUsedToAssignVariable() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "function(){ return 'world' }", ctx);
        Script.assign("hello", "call foo", ctx);
        ScriptValue hello = ctx.vars.get("hello");
        assertEquals("world", hello.getValue());
    }

    @Test
    public void testAssigningAndCallingFunctionWithArgumentsThatCanBeUsedToAssignVariable() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "function(pre){ return pre + ' world' }", ctx);
        Script.assign("hello", "call foo 'hello'", ctx);
        ScriptValue hello = ctx.vars.get("hello");
        assertEquals("hello world", hello.getValue());
    }

    @Test
    public void testCallingFunctionThatTakesPrimitiveArgument() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "function(a){ return { bar: a } }", ctx);
        ScriptValue testFoo = ctx.vars.get("foo");
        assertEquals(ScriptValue.Type.JS_FUNCTION, testFoo.getType());
        Script.callAndUpdateConfigAndAlsoVarsIfMapReturned(false, "foo", "'hello'", ctx);
        ScriptValue testBar = ctx.vars.get("bar");
        assertEquals("hello", testBar.getValue());
    }

    @Test
    public void testCallingFunctionThatTakesJsonArgument() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "function(a){ return { bar: a.hello } }", ctx);
        ScriptValue testFoo = ctx.vars.get("foo");
        assertEquals(ScriptValue.Type.JS_FUNCTION, testFoo.getType());
        Script.callAndUpdateConfigAndAlsoVarsIfMapReturned(false, "foo", "{ hello: 'world' }", ctx);
        ScriptValue testBar = ctx.vars.get("bar");
        assertEquals("world", testBar.getValue());
    }

    @Test
    public void testCallingFunctionWithJsonArray() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "function(a){ return a[0] }", ctx);
        Script.assign("bar", "call foo ['hello']", ctx);
        ScriptValue bar = ctx.vars.get("bar");
        assertEquals("hello", bar.getValue());
    }

    @Test
    public void testCallingFunctionWithJavaList() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "function(a){ return a[0] }", ctx);
        Script.assign("bar", "['hello']", ctx);
        Script.assign("baz", "call foo bar", ctx);
        ScriptValue baz = ctx.vars.get("baz");
        assertEquals("hello", baz.getValue());
    }

    @Test
    public void testCallingFunctionThatUsesJsonPath() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "{ bar: [{baz: 1}, {baz: 2}, {baz: 3}]}", ctx);
        Script.assign("fun", "function(){ return karate.get('foo.bar[*].baz') }", ctx);
        Script.assign("res", "call fun", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res", null, "[1, 2, 3]", ctx).pass);
        // 'normal' variable name
        Script.assign("fun", "function(){ return karate.get('foo') }", ctx);
        Script.assign("res", "call fun", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res", null, "{ bar: [{baz: 1}, {baz: 2}, {baz: 3}]}", ctx).pass);
    }

    @Test
    public void testCallingFunctionWithJsonArrayReturnedFromAnotherFunction() {
        ScriptContext ctx = getContext();
        Script.assign("fun1", "function(){ return [1, 2, 3] }", ctx);
        Script.assign("res1", "call fun1", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res1", null, "[1, 2, 3]", ctx).pass);
        Script.assign("fun2", "function(arg){ return arg.length }", ctx);
        Script.assign("res2", "call fun2 res1", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res2", null, "3", ctx).pass);
    }

    @Test
    public void testCallingFunctionWithJsonReturnedFromAnotherFunction() {
        ScriptContext ctx = getContext();
        Script.assign("fun1", "function(){ return { foo: 'bar' } }", ctx);
        Script.assign("res1", "call fun1", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res1", null, "{ foo: 'bar' }", ctx).pass);
        Script.assign("fun2", "function(arg){ return arg.foo }", ctx);
        Script.assign("res2", "call fun2 res1", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res2", null, "'bar'", ctx).pass);
    }

    @Test
    public void testCallingFunctionWithStringReturnedFromAnotherFunction() {
        ScriptContext ctx = getContext();
        Script.assign("fun1", "function(){ return 'foo' }", ctx);
        Script.assign("res1", "call fun1", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res1", null, "'foo'", ctx).pass);
        Script.assign("fun2", "function(arg){ return arg + 'bar' }", ctx);
        Script.assign("res2", "call fun2 res1", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res2", null, "'foobar'", ctx).pass);
    }

    @Test
    public void testParsingVariableAndJsonPath() {
        assertEquals(Pair.of("foo", "$"), Script.parseVariableAndPath("foo"));
        assertEquals(Pair.of("foo", "$.bar"), Script.parseVariableAndPath("foo.bar"));
        assertEquals(Pair.of("foo", "$['bar']"), Script.parseVariableAndPath("foo['bar']"));
        assertEquals(Pair.of("foo", "$[0]"), Script.parseVariableAndPath("foo[0]"));
        assertEquals(Pair.of("foo", "$[0].bar"), Script.parseVariableAndPath("foo[0].bar"));
        assertEquals(Pair.of("foo", "$[0]['bar']"), Script.parseVariableAndPath("foo[0]['bar']"));
        assertEquals(Pair.of("foo", "/bar"), Script.parseVariableAndPath("foo/bar"));
        assertEquals(Pair.of("foo", "/"), Script.parseVariableAndPath("foo/"));
        assertEquals(Pair.of("foo", "/bar/baz[1]/ban"), Script.parseVariableAndPath("foo/bar/baz[1]/ban"));
    }

    @Test
    public void testSetValueOnVariableByPath() {
        ScriptContext ctx = getContext();        
        // json
        Script.assign("json", "{ foo: 'bar' }", ctx);       
        Script.setValueByPath("json", "$.foo", "'hello'", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: 'hello' }", ctx).pass);
        Script.setValueByPath("json.foo", null, "null", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: null }", ctx).pass);        
        Script.setValueByPath("json.foo", null, "'world'", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: 'world' }", ctx).pass);        
        Script.setValueByPath("json.bar[0]", null, "1", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: 'world', bar: [1] }", ctx).pass);
        Script.setValueByPath("json.bar[0]", null, "2", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: 'world', bar: [2] }", ctx).pass);
        Script.setValueByPath("json.bar[1]", null, "3", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: 'world', bar: [2, 3] }", ctx).pass); 
        // json key that needs to be within quotes
        Script.assign("json", "{ 'bad-name': 'foo' }", ctx);       
        Script.setValueByPath("json", "$['bad-name']", "'bar'", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ 'bad-name': 'bar' }", ctx).pass);        
        // xml        
        Script.assign("xml", "<root><foo>bar</foo></root>", ctx);
        Script.setValueByPath("xml", "/root/foo", "'hello'", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "xml", null, "<root><foo>hello</foo></root>", ctx).pass);
        Script.setValueByPath("xml/root/foo", null, "null", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "xml", null, "<root><foo/></root>", ctx).pass);        
        Script.setValueByPath("xml/root/foo", null, "'world'", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "xml", null, "<root><foo>world</foo></root>", ctx).pass);        
    }
    
    @Test
    public void testSetXmlChunkAutoConversion() {
        ScriptContext ctx = getContext();
        Script.assign("xml", "<foo><bar></bar></foo>", ctx);
        Script.assign("chunk", "<hello>world</hello>", ctx);
        Script.setValueByPath("xml", "/foo/bar", "chunk", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "xml", null, "<foo><bar><hello>world</hello></bar></foo>", ctx).pass);
    }
    
    @Test
    public void testDeleteValueOnVariableByPath() {
        ScriptContext ctx = getContext();        
        // json
        Script.assign("json", "{ foo: 'bar', baz: 'ban' }", ctx);
        Script.removeValueByPath("json", "$.baz", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: 'bar' }", ctx).pass);
        Script.setValueByPath("json.baz", null, "[1, 2, 3]", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: 'bar', baz: [1, 2, 3] }", ctx).pass);
        Script.removeValueByPath("json", "$.baz[1]", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: 'bar', baz: [1, 3] }", ctx).pass);
        // xml        
        Script.assign("xml", "<root><foo>bar</foo></root>", ctx);
        Script.removeValueByPath("xml", "/root/foo", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "xml", null, "<root/>", ctx).pass);
    } 
    
    @Test
    public void testCallJsFunctionWithMap() {
        ScriptContext ctx = getContext();
        Script.assign("json", "{ foo: 'bar', hello: 'world' }", ctx);
        Script.assign("fun", "function(o){ return o }", ctx);
        Script.assign("res", "call fun json", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res", null, "json", ctx).pass);
    }

    @Test
    public void testDefaultValidators() {
        ScriptContext ctx = getContext();
        DocumentContext doc = JsonUtils.toJsonDoc("{ foo: 'bar' }");
        ctx.vars.put("json", doc);
        assertTrue(Script.matchNamed("json", "$", "{ foo: '#ignore' }", ctx).pass);
        assertTrue(Script.matchNamed("json", "$", "{ foo: '#notnull' }", ctx).pass);
        assertFalse(Script.matchNamed("json", "$", "{ foo: '#null' }", ctx).pass);
        assertTrue(Script.matchNamed("json", "$", "{ foo: '#regex^bar' }", ctx).pass);
        assertTrue(Script.matchNamed("json", "$", "{ foo: '#regex ^bar' }", ctx).pass);
        assertFalse(Script.matchNamed("json", "$", "{ foo: '#regex^baX' }", ctx).pass);
        assertFalse(Script.matchNamed("json", "$", "{ foo: '#regex ^baX' }", ctx).pass);

        doc = JsonUtils.toJsonDoc("{ foo: null }");
        ctx.vars.put("json", doc);
        assertTrue(Script.matchNamed("json", "$", "{ foo: '#ignore' }", ctx).pass);
        assertTrue(Script.matchNamed("json", "$", "{ foo: '#null' }", ctx).pass);
        assertFalse(Script.matchNamed("json", "$", "{ foo: '#notnull' }", ctx).pass);

        doc = JsonUtils.toJsonDoc("{ foo: 'a9f7a56b-8d5c-455c-9d13-808461d17b91' }");
        ctx.vars.put("json", doc);
        assertTrue(Script.matchNamed("json", "$", "{ foo: '#uuid' }", ctx).pass);

        doc = JsonUtils.toJsonDoc("{ foo: 'a9f7a56b-8d5c-455c-9d13' }");
        ctx.vars.put("json", doc);
        assertFalse(Script.matchNamed("json", "$", "{ foo: '#uuid' }", ctx).pass);

        doc = JsonUtils.toJsonDoc("{ foo: 5 }");
        ctx.vars.put("json", doc);
        ctx.vars.put("min", 4);
        ctx.vars.put("max", 6);
        assertTrue(Script.matchNamed("json", "$", "{ foo: '#number' }", ctx).pass);
        assertTrue(Script.matchNamed("json", "$", "{ foo: '#? _ == 5' }", ctx).pass);
        assertTrue(Script.matchNamed("json", "$", "{ foo: '#? _ < 6' }", ctx).pass);
        assertTrue(Script.matchNamed("json", "$", "{ foo: '#? _ > 4' }", ctx).pass);
        assertTrue(Script.matchNamed("json", "$", "{ foo: '#? _ > 4 && _ < 6' }", ctx).pass);
        assertTrue(Script.matchNamed("json", "$", "{ foo: '#? _ > min && _ < max' }", ctx).pass);
    }

    @Test
    public void testSimpleJsonMatch() {
        ScriptContext ctx = getContext();
        DocumentContext doc = JsonUtils.toJsonDoc("{ foo: 'bar' }");
        ctx.vars.put("json", doc);
        assertFalse(Script.matchNamed("json", "$", "{ }", ctx).pass);
    }

    @Test
    public void testAssignJsonChunkObjectAndUse() {
        ScriptContext ctx = getContext();
        //===
        Script.assign("parent", "{ foo: 'bar', 'ban': { a: 1 } }", ctx);
        Script.assign("child", "parent.ban", ctx);
        assertTrue(Script.matchNamed("child.a", null, "1", ctx).pass);
        //===
        Script.assign("parent", "{ foo: 'bar', 'ban': { a: [1, 2, 3] } }", ctx);
        Script.assign("child", "parent.ban", ctx);
        assertTrue(Script.matchNamed("child.a[1]", null, "2", ctx).pass);
    }

    @Test
    public void testAssignJsonChunkListAndUse() {
        ScriptContext ctx = getContext();
        //===
        Script.assign("parent", "{ foo: { bar: [{ baz: 1}, {baz: 2}, {baz: 3}] }}", ctx);
        Script.assign("child", "parent.foo", ctx);
        assertTrue(Script.matchNamed("child", null, "{ bar: [{ baz: 1}, {baz: 2}, {baz: 3}]}", ctx).pass);
        assertTrue(Script.matchNamed("child.bar", null, "[{ baz: 1}, {baz: 2}, {baz: 3}]", ctx).pass);
        assertTrue(Script.matchNamed("child.bar[0]", null, "{ baz: 1}", ctx).pass);
    }

    @Test
    public void testEvalUrl() {
        ScriptContext ctx = getContext();
        String url = "'http://localhost:8089/v1/cats'";
        assertEquals("http://localhost:8089/v1/cats", Script.eval(url, ctx).getAsString());
    }

    @Test
    public void testEvalParamWithDot() {
        ScriptContext ctx = getContext();
        String param = "'ACS.Itself'";
        assertEquals("ACS.Itself", Script.eval(param, ctx).getAsString());
    }
    
    @Test
    public void testMatchHandlesNonStringNullsGracefully() {
        ScriptContext ctx = getContext();
        Script.assign("json", "{ foo: null }", ctx);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "json.foo", null, "[]", ctx).pass);
    }    

    @Test
    public void testMatchJsonObjectContains() {
        ScriptContext ctx = getContext();
        Script.assign("json", "{ foo: 'bar', baz: [1, 2, 3] }", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ baz: [1, 2, 3], foo: 'bar' }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.CONTAINS, "json", null, "{ baz: [1, 2, 3] }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.CONTAINS, "json", null, "{ foo: 'bar' }", ctx).pass);
    }

    @Test
    public void testMatchJsonArrayContains() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "{ bar: [1, 2, 3] }", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo.bar", null, "[1 ,2, 3]", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.CONTAINS, "foo.bar", null, "[1]", ctx).pass);
    }

    @Test
    public void testMatchContainsForSingleElements() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "{ bar: [1, 2, 3] }", ctx);
        assertTrue(Script.matchNamed(MatchType.CONTAINS, "foo.bar", null, "1", ctx).pass);
        Script.assign("json", "[{ foo: 1 }, { foo: 2 }, { foo: 3 }]", ctx);
        assertTrue(Script.matchNamed(MatchType.CONTAINS, "json", null, "{ foo: 1 }", ctx).pass);
        Script.assign("json", "[{ foo: 1 }]", ctx);
        assertTrue(Script.matchNamed(MatchType.CONTAINS_ONLY, "json", null, "{ foo: 1 }", ctx).pass);
    }
    
    @Test
    public void testMatchJsonObjectErrorReporting() {   
        ScriptContext ctx = getContext();
        Script.assign("json", "{ a: 1, b: 2, c: 3}", ctx);
        AssertionResult ar = Script.matchNamed(MatchType.EQUALS, "json", null, "{ a: 1, c: 3 }", ctx);
        assertFalse(ar.pass);        
        assertTrue(ar.message.contains("actual value has 1 more key"));
    } 

    @Test
    public void testMatchJsonArrayErrorReporting() {
        ScriptContext ctx = getContext();
        Script.assign("json", "[{ foo: 1 }, { foo: 2 }, { foo: 3 }]", ctx);
        AssertionResult ar = Script.matchNamed(MatchType.EQUALS, "json", null, "[{ foo: 1 }, { foo: 2 }, { foo: 4 }]", ctx);
        assertFalse(ar.pass);
        assertTrue(ar.message.contains("$[2].foo"));
        ar = Script.matchNamed(MatchType.CONTAINS, "json", null, "[{ foo: 1 }, { foo: 2 }, { foo: 4 }]", ctx);
        assertFalse(ar.pass);
        assertTrue(ar.message.contains("$[*]"));
        ar = Script.matchNamed(MatchType.CONTAINS_ONLY, "json", null, "[{ foo: 3 }, { foo: 2 }, { foo: 0 }]", ctx);
        assertFalse(ar.pass);
        assertTrue(ar.message.contains("$[*]"));
        ar = Script.matchNamed(MatchType.CONTAINS_ONLY, "json", null, "[{ foo: 3 }, { foo: 2 }, { foo: 1 }]", ctx);
        assertTrue(ar.pass);
        ar = Script.matchNamed(MatchType.CONTAINS_ONLY, "json", null, "[{ foo: 3 }, { foo: 2 }]", ctx);
        assertFalse(ar.pass);
        assertTrue(ar.message.contains("not the same size"));
    }

    @Test
    public void testMatchStringContains() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "'hello world'", ctx);
        assertTrue(Script.matchNamed(MatchType.CONTAINS, "foo", null, "'hello'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.CONTAINS, "foo", null, "'zoo'", ctx).pass);
    }

    @Test
    public void testKarateEnvAccessFromScript() {
        String featureDir = FileUtils.getDirContaining(getClass()).getPath();
        ScriptEnv env = ScriptEnv.init("baz", new File(featureDir));
        ScriptContext ctx = new ScriptContext(env, null, null);
        Script.assign("foo", "function(){ return karate.env }", ctx);
        Script.assign("bar", "call foo", ctx);
        ScriptValue bar = ctx.vars.get("bar");
        assertEquals("baz", bar.getValue());
        // null
        env = ScriptEnv.init(null, new File(featureDir));
        ctx = new ScriptContext(env, null, null);
        Script.assign("foo", "function(){ return karate.env }", ctx);
        Script.assign("bar", "call foo", ctx);
        bar = ctx.vars.get("bar");
        assertNull(bar.getValue());
    }

    @Test
    public void testCallingFeatureWithNoArgument() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "call read('test-called.feature')", ctx);
        ScriptValue a = Script.evalJsonPathOnVarByName("foo", "$.a", ctx);
        assertEquals(1, a.getValue());
        ScriptValue b = Script.evalJsonPathOnVarByName("foo", "$.b", ctx);
        assertEquals(2, b.getValue());
    }

    @Test
    public void testCallingFeatureWithVarOverrides() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "call read('test-called.feature') { c: 3 }", ctx);
        ScriptValue a = Script.evalJsonPathOnVarByName("foo", "$.a", ctx);
        assertEquals(1, a.getValue());
        ScriptValue b = Script.evalJsonPathOnVarByName("foo", "$.b", ctx);
        assertEquals(2, b.getValue());
        ScriptValue c = Script.evalJsonPathOnVarByName("foo", "$.c", ctx);
        assertEquals(3, c.getValue());
    }

    @Test
    public void testCallingFeatureWithVarOverrideFromVariable() {
        ScriptContext ctx = getContext();
        Script.assign("bar", "{ c: 3 }", ctx);
        Script.assign("foo", "call read('test-called.feature') bar", ctx);
        ScriptValue a = Script.evalJsonPathOnVarByName("foo", "$.a", ctx);
        assertEquals(1, a.getValue());
        ScriptValue b = Script.evalJsonPathOnVarByName("foo", "$.b", ctx);
        assertEquals(2, b.getValue());
        ScriptValue c = Script.evalJsonPathOnVarByName("foo", "$.c", ctx);
        assertEquals(3, c.getValue());
    }

    @Test
    public void testCallingFeatureWithList() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "call read('test-called.feature') [{c: 100}, {c: 200}, {c: 300}]", ctx);
        ScriptValue c0 = Script.evalJsonPathOnVarByName("foo", "$[0].c", ctx);
        assertEquals(100, c0.getValue());
        ScriptValue c1 = Script.evalJsonPathOnVarByName("foo", "$[1].c", ctx);
        assertEquals(200, c1.getValue());
        ScriptValue c2 = Script.evalJsonPathOnVarByName("foo", "$[2].c", ctx);
        assertEquals(300, c2.getValue());
    }

    @Test
    public void testCallingFeatureThatEvaluatesEmbeddedExpressions() {
        ScriptContext ctx = getContext();
        Script.assign("result", "call read('test-called-embedded.feature') { foo: 'world' }", ctx);
        ScriptValue sv1 = Script.evalJsonPathOnVarByName("result", "$.json.hello", ctx);
        assertEquals("world", sv1.getValue());
        ScriptValue sv2 = Script.evalJsonPathOnVarByName("result", "$.xml.hello", ctx);
        assertEquals("world", sv2.getValue());
    }

    @Test
    public void testCallingFeatureThatEvaluatesEmbeddedExpressionsFromFileRead() {
        ScriptContext ctx = getContext();
        Script.assign("result", "call read('test-called-embedded-file.feature') { foo: 'world' }", ctx);
        ScriptValue sv1 = Script.evalJsonPathOnVarByName("result", "$.json.hello", ctx);
        assertEquals("world", sv1.getValue());
        ScriptValue sv2 = Script.evalJsonPathOnVarByName("result", "$.xml.hello", ctx);
        assertEquals("world", sv2.getValue());
    }

    @Test
    public void testCallingFeatureWithJsonCreatedByJavaScript() {
        ScriptContext ctx = getContext();
        Script.assign("fun", "function(){ return { c: 100} }", ctx);
        Script.assign("res", "call fun", ctx);
        Script.assign("foo", "call read('test-called.feature') res", ctx);
        ScriptValue c = Script.evalJsonPathOnVarByName("foo", "$.c", ctx);
        assertEquals(100, c.getValue());
    }

    @Test
    public void testCallingFeatureWithJsonArrayCreatedByJavaScript() {
        ScriptContext ctx = getContext();
        Script.assign("fun", "function(){ return [{ c: 100}] }", ctx);
        Script.assign("res", "call fun", ctx);
        Script.assign("foo", "call read('test-called.feature') res", ctx);
        ScriptValue c = Script.evalJsonPathOnVarByName("foo", "$[0].c", ctx);
        assertEquals(100, c.getValue());
    }

    @Test
    public void testGetSyntaxForJson() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "[{baz: 1}, {baz: 2}, {baz: 3}]", ctx);
        Script.assign("nums", "get foo[*].baz", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "nums", null, "[1, 2, 3]", ctx).pass);
        Script.assign("foo", "{ bar: [{baz: 1}, {baz: 2}, {baz: 3}]}", ctx);
        Script.assign("nums", "get foo.bar[*].baz", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "nums", null, "[1, 2, 3]", ctx).pass);
        Script.assign("nums", "get foo $.bar[*].baz", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "nums", null, "[1, 2, 3]", ctx).pass);
    }

    @Test
    public void testGetSyntaxForXml() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "<records>\n  <record>a</record>\n  <record>b</record>\n  <record>c</record>\n</records>", ctx);
        Script.assign("count", "get foo count(//record)", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "count", null, "3", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "count", null, "3", ctx).pass);
    }

    @Test
    public void testFromJsKarateCallFeatureWithNoArg() {
        ScriptContext ctx = getContext();
        Script.assign("fun", "function(){ return karate.call('test-called.feature') }", ctx);
        Script.assign("res", "fun()", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res.a", null, "1", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res.b", null, "2", ctx).pass);
    }

    @Test
    public void testFromJsKarateCallFeatureWithJsonArg() {
        ScriptContext ctx = getContext();
        Script.assign("fun", "function(){ return karate.call('test-called.feature', {c: 3}) }", ctx);
        Script.assign("res", "fun()", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res.a", null, "1", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res.b", null, "2", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res.c", null, "3", ctx).pass);
    }

    @Test
    public void testFromJsKarateGetForNonExistentVariable() {
        ScriptContext ctx = getContext();
        Script.assign("fun", "function(){ var foo = karate.get('foo'); return foo ? true : false }", ctx);
        Script.assign("res", "fun()", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res", null, "false", ctx).pass);
    }

    @Test
    public void testFromJsKarateGetForJsonArrayVariable() {
        ScriptContext ctx = getContext();
        Script.assign("fun", "function(){ return [1, 2, 3] }", ctx);
        Script.assign("res", "call fun", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res", null, "[1, 2, 3]", ctx).pass);
    }

    @Test
    public void testFromJsKarateGetForJsonObjectVariableAndCallFeatureAndJs() {
        ScriptContext ctx = getContext();
        Script.assign("fun", "read('headers.js')", ctx);
        Script.assign("res", "call fun", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res", null, "{ foo: 'bar_someValue' }", ctx).pass);
        Script.assign("signin", "call read('signin.feature')", ctx);
        Script.assign("ticket", "signin.ticket", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "ticket", null, "{ foo: 'bar' }", ctx).pass);
        Script.assign("res", "call fun", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res", null, "{ foo: 'bar_someValue', baz: 'ban' }", ctx).pass);
    }
    
    @Test
    public void testFromJsKarateJsonPath() {
        ScriptContext ctx = getContext();
        Script.assign("fun", "function(){ var foo = [{v:1},{v:2}]; return karate.jsonPath(foo, '$[*].v') }", ctx);
        Script.assign("res", "call fun", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "res", null, "[1, 2]", ctx).pass);
    }    

    @Test
    public void testAssigningRawTextWhichOtherwiseConfusesKarate() {
        ScriptContext ctx = getContext();
        try {
            Script.assign("foo", "{ not json }", ctx);
            fail("we expected this to fail");
        } catch (InvalidJsonException e) {
            logger.debug("expected {}", e.getMessage());
        }
        Script.assignText("foo", "{ not json }", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'{ not json }'", ctx).pass);
    }

    @Test
    public void testBigDecimalsInJson() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "{ val: -1002.2000000000002 }", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ val: -1002.2000000000002 }", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ val: -1002.2000000000001 }", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ val: -1002.20 }", ctx).pass);
        Script.assign("foo", "{ val: -1002.20 }", ctx);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ val: -1002.2000000000001 }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ val: -1002.2000000000000 }", ctx).pass);
        Script.assign("foo", "{ val: -1002.2000000000001 }", ctx);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ val: -1002.20 }", ctx).pass);
        Script.assign("foo", "{ val: -1002.2000000000000 }", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ val: -1002.20 }", ctx).pass);
    }
    
    @Test
    public void testDollarInEmbeddedExpressions() {
        ScriptContext ctx = getContext();
        Script.assign("temperature", "{ celsius: 100, fahrenheit: 212 }", ctx);
        assertTrue(Script.matchNamed(MatchType.CONTAINS, "temperature", null, "{ fahrenheit: 212 }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.CONTAINS, "temperature", null, "{ fahrenheit: '#($.celsius * 1.8 + 32)' }", ctx).pass);
    }
    
    @Test
    public void testValidationStringInsteadOfNumberInPredicate() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "{ bar: 5 }", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ bar: '#number' }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ bar: '#? _ == 5' }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ bar: '#? _ > 0' }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ bar: '#number? _ > 0' }", ctx).pass);
        Script.assign("foo", "{ bar: '5' }", ctx);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ bar: '#number' }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ bar: '#? _ == 5' }", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ bar: '#? _ === 5' }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ bar: '#? _ > 0' }", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "{ bar: '#number? _ > 0' }", ctx).pass);
    }

    @Test
    public void testMatchMacroArray() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "['bar', 'baz']", ctx);
        Script.assign("arr", "'#string'", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#array'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#number'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[]'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[2]'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[1]'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[_ == 2]'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[_ != 2]'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] arr'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] (arr)'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] #string'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] #number'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[2] #string'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[1] arr'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[1] (arr)'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[2]? _.length == 3'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[2]? _.length == 4'", ctx).pass);
        // non-root path
        Script.assign("foo", "{ ban: ['bar', 'baz'], count: 2 }", ctx);
        Script.assign("len", "2", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo.ban", null, "'#[] arr'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo.ban", null, "'#[] (arr)'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo.ban", null, "'#[2] arr'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo.ban", null, "'#[2] (arr)'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo.ban", null, "'#[$.count] #string'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo.ban", null, "'#[foo.count] #string'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo.ban", null, "'#[len] #string'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo.ban", null, "'#[_ < 3]'", ctx).pass);
    }
    
    @Test
    public void testMatchMacroArrayComplex() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "[{ a: 1, b: 2 }, { a: 3, b: 4 }]", ctx);
        Script.assign("bar", "{ a: '#number', b: '#number' }", ctx);
        Script.assign("baz", "{ c: '#number' }", ctx);
        assertTrue(Script.matchNamed(MatchType.EACH_EQUALS, "foo", null, "bar", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo[0]", null, "'#(bar)'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo[0]", null, "'#(^bar)'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo[0]", null, "'#(!^bar)'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo[0]", null, "'#(!^baz)'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] bar'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] ^bar'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] !^bar'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] !^baz'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] (bar)'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] (^bar)'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] (!^bar)'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] (!^baz)'", ctx).pass);
    }

    @Test
    public void testMatchMacroArrayComplexContains() {
        ScriptContext ctx = getContext();
        Script.assign("foo", "[{ a: 1, b: 2 }, { a: 3, b: 4 }]", ctx);
        Script.assign("bar", "{ b: '#number' }", ctx);
        Script.assign("baz", "{ c: '#number' }", ctx);
        assertFalse(Script.matchNamed(MatchType.EACH_EQUALS, "foo", null, "bar", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo[0]", null, "'#(bar)'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo[0]", null, "'#(^bar)'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo[0]", null, "'#(!^bar)'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo[0]", null, "'#(!^baz)'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] bar'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] ^bar'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] !^bar'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] !^baz'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] (bar)'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] (^bar)'", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] (!^bar)'", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "foo", null, "'#[] (!^baz)'", ctx).pass);
    }    

    @Test
    public void testSchemaLikeAndOptionalKeys() {
        ScriptContext ctx = getContext();
        Script.assign("child", "{ hello: '#string' }", ctx);
        Script.assign("json", "{ foo: 'bar', baz: [1, 2, 3]}", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: '#string', baz: '#[] #number' }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: '#string', baz: '#[] #number', child: '##(child)' }", ctx).pass);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: '#string', baz: '#[] #number', child: '#(child)' }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: '#string', baz: '##[] #number' }", ctx).pass);
        Script.assign("json", "{ foo: 'bar', child: { hello: 'world' } }", ctx);
        assertFalse(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: '#string', baz: '#[] #number', child: '#(child)' }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: '#string', baz: '##[] #number', child: '#(child)' }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: '#string', baz: '##[] #number', child: '##(child)' }", ctx).pass);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: '#string', blah: '##number', child: '#(child)' }", ctx).pass);
        Script.assign("json", "{ foo: 'bar', baz: null }", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "{ foo: '#string', baz: '##string' }", ctx).pass);
        Script.assign("json", "null", ctx);
        assertTrue(Script.matchNamed(MatchType.EQUALS, "json", null, "'##string'", ctx).pass);
    }
    
    @Test
    public void testReplace() {
        ScriptContext ctx = getContext();
        assertEquals("foo", Script.replacePlaceholderText("foo", "foo", "'bar'", ctx));
        assertEquals("bar", Script.replacePlaceholderText("<foo>", "foo", "'bar'", ctx));
        assertEquals("bar", Script.replacePlaceholderText("<foo>", "foo", "'bar'", ctx));
        assertEquals("bar", Script.replacePlaceholderText("@@foo@@", "@@foo@@", "'bar'", ctx));
        assertEquals("bar bar bar", Script.replacePlaceholderText("<foo> <foo> <foo>", "foo", "'bar'", ctx));
    }
    
}
