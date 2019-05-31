package com.intuit.karate;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class JsonTest {
    
    @Test
    public void testJson() {
        new Json("{ hello: 'world' }").equals("{ hello: 'world' }");
        new Json("{ hello: 'world' }").equals("{ hello: '#string' }");
        assertEquals("world", new Json("{ hello: 'world' }").get("hello"));
        new Json("{ hello: 'world' }").getMatcher("hello").equalsText("world");
        new Json("{ a: { b: 2 } }").getJson("a").equals("{ b: 2 }");
        new Json("{ a: [1, 2] }").getJson("a").equals("[1, 2]");
        new Json().set("hello", "world").equals("{ hello: 'world' }");
        new Json().set("foo.bar", "world").equals("{ foo: { bar: 'world' }}");
        new Json().set("foo.bar", "[]").equals("{ foo: { bar: [] }}");
        new Json().set("foo.bar", "{ ban: 'baz' }").equals("{ foo: { bar: { ban: 'baz' } }}");
    }
    
}
