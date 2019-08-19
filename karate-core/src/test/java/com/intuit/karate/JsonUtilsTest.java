package com.intuit.karate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minidev.json.parser.ParseException;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class JsonUtilsTest {

    private static final Logger logger = LoggerFactory.getLogger(JsonUtilsTest.class);

    @Test
    public void testNonStrictJsonParsing() {
        String raw = "{ foo: 'bar' }";
        DocumentContext dc = JsonUtils.toJsonDoc(raw);
        logger.debug("parsed json: {}", dc.jsonString());
        String value = dc.read("$.foo");
        assertEquals("bar", value);
    }

    @Test
    public void testJsonArrayAsRoot() {
        String raw = "[1, 2, 3]";
        DocumentContext doc = JsonUtils.toJsonDoc(raw);
        Object sec = doc.read("$[1]");
        assertEquals(2, sec);
    }

    @Test
    public void testJsonChunkByPath() {
        String raw = "[{ foo: 'bar' }]";
        DocumentContext doc = JsonUtils.toJsonDoc(raw);
        Map map = doc.read("$[0]");
        DocumentContext foo = JsonPath.parse(map);
        assertEquals("{\"foo\":\"bar\"}", foo.jsonString());
    }

    @Test
    public void testMapToJson() {
        Map<String, Object> map = new HashMap<>();
        map.put("foo", "bar");
        Map<String, Object> child = new HashMap<>();
        child.put("hello", "world");
        map.put("baz", child);
        DocumentContext doc = JsonPath.parse(map);
        logger.debug("from map: {}", doc.jsonString());
        assertEquals("world", doc.read("$.baz.hello"));
    }

    @Test
    public void testSetByPath() {
        String raw = "{ foo: 'bar' }";
        DocumentContext doc = JsonUtils.toJsonDoc(raw);
        JsonUtils.setValueByPath(doc, "$.foo", "baz");
        assertEquals("{\"foo\":\"baz\"}", doc.jsonString());
        Map temp = JsonUtils.toJsonDoc("{ baz: 'but' }").read("$");
        JsonUtils.setValueByPath(doc, "$.foo", temp);
        assertEquals("{\"foo\":{\"baz\":\"but\"}}", doc.jsonString());
        JsonUtils.setValueByPath(doc, "$.boo", temp);
        assertEquals("{\"foo\":{\"baz\":\"but\"},\"boo\":{\"baz\":\"but\"}}", doc.jsonString());
        doc = JsonUtils.toJsonDoc(raw);
        JsonUtils.setValueByPath(doc, "$.boo", JsonUtils.toJsonDoc("[1, 2]").read("$"));
        assertEquals("{\"foo\":\"bar\",\"boo\":[1,2]}", doc.jsonString());
        JsonUtils.setValueByPath(doc, "$.boo[2]", 3); // append
        assertEquals("{\"foo\":\"bar\",\"boo\":[1,2,3]}", doc.jsonString());
        JsonUtils.setValueByPath(doc, "$.boo[1]", 10); // update by array index
        assertEquals("{\"foo\":\"bar\",\"boo\":[1,10,3]}", doc.jsonString());
        doc = JsonUtils.toJsonDoc(raw);
        JsonUtils.setValueByPath(doc, "$.boo[0]", 3); // create and append as first
        assertEquals("{\"foo\":\"bar\",\"boo\":[3]}", doc.jsonString());
        // special case if root is array
        doc = JsonUtils.toJsonDoc("[{ foo: 'bar'}]");
        JsonUtils.setValueByPath(doc, "$[1]", JsonUtils.toJsonDoc("{ foo: 'baz' }").read("$"));
        assertEquals("[{\"foo\":\"bar\"},{\"foo\":\"baz\"}]", doc.jsonString());
        doc = JsonUtils.toJsonDoc("{}");
        JsonUtils.setValueByPath(doc, "$.foo.bar", 1);
        assertEquals("{\"foo\":{\"bar\":1}}", doc.jsonString());
        doc = JsonUtils.toJsonDoc("[]");
        JsonUtils.setValueByPath(doc, "$[0].foo.bar", 1);
        assertEquals("[{\"foo\":{\"bar\":1}}]", doc.jsonString());
    }

    @Test
    public void testParsingParentAndLeafName() {
        assertEquals(StringUtils.pair("", "$"), JsonUtils.getParentAndLeafPath("$"));
        assertEquals(StringUtils.pair("$", "foo"), JsonUtils.getParentAndLeafPath("$.foo"));
        assertEquals(StringUtils.pair("$", "['foo']"), JsonUtils.getParentAndLeafPath("$['foo']"));
        assertEquals(StringUtils.pair("$.foo", "bar"), JsonUtils.getParentAndLeafPath("$.foo.bar"));
        assertEquals(StringUtils.pair("$.foo", "['bar']"), JsonUtils.getParentAndLeafPath("$.foo['bar']"));
        assertEquals(StringUtils.pair("$.foo", "bar[0]"), JsonUtils.getParentAndLeafPath("$.foo.bar[0]"));
        assertEquals(StringUtils.pair("$.foo", "['bar'][0]"), JsonUtils.getParentAndLeafPath("$.foo['bar'][0]"));
        assertEquals(StringUtils.pair("$.foo[2]", "bar[0]"), JsonUtils.getParentAndLeafPath("$.foo[2].bar[0]"));
        assertEquals(StringUtils.pair("$.foo[2]", "['bar'][0]"), JsonUtils.getParentAndLeafPath("$.foo[2]['bar'][0]"));
        assertEquals(StringUtils.pair("$.foo[2]", "bar"), JsonUtils.getParentAndLeafPath("$.foo[2].bar"));
        assertEquals(StringUtils.pair("$.foo[2]", "['bar']"), JsonUtils.getParentAndLeafPath("$.foo[2]['bar']"));
    }

    @Test
    public void testParsingYaml() {
        String yaml = "hello: 25";
        DocumentContext doc = JsonUtils.fromYaml(yaml);
        assertEquals("{\"hello\":25}", doc.jsonString());
    }

    @Test
    public void testYamlToMutation() throws Exception {
        InputStream is = getClass().getResourceAsStream("mutation.yaml");
        String yaml = FileUtils.toString(is);
        DocumentContext doc = JsonUtils.fromYaml(yaml);
        assertTrue(doc.jsonString().contains("[\"id\",\"name\",\"notes\",\"deleted\"]"));
    }

    @Test
    public void testPrettyPrint() {
        String raw = "{ foo: 'bar', baz: null, 'spa cey': [1, 2, 3], bool: true, nest: { a: 'b', 'hy-phen': 'blah' } }";
        DocumentContext doc = JsonUtils.toJsonDoc(raw);
        String temp = JsonUtils.toPrettyJsonString(doc);
        String expected
                = "{\n"
                + "  \"foo\": \"bar\",\n"
                + "  \"baz\": null,\n"
                + "  \"spa cey\": [\n"
                + "    1,\n"
                + "    2,\n"
                + "    3\n"
                + "  ],\n"
                + "  \"bool\": true,\n"
                + "  \"nest\": {\n"
                + "    \"a\": \"b\",\n"
                + "    \"hy-phen\": \"blah\"\n"
                + "  }\n"
                + "}\n";
        assertEquals(temp, expected);
    }

    @Test
    public void testPojoConversion() {
        ComplexPojo pojo = new ComplexPojo();
        pojo.setFoo("testFoo");
        pojo.setBar(1);
        ComplexPojo p1 = new ComplexPojo();
        p1.setFoo("p1");
        ComplexPojo p2 = new ComplexPojo();
        p2.setFoo("p2");
        pojo.setBan(Arrays.asList(p1, p2));
        String s = JsonUtils.toJson(pojo);
        String expected = "{\"bar\":1,\"foo\":\"testFoo\",\"baz\":null,\"ban\":[{\"bar\":0,\"foo\":\"p1\",\"baz\":null,\"ban\":null},{\"bar\":0,\"foo\":\"p2\",\"baz\":null,\"ban\":null}]}";
        assertEquals(s, expected);
        ComplexPojo temp = (ComplexPojo) JsonUtils.fromJson(s, ComplexPojo.class.getName());
        assertEquals(temp.getFoo(), "testFoo");
        assertEquals(2, temp.getBan().size());
        temp = JsonUtils.fromJson(s, ComplexPojo.class);
        assertEquals(temp.getFoo(), "testFoo");
        assertEquals(2, temp.getBan().size());
        s = XmlUtils.toXml(pojo);
        assertEquals(s, "<root><bar>1</bar><foo>testFoo</foo><baz/><ban><bar>0</bar><foo>p1</foo><baz/><ban/></ban><ban><bar>0</bar><foo>p2</foo><baz/><ban/></ban></root>");
    }

    @Test
    public void testEmptyJsonObject() {
        DocumentContext doc = JsonUtils.emptyJsonObject();
        String json = doc.jsonString();
        assertEquals("{}", json);
    }

    @Test
    public void testEmptyJsonArray() {
        DocumentContext doc = JsonUtils.emptyJsonArray(0);
        String json = doc.jsonString();
        assertEquals("[]", json);
        doc = JsonUtils.emptyJsonArray(1);
        json = doc.jsonString();
        assertEquals("[{}]", json);
        doc = JsonUtils.emptyJsonArray(2);
        json = doc.jsonString();
        assertEquals("[{},{}]", json);
    }

    @Test
    public void testWriteJsonWithByteArrayValueWillFail() {
        Map<String, Object> map = new HashMap();
        byte[] bytes = "hello".getBytes();
        map.put("foo", bytes);
        try {
            JsonUtils.toJson(map);
            fail("we should not have reached here");
        } catch (Exception e) {
            assertTrue(e instanceof ClassCastException);
        }
    }

    @Test
    public void testCsv() {
        String raw = FileUtils.toString(getClass().getResourceAsStream("test.csv"));
        DocumentContext doc = JsonUtils.fromCsv(raw);
        Match.equals(doc, "[{ foo: 'goodbye', bar: '10', baz: 'true' }, { foo: 'cruel', bar: '20', baz: 'false' }, { foo: 'world', bar: '30', baz: 'true' }]");
    }

    @Test
    public void testMalformed() {
        String text = FileUtils.toString(getClass().getResourceAsStream("malformed.txt"));
        try {
            Object o = JsonUtils.toJsonDocStrict(text);
            fail("we should not have reached here");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof ParseException);
        }
    }

}
