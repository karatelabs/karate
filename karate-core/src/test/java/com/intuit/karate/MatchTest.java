package com.intuit.karate;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class MatchTest {
    
    private static final Logger logger = LoggerFactory.getLogger(MatchTest.class);
    
    @Test
    public void testSomeMatches() {
        Map<String, Object> map =  Match.init()
                .defText("name", "Billie")
                .def("cat", "{ name: '#(name)' }")
                .asMap("cat");
        Match.equals(map, "{ name: 'Billie' }");
        Match.init()
                .defText("name", "Billie")
                .eval("{ name: '#(name)' }")
                .equals("{ name: 'Billie' }");
        Match.init()
                .def("billie", map)
                .eval("billie.name")
                .equalsText("Billie");
        Match.init("{ foo: 1 }")
                .eval("2 + foo")
                .equalsObject(3);
        List<Object> list = Match.init()
                .def("foo", "[1, 2]")
                .asList("foo");
        Match.equals(list, "[1, 2]");
        Match.contains(list, "[1, 2]");
        Match.contains(list, "[1]");
    }
    
    @Test
    public void testJson() {
        Match.json("{ a: 'foo', b: 1 }").equals("{ b: 1, a: 'foo' }");
        Match.json("[{ a: 'foo', b: 1 }, { a: 'bar', b: 2 }]").equals("[{ b: 1, a: 'foo' }, { b: 2, a: 'bar' }]");
    }
    
    @Test
    public void testXml() {
        Match.xml("<foo>a</foo>").equals("<foo>a</foo>");
        Match.xml("<foo>a</foo>").asMap().get("foo").equals("a");
    }

    @Test
    public void testString() {
        Match.equalsText("foo", "foo");
        Match.containsText("foo", "foo");
        Match.containsText("foobar", "oob");
    } 
    
    
}
