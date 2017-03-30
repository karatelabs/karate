package com.intuit.karate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
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
    }

    @Test
    public void testLeafName() {
        assertEquals(Pair.of("", "$"), JsonUtils.getParentAndLeafPath("$"));
        assertEquals(Pair.of("$", "foo"), JsonUtils.getParentAndLeafPath("$.foo"));
        assertEquals(Pair.of("$.foo", "bar"), JsonUtils.getParentAndLeafPath("$.foo.bar"));
        assertEquals(Pair.of("$.foo", "bar[0]"), JsonUtils.getParentAndLeafPath("$.foo.bar[0]"));
        assertEquals(Pair.of("$.foo[2]", "bar[0]"), JsonUtils.getParentAndLeafPath("$.foo[2].bar[0]"));
        assertEquals(Pair.of("$.foo[2]", "bar"), JsonUtils.getParentAndLeafPath("$.foo[2].bar"));
    }

    @Test
    public void testParsingYaml() {
        String yaml = "hello: 25";
        DocumentContext doc = JsonUtils.fromYaml(yaml);
        assertEquals("{\"hello\":25}", doc.jsonString());
    }
    
    @Test
    public void testYamlToMutation() throws Exception {
        String yaml = IOUtils.toString(getClass().getResourceAsStream("mutation.yaml"), "utf-8");
        DocumentContext doc = JsonUtils.fromYaml(yaml);
        assertTrue(doc.jsonString().contains("[\"id\",\"name\",\"notes\",\"deleted\"]"));
    }

}
