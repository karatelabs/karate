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
    }
    
}
