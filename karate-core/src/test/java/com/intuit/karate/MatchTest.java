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
        Map<String, Object> map = new Match()
                .text("name", "Billie")
                .def("cat", "{ name: '#(name)' }")
                .asMap("cat");
        Match.equals(map, "{ name: 'Billie' }");
        new Match()
                .text("name", "Billie")
                .eval("{ name: '#(name)' }")
                .equals("{ name: 'Billie' }");
        new Match()
                .def("billie", map)
                .eval("billie.name")
                .equalsText("Billie");
        new Match("{ foo: 1 }")
                .eval("2 + foo")
                .equalsObject(3);
        List<Object> list = new Match()
                .def("foo", "[1, 2]")
                .asList("foo");
        Match.equals(list, "[1, 2]");
        Match.contains(list, "[1, 2]");
        Match.contains(list, "[1]");
    }

    @Test
    public void testJson() {
        new Match("{ a: 'foo', b: 1 }").equals("{ b: 1, a: 'foo' }");
        new Match("[{ a: 'foo', b: 1 }, { a: 'bar', b: 2 }]").equals("[{ b: 1, a: 'foo' }, { b: 2, a: 'bar' }]");
    }

    @Test
    public void testXml() {
        new Match("<foo>a</foo>").equals("<foo>a</foo>");
        new Match("<foo>a</foo>").asMap().get("foo").equals("a");
    }

    @Test
    public void testXmlNamespaced() {
        new Match("<foo:cat xmlns:foo=\"foobar\">a</foo:cat>").equals("<bar:cat xmlns:bar=\"foobar\">a</bar:cat>");
        new Match("<foo:cat xmlns:foo=\"foobar\" xmlns:bar=\"https:github.com\"><bar:dog>a</bar:dog></foo:cat>")
                .equals("<foo2:cat xmlns:foo2=\"foobar\" xmlns:bar2=\"https:github.com\"><bar2:dog>a</bar2:dog></foo2:cat>");

    }

    @Test
    public void testString() {
        Match.equalsText("foo", "foo");
        Match.containsText("foo", "foo");
        Match.containsText("foobar", "oob");
    }

}
