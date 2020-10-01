package com.intuit.karate.runtime;

import com.intuit.karate.AssignType;
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
    }

    @Test
    void testEmbeddedXml() {

    }

    @Test
    void testEvalXml() {
        engine.assign(AssignType.AUTO, "myXml", "<root><foo>bar</foo><hello>world</hello></root>");
        Variable myXml = engine.vars.get("myXml");
        assertTrue(myXml.isXml());
        Variable temp = engine.eval("myXml.root.foo");
        assertEquals("bar", temp.getValue());
    }
    
    @Test
    void testEvalJson() {
        engine.assign(AssignType.AUTO, "myJson", "{ foo: 'bar', baz: [1, 2], ban: { hello: 'world' } }");
        Variable myXml = engine.vars.get("myJson");
        assertTrue(myXml.isMap());
        Variable value = engine.eval("myJson.foo");
        assertEquals("bar", value.getValue());
        value = engine.eval("myJson.baz[1]");
        assertEquals(2, value.<Number>getValue());
        value = engine.eval("myJson.ban.hello");
        assertEquals("world", value.getValue());
    }    

}
