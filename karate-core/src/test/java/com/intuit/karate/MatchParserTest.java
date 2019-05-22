package com.intuit.karate;

import com.intuit.karate.core.MatchStep;
import com.intuit.karate.core.MatchType;
import org.junit.Test;

import static com.intuit.karate.core.MatchType.*;
import static org.junit.Assert.assertEquals;

public class MatchParserTest {

    @Test
    public void testMatchStep() {
        test("hello ==", EQUALS, "hello", null, null);
        test("hello world == foo", EQUALS, "hello", "world", "foo");
        test("each hello world == foo", EACH_EQUALS, "hello", "world", "foo");
        test("hello.foo(bar) != blah", NOT_EQUALS, "hello.foo(bar)", null, "blah");
        test("foo count(/records//record) contains any blah", CONTAINS_ANY, "foo", "count(/records//record)", "blah");
        test("__arg == karate.get('foos[' + __loop + ']')", EQUALS, "__arg", null, "karate.get('foos[' + __loop + ']')");
        test("response $[?(@.b=='ab')] == '#[1]'", EQUALS, "response", "$[?(@.b=='ab')]", "'#[1]'");
        test("test != '#? _.length == 2'", NOT_EQUALS, "test", null, "'#? _.length == 2'");
        test("actual[0] !contains badSchema", NOT_CONTAINS, "actual[0]", null, "badSchema");
        test("actual[0] contains badSchema", CONTAINS, "actual[0]", null, "badSchema");
        test("driver.eval('{ foo: \"bar\" }') == { hello: 'world' }", EQUALS, "driver.eval('{ foo: \"bar\" }')", null, "{ hello: 'world' }");
        test("response.integration.serviceData['Usage Data'][0].Stage ==", EQUALS, "response.integration.serviceData['Usage Data'][0].Stage", null, null);
        test("response contains { foo: 'a any b' }", CONTAINS, "response", null, "{ foo: 'a any b' }");
        test("response.foo == 'a contains b'", EQUALS, "response.foo", null, "'a contains b'");
        test("$.addOns[?(@.entitlementStateChangeReason=='RESUBSCRIBE')].addOnOfferID contains only toAddOnOfferIDs", CONTAINS_ONLY, "$.addOns[?(@.entitlementStateChangeReason=='RESUBSCRIBE')].addOnOfferID", null, "toAddOnOfferIDs");
    }

    private void test(String raw, MatchType type, String name, String path, String expected) {
        MatchStep step = MatchStep.parse(raw);
        assertEquals(type, step.type);
        assertEquals(expected, step.expected);
        assertEquals(name, step.name);
        assertEquals(path, step.path);
    }


}
