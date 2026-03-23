package io.karatelabs.match;

import io.karatelabs.common.Json;
import io.karatelabs.js.Engine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OperationTest {

    static Value value(Object o) {
        Object temp = Value.parseIfJsonOrXmlString(o);
        return new Value(temp);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "EQUALS;EACH_EQUALS",
            "CONTAINS;EACH_CONTAINS",
            "CONTAINS_DEEP;EACH_CONTAINS_DEEP"}, delimiter = ';')
    void testSchema(String matchType, String matchEachType) {
        Json json = Json.of("{ a: '#number' }");
        Map<String, Object> map = json.asMap();
        Result mr = Match.evaluate("[{ a: 1 }, { a: 2 }]", null, null).is(Match.Type.valueOf(matchEachType), map);
        assertTrue(mr.pass);
        Engine engine = new Engine();
        engine.put("schema", map);
        Operation operation = new Operation(engine, Match.Type.valueOf(matchType), value("[{ a: 1 }, { a: 2 }]"), value("#[] schema"));
        assertTrue(operation.execute());
        operation = new Operation(engine, Match.Type.valueOf(matchType), value("{ a: 'x', b: { c: 'y' } }"), value("{ a: '#string', b: { c: '#string' } }"));
        assertTrue(operation.execute());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EQUALS", "CONTAINS", "CONTAINS_DEEP"})
    void testSchemaOptionalObject(String matchType) {
        Json part = Json.of("{ bar: '#string' }");
        Engine engine = new Engine();
        engine.put("part", part.asMap());
        Operation operation = new Operation(engine, Match.Type.valueOf(matchType), value("{ foo: null }"), value("{ foo: '##(part)' }"));
        assertTrue(operation.execute());
        operation = new Operation(engine, Match.Type.valueOf(matchType), value("{ foo: { bar: 'baz' } }"), value("{ foo: '##(part)' }"));
        assertTrue(operation.execute());
    }

    @Test
    void testIssue2515() {
        String cat = """
                {
                  name: 'Billie',
                  kittens: [
                    { id: 23, name: 'Bob', bla: [{ b: '1'}] },
                    { id: 42, name: 'Wild' }
                  ]
                }
                """;
        Json expectedKittens1 = Json.of("[{ id: 42, name: 'Wild' }, { id: 23, name: 'Bob', bla: [{ b: '1' }] }]");
        Engine engine = new Engine();
        engine.put("expectedKittens1", expectedKittens1.asList());
        Operation operation = new Operation(engine, Match.Type.EQUALS, value(cat), value("{ name: 'Billie', kittens: '#(^^expectedKittens1)' }"));
        assertTrue(operation.execute());
        Json expectedKittens2 = Json.of("[{ id: 42, name: 'Wild' }, { id: 23, name: 'Bob', bla: { b: '1' } }]");
        engine.put("expectedKittens2", expectedKittens2.asList());
        operation = new Operation(engine, Match.Type.EQUALS, value(cat), value("{ name: 'Billie', kittens: '#(^^expectedKittens2)' }"));
        assertFalse(operation.execute());
    }

    @Test
    void testIssue2727() {
        String response = "[ { a: 1, b: [ { c: 3, d: 4 } ] } ]";
        Json pattern1 = Json.of("{ a: 1, b: [ { c: 3 } ] }");
        Engine engine = new Engine();
        engine.put("pattern1", pattern1.asMap());
        Operation operation = new Operation(engine, Match.Type.CONTAINS, value(response), value("#(^+pattern1)"));
        assertTrue(operation.execute());
        Json pattern2 = Json.of("{ c: 3 }");
        engine.put("pattern2", pattern2.asMap());
        operation = new Operation(engine, Match.Type.CONTAINS, value(response), value("{ a: 1, b: [ '#(^pattern2)' ] }"));
        assertTrue(operation.execute());
    }

    @Test
    void testArrayContainsPartialMatch() {
        // Test case from schema-like.feature: array contains element that contains partial object
        String actual = "[ { a: 1, b: 'x' }, { a: 2, b: 'y' } ]";
        Json part = Json.of("{ a: 1 }");
        Engine engine = new Engine();
        engine.put("part", part.asMap());
        // #(^part) means: array contains an element that contains { a: 1 }
        Operation operation = new Operation(engine, Match.Type.CONTAINS, value(actual), value("#(^part)"));
        assertTrue(operation.execute(), "array should contain element that contains { a: 1 }");
        // Also test with ^+ (contains deep) for comparison
        operation = new Operation(engine, Match.Type.CONTAINS, value(actual), value("#(^+part)"));
        assertTrue(operation.execute(), "array should contain element that contains deep { a: 1 }");
    }

    @Test
    void testArrayContainsAnyPartialMatch() {
        // Test case from schema-like.feature: array contains element that contains any of partial object
        String actual = "[ { a: 1, b: 'x' }, { a: 2, b: 'y' } ]";
        // mix has b: 'y' (matches second element) and c: true (matches none)
        Json mix = Json.of("{ b: 'y', c: true }");
        Engine engine = new Engine();
        engine.put("mix", mix.asMap());
        // #(^*mix) means: array contains an element that contains any of mix's keys
        Operation operation = new Operation(engine, Match.Type.CONTAINS, value(actual), value("#(^*mix)"));
        assertTrue(operation.execute(), "array should contain element that contains any of { b: 'y', c: true }");
    }

}
