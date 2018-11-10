package com.intuit.karate;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class JsonTest {
    
    @Test
    public void testJson() {
        new Json("{ hello: 'world' }").equals("{ hello: 'world' }");
        new Json("{ hello: 'world' }").equals("{ hello: '#string' }");
        new Json().set("hello", "world").equals("{ hello: 'world' }");
        new Json().set("foo.bar", "world").equals("{ foo: { bar: 'world' }}");
        new Json().set("foo.bar", "[]").equals("{ foo: { bar: [] }}");
        new Json().set("foo.bar", "{ ban: 'baz' }").equals("{ foo: { bar: { ban: 'baz' } }}");
    }
    
}
