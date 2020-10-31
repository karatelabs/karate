package com.intuit.karate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import static com.intuit.karate.ScriptValue.Type.*;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ScriptValueTest {

    static final Logger logger = LoggerFactory.getLogger(ScriptValueTest.class);

    @Test
    void testTypeDetection() {
        DocumentContext doc = JsonPath.parse("{ foo: 'bar' }");
        ScriptValue sv = new ScriptValue(doc);
        assertEquals(JSON, sv.getType());
        doc = JsonPath.parse("[1, 2]");
        sv = new ScriptValue(doc);
        assertEquals(JSON, sv.getType());
        Object temp = doc.read("$");
        assertTrue(temp instanceof List);
        sv = new ScriptValue(1);
        assertTrue(sv.isPrimitive());
        assertTrue(sv.isNumber());
        assertEquals(1, sv.getAsNumber().intValue());
        sv = new ScriptValue(100L);
        assertTrue(sv.isPrimitive());
        assertTrue(sv.isNumber());
        assertEquals(100, sv.getAsNumber().longValue());
        sv = new ScriptValue(1.0);
        assertTrue(sv.isPrimitive());
        assertTrue(sv.isNumber());
        assertEquals(1.0, sv.getAsNumber().doubleValue(), 0);
    }

}
