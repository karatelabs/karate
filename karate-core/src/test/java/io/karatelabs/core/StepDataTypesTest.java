/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class StepDataTypesTest {

    @Test
    void testDefNumber() {
        ScenarioRuntime sr = run("""
            * def a = 1 + 2
            """);
        assertPassed(sr);
        assertEquals(3, get(sr, "a"));
    }

    @Test
    void testDefString() {
        ScenarioRuntime sr = run("""
            * def name = 'hello'
            """);
        assertPassed(sr);
        assertEquals("hello", get(sr, "name"));
    }

    @Test
    void testDefJson() {
        ScenarioRuntime sr = run("""
            * def foo = { name: 'bar' }
            """);
        assertPassed(sr);
        matchVar(sr, "foo", Map.of("name", "bar"));
    }

    @Test
    void testDefArray() {
        ScenarioRuntime sr = run("""
            * def arr = [1, 2, 3]
            """);
        assertPassed(sr);
        matchVar(sr, "arr", List.of(1, 2, 3));
    }

    @Test
    void testDefNestedJson() {
        ScenarioRuntime sr = run("""
            * def data = { user: { name: 'john', age: 30 } }
            """);
        assertPassed(sr);
        Object data = get(sr, "data");
        assertInstanceOf(Map.class, data);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) data;
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) map.get("user");
        assertEquals("john", user.get("name"));
        assertEquals(30, user.get("age"));
    }

    @Test
    void testDefWithExpression() {
        ScenarioRuntime sr = run("""
            * def x = 10
            * def y = x * 2
            """);
        assertPassed(sr);
        assertEquals(20, get(sr, "y"));
    }

    @Test
    void testSetNested() {
        ScenarioRuntime sr = run("""
            * def foo = { a: 1 }
            * set foo.b = 2
            """);
        assertPassed(sr);
        matchVar(sr, "foo", Map.of("a", 1, "b", 2));
    }

    @Test
    void testSetArrayIndex() {
        ScenarioRuntime sr = run("""
            * def arr = [1, 2, 3]
            * set arr[1] = 99
            """);
        assertPassed(sr);
        matchVar(sr, "arr", List.of(1, 99, 3));
    }

    @Test
    void testRemove() {
        ScenarioRuntime sr = run("""
            * def foo = { a: 1, b: 2 }
            * remove foo.b
            """);
        assertPassed(sr);
        matchVar(sr, "foo", Map.of("a", 1));
    }

    // ========== JsonPath wildcard for set / remove ==========
    // V1 routed any non-empty path after the variable through Jayway; v2's `set` / `remove`
    // delegate to JS, so `[*]` / `..` / `[?(...)]` would hit the JS parser as syntax errors.
    // The keyword now detects JsonPath constructs and falls back to Jayway.

    @Test
    void testSetWildcardAddsKeyToEachElement() {
        ScenarioRuntime sr = run("""
            * def body = [ { name: 'name1', value: true }, { name: 'name2', value: false } ]
            * set body[*].parent = 'test'
            * match each body == { name: '#string', value: '#boolean', parent: 'test' }
            """);
        assertPassed(sr);
    }

    @Test
    void testSetWildcardReplacesExistingKey() {
        ScenarioRuntime sr = run("""
            * def body = [ { a: 1, b: 2 }, { a: 3, b: 4 } ]
            * set body[*].a = 99
            * match body == [ { a: 99, b: 2 }, { a: 99, b: 4 } ]
            """);
        assertPassed(sr);
    }

    @Test
    void testRemoveWildcardDropsKeyFromEachElement() {
        ScenarioRuntime sr = run("""
            * def body = [ { name: 'name1', value: true }, { name: 'name2', value: false } ]
            * remove body[*].value
            * match each body == { name: '#string' }
            """);
        assertPassed(sr);
    }

    @Test
    void testSetWildcardOnNestedArray() {
        ScenarioRuntime sr = run("""
            * def root = { items: [ { id: 1 }, { id: 2 } ] }
            * set root.items[*].flag = true
            * match root == { items: [ { id: 1, flag: true }, { id: 2, flag: true } ] }
            """);
        assertPassed(sr);
    }

    @Test
    void testRemoveRecursiveDescent() {
        // `..` is the JsonPath recursive-descent operator — also can't be parsed as JS.
        ScenarioRuntime sr = run("""
            * def doc = { a: { secret: 1, keep: 2 }, b: { secret: 3, keep: 4 } }
            * remove doc..secret
            * match doc == { a: { keep: 2 }, b: { keep: 4 } }
            """);
        assertPassed(sr);
    }

    // ========== nested set with auto-vivified intermediate paths ==========
    // V1 always routed `set var.path = expr` through Jayway, which auto-creates
    // intermediate objects. v2 had been delegating to JS, where `obj.foo.bar = x`
    // throws TypeError when `obj.foo` is undefined. The fix re-routes any "pure"
    // JsonPath LHS through Jayway. The v2-idiomatic alternative is
    // to build the structure with a single `def` literal — `set` is mainly kept
    // for v1 compatibility (and remains the way to set XML via xpath).

    @Test
    void testSetAutoVivifyIntermediatePaths() {
        ScenarioRuntime sr = run("""
            * def reqPayload = {}
            * def short_id = 'ABC123'
            * set reqPayload.organization.name = 'Test ' + short_id
            * match reqPayload == { organization: { name: 'Test ABC123' } }
            """);
        assertPassed(sr);
    }

    @Test
    void testSetAutoVivifyDeeplyNested() {
        ScenarioRuntime sr = run("""
            * def foo = {}
            * set foo.a.b.c = 5
            * match foo == { a: { b: { c: 5 } } }
            """);
        assertPassed(sr);
    }

    @Test
    void testSetWithStringConcatenationRhs() {
        // Original repro: parser used to fail with "MATH_ADD_EXPR is not a valid
        // assignment target" in 2.0.7 when the RHS was a string concatenation.
        // The parser fix landed earlier; this test guards both the parse and the
        // auto-vivify behavior together.
        ScenarioRuntime sr = run("""
            * def reqPayload = { organization: {} }
            * def short_id = 'ABC123'
            * set reqPayload.organization.name = 'Test ' + short_id
            * match reqPayload.organization.name == 'Test ABC123'
            """);
        assertPassed(sr);
    }

    @Test
    void testSetDynamicIndexStillRoutesThroughJs() {
        // Negative guard: a JS-style dynamic index (`arr[i]` where `i` is a runtime
        // identifier, not a literal) is NOT pure JsonPath and must continue to route
        // through the JS engine. Jayway can't resolve `i` to its value.
        ScenarioRuntime sr = run("""
            * def arr = [10, 20, 30]
            * def i = 1
            * set arr[i] = 99
            * match arr == [10, 99, 30]
            """);
        assertPassed(sr);
    }

    // ========== Hyphenated keys in a dot-notation set / remove LHS ==========
    // V1 routed `set var.path` through Jayway, which tolerates hyphenated keys.
    // v2 < 2.0.10 delegated dot paths to JS, where `obj.hyphen-key` is read as
    // subtraction (`obj.hyphen - key`) and throws "MATH_ADD_EXPR is not a valid
    // assignment target". `isPureJsonPath` now accepts '-' inside a dot segment so
    // these route through Jayway. (Bracket notation `set obj['hyphen-key']` — the
    // unambiguous, recommended form — was the separate fix in #2886.)

    @Test
    void testSetDotNotationHyphenatedKey() {
        ScenarioRuntime sr = run("""
            * def obj = { name: 'original', 'hyphen-key': 'default' }
            * set obj.hyphen-key = 'updated'
            * match obj['hyphen-key'] == 'updated'
            """);
        assertPassed(sr);
    }

    @Test
    void testSetDotNotationHyphenatedKeyNested() {
        ScenarioRuntime sr = run("""
            * def headers = { auth: { 'x-custom-header': 'original' } }
            * set headers.auth.x-custom-header = 'updated'
            * match headers.auth['x-custom-header'] == 'updated'
            """);
        assertPassed(sr);
    }

    @Test
    void testSetDotNotationHyphenInIntermediateSegment() {
        // Jayway resolves the hyphen at any path position, not just the leaf.
        ScenarioRuntime sr = run("""
            * def doc = { 'a-b': { c: 1 } }
            * set doc.a-b.c = 99
            * match doc['a-b'].c == 99
            """);
        assertPassed(sr);
    }

    @Test
    void testSetDotNotationHyphenatedKeyAutoVivify() {
        ScenarioRuntime sr = run("""
            * def obj = {}
            * set obj.new-key = 'v'
            * match obj['new-key'] == 'v'
            """);
        assertPassed(sr);
    }

    @Test
    void testRemoveDotNotationHyphenatedKey() {
        // `remove` shares the same JsonPath LHS routing; without the fix it hit the
        // `delete obj.hyphen-key` JS fallback and silently no-op'd.
        ScenarioRuntime sr = run("""
            * def obj = { 'hyphen-key': 1, keep: 2 }
            * remove obj.hyphen-key
            * match obj == { keep: 2 }
            """);
        assertPassed(sr);
    }

    @Test
    void testCopy() {
        ScenarioRuntime sr = run("""
            * def original = { a: 1, b: { c: 2 } }
            * copy clone = original
            * set clone.b.c = 99
            """);
        assertPassed(sr);
        // Original should be unchanged
        @SuppressWarnings("unchecked")
        Map<String, Object> original = (Map<String, Object>) get(sr, "original");
        @SuppressWarnings("unchecked")
        Map<String, Object> originalB = (Map<String, Object>) original.get("b");
        assertEquals(2, originalB.get("c"));

        // Clone should have new value
        @SuppressWarnings("unchecked")
        Map<String, Object> clone = (Map<String, Object>) get(sr, "clone");
        @SuppressWarnings("unchecked")
        Map<String, Object> cloneB = (Map<String, Object>) clone.get("b");
        assertEquals(99, cloneB.get("c"));
    }

    @Test
    void testText() {
        ScenarioRuntime sr = run("""
            * text myText =
            \"\"\"
            hello world
            this is multi-line
            \"\"\"
            """);
        assertPassed(sr);
        String text = (String) get(sr, "myText");
        assertTrue(text.contains("hello world"));
        assertTrue(text.contains("this is multi-line"));
    }

    @Test
    void testDocStringIndentation() {
        // Docstring indentation should be normalized based on the first line's margin
        ScenarioRuntime sr = run("""
            * text myText =
              \"\"\"
              line one
                indented line
              line three
              \"\"\"
            """);
        assertPassed(sr);
        String text = (String) get(sr, "myText");
        // First line sets the margin, indented line preserves relative indentation
        assertTrue(text.startsWith("line one"));
        assertTrue(text.contains("  indented line")); // 2 spaces relative to margin
        assertTrue(text.contains("line three"));
    }

    @Test
    void testJson() {
        ScenarioRuntime sr = run("""
            * json myJson = { name: 'test', value: 123 }
            """);
        assertPassed(sr);
        matchVar(sr, "myJson", Map.of("name", "test", "value", 123));
    }

    @Test
    void testDefNull() {
        ScenarioRuntime sr = run("""
            * def x = null
            """);
        assertPassed(sr);
        assertNull(get(sr, "x"));
    }

    @Test
    void testDefBoolean() {
        ScenarioRuntime sr = run("""
            * def t = true
            * def f = false
            """);
        assertPassed(sr);
        assertEquals(true, get(sr, "t"));
        assertEquals(false, get(sr, "f"));
    }

    @Test
    void testReplace() {
        // Replace <token> with value in a string variable
        ScenarioRuntime sr = run("""
            * def text = 'hello <name> world'
            * replace text.name = 'foo'
            * match text == 'hello foo world'
            """);
        assertPassed(sr);
        assertEquals("hello foo world", get(sr, "text"));
    }

    @Test
    void testReplaceMultipleTokens() {
        ScenarioRuntime sr = run("""
            * def text = '<greeting> <name>!'
            * replace text.greeting = 'Hello'
            * replace text.name = 'World'
            * match text == 'Hello World!'
            """);
        assertPassed(sr);
    }

    @Test
    void testReplaceWithTable() {
        // Replace multiple tokens using table syntax
        ScenarioRuntime sr = run("""
            * def text = 'hello <one> world <two> bye'
            * replace text
              | token | value   |
              | one   | 'cruel' |
              | two   | 'good'  |
            * match text == 'hello cruel world good bye'
            """);
        assertPassed(sr);
        assertEquals("hello cruel world good bye", get(sr, "text"));
    }

    @Test
    void testReplaceWithTableExpressions() {
        // Replace with table where values are expressions
        ScenarioRuntime sr = run("""
            * def text = 'hello <one> world <two> bye'
            * def first = 'cruel'
            * def second = 'good'
            * replace text
              | token | value  |
              | one   | first  |
              | two   | second |
            * match text == 'hello cruel world good bye'
            """);
        assertPassed(sr);
    }

    @Test
    void testReplaceWithTableCustomPlaceholders() {
        // Replace with custom placeholder syntax (not <...>)
        ScenarioRuntime sr = run("""
            * def text = 'hello ${one} world @@two@@ bye'
            * replace text
              | token   | value   |
              | ${one}  | 'cruel' |
              | @@two@@ | 'good'  |
            * match text == 'hello cruel world good bye'
            """);
        assertPassed(sr);
    }

    @Test
    void testReplaceWithJsonObject() {
        // Replace tokens in a JSON object, then convert back to JSON
        ScenarioRuntime sr = run("""
            * def data = { foo: '<foo>', bar: { hello: '<bar>'} }
            * replace data
              | token | value |
              | foo   | 'one' |
              | bar   | 'two' |
            * json data = data
            * match data == { foo: 'one', bar: { hello: 'two' } }
            """);
        assertPassed(sr);
    }

    @Test
    void testCsvKeywordWithDocString() {
        // csv keyword with doc string
        ScenarioRuntime sr = run("""
            * csv data =
            \"\"\"
            first,second
            a1,a2
            b1,b2
            \"\"\"
            * match data == [{ first: 'a1', second: 'a2'}, { first: 'b1', second: 'b2' }]
            """);
        assertPassed(sr);
    }

    @Test
    void testCsvKeywordWithVariable() {
        // csv keyword with variable reference
        ScenarioRuntime sr = run("""
            * text foo =
            \"\"\"
            name,type
            Billie,LOL
            Bob,Wild
            \"\"\"
            * csv bar = foo
            * match bar == [{ name: 'Billie', type: 'LOL' }, { name: 'Bob', type: 'Wild' }]
            """);
        assertPassed(sr);
    }

    @Test
    void testCsvKeywordWithByteArray() {
        // csv keyword with byte array reference
        ScenarioRuntime sr = run("""
            * def csvBytes = "name,age\\nJane,29".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            * csv info = csvBytes
            * match info == [{ name: 'Jane', age: '29' }]
            """);
        assertPassed(sr);
    }

    @Test
    void testYamlKeywordWithDocString() {
        // yaml keyword with doc string
        ScenarioRuntime sr = run("""
            * yaml data =
            \"\"\"
            name: John
            age: 30
            \"\"\"
            * match data == { name: 'John', age: 30 }
            """);
        assertPassed(sr);
    }

    @Test
    void testYamlNestedStructure() {
        // yaml keyword with nested objects
        ScenarioRuntime sr = run("""
            * yaml data =
            \"\"\"
            name: John
            input:
              id: 1
              subType:
                name: Smith
                deleted: false
            \"\"\"
            * match data == { name: 'John', input: { id: 1, subType: { name: 'Smith', deleted: false } } }
            """);
        assertPassed(sr);
    }

    @Test
    void testPropertyAssignmentWithDocString() {
        // Property assignment with docstring value (e.g., foo.bar = """json""")
        ScenarioRuntime sr = run("""
            * def foo = { bar: 'one' }
            * foo.bar =
            \"\"\"
            {
              some: 'big',
              message: 'content'
            }
            \"\"\"
            * match foo == { bar: { some: 'big', message: 'content' } }
            """);
        assertPassed(sr);
    }

    // ========== String Keyword ==========

    @Test
    void testStringKeyword() {
        // string keyword converts JSON to string
        ScenarioRuntime sr = run("""
            * def json = { 'sp ace': 'foo', 'hy-phen': 'bar', 'full.stop': 'baz' }
            * string jsonString = json
            * match jsonString == '{"sp ace":"foo","hy-phen":"bar","full.stop":"baz"}'
            """);
        assertPassed(sr);
    }

    @Test
    void testJsonPathOnStringAutoConvert() {
        // V1 compatibility: JSONPath on a string should auto-convert if it looks like JSON
        ScenarioRuntime sr = run("""
            * def response = "{ foo: { hello: 'world' } }"
            * def foo = $.foo
            * match foo == { hello: 'world' }
            """);
        assertPassed(sr);
    }

    // ========== def var = $.path on a missing JSONPath ==========
    // V1 parity: `def x = $.missing` assigns #notpresent (no error) when the path
    // doesn't exist, so a value that may-or-may-not be present can be read and then
    // conditionally acted on. v2 routed the def/assignment `$` path straight through
    // Jayway, which threw "No results for path" / "Missing property in path". The match
    // side already degraded to #notpresent; the def side now matches it.

    @Test
    void testDefDollarMissingLeafReturnsNotPresent() {
        ScenarioRuntime sr = run("""
            * def response = { data: { name: 'test' } }
            * def missing = $.data.nonExistent
            * match missing == '#notpresent'
            """);
        assertPassed(sr);
    }

    @Test
    void testDefDollarMissingIntermediateReturnsNotPresent() {
        // Intermediate segment absent — Jayway raises "Missing property in path"
        // (also a PathNotFoundException), which must degrade to #notpresent too.
        ScenarioRuntime sr = run("""
            * def response = { name: 'test' }
            * def deep = $.deeply.nested.path
            * match deep == '#notpresent'
            """);
        assertPassed(sr);
    }

    @Test
    void testDefDollarNamedVarMissingPathReturnsNotPresent() {
        // The $varname.path form (not just the bare $. on response) degrades too.
        ScenarioRuntime sr = run("""
            * def foo = { a: 1 }
            * def missing = $foo.b
            * match missing == '#notpresent'
            """);
        assertPassed(sr);
    }

    @Test
    void testDefDollarExistingPathStillResolves() {
        // Control: a present path resolves to its value, unchanged.
        ScenarioRuntime sr = run("""
            * def response = { data: { name: 'test' } }
            * def name = $.data.name
            * match name == 'test'
            """);
        assertPassed(sr);
    }

    // ========== Variable Name Validation ==========

    @Test
    void testValidVariableNames() {
        // V2 allows variable names starting with letter or underscore
        // Note: V1 only allowed letter as first character, V2 is more permissive
        ScenarioRuntime sr = run("""
            * def foo = 1
            * def foo_bar = 2
            * def foo_ = 3
            * def foo1 = 4
            * def a = 5
            * def a1 = 6
            * def _foo = 7
            * def _foo_ = 8
            """);
        assertPassed(sr);
        assertEquals(1, get(sr, "foo"));
        assertEquals(2, get(sr, "foo_bar"));
        assertEquals(3, get(sr, "foo_"));
        assertEquals(4, get(sr, "foo1"));
        assertEquals(5, get(sr, "a"));
        assertEquals(6, get(sr, "a1"));
        assertEquals(7, get(sr, "_foo"));
        assertEquals(8, get(sr, "_foo_"));
    }

    @Test
    void testInvalidVariableNameLeadingDigit() {
        // Variable names cannot start with a digit
        ScenarioRuntime sr = run("""
            * def 2foo = 1
            """);
        assertFailed(sr);
    }

    @Test
    void testInvalidVariableNameReservedKarate() {
        // 'karate' is a reserved variable name
        ScenarioRuntime sr = run("""
            * def karate = 1
            """);
        assertFailed(sr);
    }

    // ========== Doc-String JSON with Embedded Expressions ==========
    // V2 restores v1 behavior: anything starting with { or [ on the RHS of `def`
    // is parsed by Karate's relaxed JSON parser (which accepts #(expr) tokens),
    // not by the JS engine. To force JS / ES6 evaluation, wrap in parens.

    @Test
    void testDocStringJsonUnquotedEmbeddedExpr() {
        // works in v1, regressed in v2.0.5,
        // restored here by routing { ... } through Karate's JSON parser first
        ScenarioRuntime sr = run("""
            * def id = 123
            * def payload =
            \"\"\"
            {
              "id": #(id),
              "name": "sample"
            }
            \"\"\"
            * match payload == { id: 123, name: 'sample' }
            """);
        assertPassed(sr);
    }

    @Test
    void testDocStringJsonQuotedEmbeddedExprWorks() {
        // Workaround 1: quote the embedded expression — string value "#(id)"
        // is post-processed to substitute the variable
        ScenarioRuntime sr = run("""
            * def id = 123
            * def payload =
            \"\"\"
            {
              "id": "#(id)",
              "name": "sample"
            }
            \"\"\"
            * match payload == { id: 123, name: 'sample' }
            """);
        assertPassed(sr);
    }

    @Test
    void testDocStringJsonShorthandProperty() {
        // Workaround 2: ES6 shorthand — { id } is equivalent to { id: id }
        ScenarioRuntime sr = run("""
            * def id = 123
            * def name = 'sample'
            * def payload =
            \"\"\"
            { id, name }
            \"\"\"
            * match payload == { id: 123, name: 'sample' }
            """);
        assertPassed(sr);
    }

    @Test
    void testDocStringJsonExplicitReferenceParenWrapped() {
        // Workaround 3: paren-wrap the body to force JS evaluation (def parses
        // a leading { as Karate JSON by default, so a bare `id` would otherwise
        // be read as the string "id")
        ScenarioRuntime sr = run("""
            * def id = 123
            * def payload =
            \"\"\"
            ({ id: id, name: 'sample' })
            \"\"\"
            * match payload == { id: 123, name: 'sample' }
            """);
        assertPassed(sr);
    }

    // The same shorthand / paren-wrap forms also work in plain inline JSON
    // (not just doc-strings) — useful any time you want an unquoted value without #(...)

    @Test
    void testDefInlineJsonShorthandProperty() {
        // ES6 shorthand — { id, name } is not valid JSON so it falls through to JS
        ScenarioRuntime sr = run("""
            * def id = 123
            * def name = 'sample'
            * def payload = { id, name }
            * match payload == { id: 123, name: 'sample' }
            """);
        assertPassed(sr);
    }

    @Test
    void testDefInlineJsonExplicitReferenceTreatedAsString() {
        // GOTCHA: a bare `id` on the value side of a def JSON literal is read
        // as the STRING "id" by Karate's relaxed JSON parser (v1 behavior).
        // Use '#(id)', ES6 shorthand { id }, or paren-wrap to force JS.
        ScenarioRuntime sr = run("""
            * def id = 123
            * def payload = { id: id, name: 'sample' }
            * match payload == { id: 'id', name: 'sample' }
            """);
        assertPassed(sr);
    }

    @Test
    void testDefInlineJsonParenWrappedExplicitReference() {
        // Paren-wrap forces JS evaluation
        ScenarioRuntime sr = run("""
            * def id = 123
            * def payload = ({ id: id, name: 'sample' })
            * match payload == { id: 123, name: 'sample' }
            """);
        assertPassed(sr);
    }

    // ========== Hyphenated Keys in Inline Maps ==========
    // V1's relaxed JSON parser accepted bare hyphenated keys like Content-Type.
    // V2 < 2.0.6 evaluated as JS where `Content-Type` was parsed as subtraction.
    // Fixed by routing { ... } through Karate's JSON parser first.

    @Test
    void testInlineMapWithHyphenatedKeys() {
        ScenarioRuntime sr = run("""
            * def headers = { Accept: 'application/json', Content-Type: 'application/json', Idempotency-Key: 'abc-123' }
            * match headers['Content-Type'] == 'application/json'
            * match headers['Idempotency-Key'] == 'abc-123'
            """);
        assertPassed(sr);
    }

    @Test
    void testInlineMapWithHyphenatedKeysAndEmbeddedExpr() {
        // Hyphenated keys with #(...) value substitution
        ScenarioRuntime sr = run("""
            * def contentType = 'application/json'
            * def headers = { Accept: 'application/json', Content-Type: '#(contentType)', Idempotency-Key: 'abc-123' }
            * match headers['Content-Type'] == 'application/json'
            * match headers['Idempotency-Key'] == 'abc-123'
            """);
        assertPassed(sr);
    }

    // ========== Lazy #(...) resolution at def time ==========
    // V1 behavior: `#(varName)` in a JSON literal at `def` time tolerated an
    // undefined variable — the placeholder string was preserved and resolved
    // later at match time. Common pattern for shared schemas-as-templates,
    // where the template is loaded ahead of the variable being defined.

    @Test
    void testDefSchemaTemplateWithUndefinedVarDoesNotThrow() {
        // undefined `idCheck` at def time
        // must not throw; the schema is stored as a template and resolved later.
        ScenarioRuntime sr = run("""
            * def MySchema =
            \"\"\"
            {
              id: '#(idCheck)',
              name: '#string'
            }
            \"\"\"
            """);
        assertPassed(sr);
        // Placeholder is preserved verbatim for the match engine to resolve later.
        Map<String, Object> schema = (Map<String, Object>) get(sr, "MySchema");
        assertEquals("#(idCheck)", schema.get("id"));
        assertEquals("#string", schema.get("name"));
    }

    // (End-to-end: deferred placeholder resolved at match time — see
    // StepMatchTest.testMatchSchemaTemplateWithDeferredEmbeddedExpr)

    @Test
    void testNestedSchemaTemplateWithUndefinedVar() {
        // Undefined-var deferral works through nested maps and lists too.
        ScenarioRuntime sr = run("""
            * def MySchema =
            \"\"\"
            {
              user: { id: '#(idCheck)', name: '#string' },
              tags: ['#(tagCheck)']
            }
            \"\"\"
            """);
        assertPassed(sr);
        Map<String, Object> schema = (Map<String, Object>) get(sr, "MySchema");
        Map<String, Object> user = (Map<String, Object>) schema.get("user");
        assertEquals("#(idCheck)", user.get("id"));
        List<Object> tags = (List<Object>) schema.get("tags");
        assertEquals("#(tagCheck)", tags.get(0));
    }

    @Test
    void testDefSchemaTemplateNestedAccessUndefinedRoot() {
        // #(foo.bar) where `foo` itself is undefined → eval throws → placeholder preserved.
        ScenarioRuntime sr = run("""
            * def MySchema =
            \"\"\"
            { val: '#(foo.bar)' }
            \"\"\"
            """);
        assertPassed(sr);
        Map<String, Object> schema = (Map<String, Object>) get(sr, "MySchema");
        assertEquals("#(foo.bar)", schema.get("val"));
    }

    @Test
    void testDefSchemaTemplateNestedAccessDefinedRootMissingProp() {
        // #(foo.bar) where `foo = {}` → no error → JS undefined.
        // Documents (not asserts ideal) the boundary of the deferral: there is no
        // exception to swallow, so the value resolves eagerly to undefined → null.
        ScenarioRuntime sr = run("""
            * def foo = ({})
            * def MySchema =
            \"\"\"
            { val: '#(foo.bar)' }
            \"\"\"
            """);
        assertPassed(sr);
        Map<String, Object> schema = (Map<String, Object>) get(sr, "MySchema");
        assertEquals(null, schema.get("val"));
    }

    @Test
    void testDefSchemaTemplateNullChainDeferrsViaTypeError() {
        // #(foo.bar) where `foo = null` → TypeError → placeholder preserved (full v1 parity).
        ScenarioRuntime sr = run("""
            * def foo = null
            * def MySchema =
            \"\"\"
            { val: '#(foo.bar)' }
            \"\"\"
            """);
        assertPassed(sr);
        Map<String, Object> schema = (Map<String, Object>) get(sr, "MySchema");
        assertEquals("#(foo.bar)", schema.get("val"));
    }

    @Test
    void testDefSchemaTemplateTernaryWithUndefinedVar() {
        // Embedded expressions can be arbitrary JS (#(a ? 'b' : 'c'), calls, etc.).
        // If eval fails for any reason at def time, the placeholder is preserved
        // and the match engine evaluates it later.
        ScenarioRuntime sr = run("""
            * def MySchema =
            \"\"\"
            { val: '#(flag ? "yes" : "no")' }
            \"\"\"
            """);
        assertPassed(sr);
        Map<String, Object> schema = (Map<String, Object>) get(sr, "MySchema");
        assertEquals("#(flag ? \"yes\" : \"no\")", schema.get("val"));
    }

    @Test
    void testDefinedVarStillSubstitutesEagerly() {
        // Sanity: the deferral only kicks in for ReferenceError. When the
        // variable IS in scope, `#(varName)` substitutes eagerly as before.
        ScenarioRuntime sr = run("""
            * def idCheck = '#number'
            * def MySchema =
            \"\"\"
            {
              id: '#(idCheck)',
              name: '#string'
            }
            \"\"\"
            """);
        assertPassed(sr);
        Map<String, Object> schema = (Map<String, Object>) get(sr, "MySchema");
        assertEquals("#number", schema.get("id"));
    }

}
