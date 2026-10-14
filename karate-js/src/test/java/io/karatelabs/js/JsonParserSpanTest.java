/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class JsonParserSpanTest {

    // -------------------------------------------------------------------------
    // the round-trip invariant over document shapes
    // -------------------------------------------------------------------------

    @Test
    void minified() {
        String t = "{\"a\":1,\"b\":[true,false,null],\"c\":{\"d\":\"x\"}}";
        JsonSpan root = roundTrip(t).root();
        assertEquals(0, root.start());
        assertEquals(t.length(), root.end());
    }

    @Test
    void tabsCrlfAndNoFinalNewline() {
        String t = "{\r\n\t\"a\": 1,\r\n\t\"b\": [\r\n\t\t2\r\n\t]\r\n}";
        JsonSpan root = roundTrip(t).root();
        JsonSpan.Member a = root.members().get(0);
        assertEquals(4, a.keyStart());       // CRLF is two units
        assertEquals(7, a.keyEnd());
        assertEquals("1", a.value().text(t));
    }

    @Test
    void surroundingWhitespaceIsOutsideTheRoot() {
        String t = " \n\t[1, 2] \r\n";
        JsonSpan root = roundTrip(t).root();
        assertEquals("[1, 2]", root.text(t));
    }

    @Test
    void emptyContainers() {
        String t = "{\"o\":{},\"a\":[ ],\"s\":\"\"}";
        JsonSpan root = roundTrip(t).root();
        assertEquals("{}", root.members().get(0).value().text(t));
        assertEquals("[ ]", root.members().get(1).value().text(t));
        assertEquals("\"\"", root.members().get(2).value().text(t));
        assertTrue(root.members().get(0).value().members().isEmpty());
        assertTrue(root.members().get(1).value().elements().isEmpty());
    }

    @Test
    void nested() {
        String t = "[ {\"a\": [ [1], {\"b\": {\"c\": [null]}} ]}, [] ]";
        JsonSpan root = roundTrip(t).root();
        assertEquals(JsonSpan.Kind.ARRAY, root.kind());
        JsonSpan inner = root.elements().get(0).members().get(0).value();
        assertEquals("[ [1], {\"b\": {\"c\": [null]}} ]", inner.text(t));
        JsonSpan c = inner.elements().get(1).members().get(0).value().members().get(0).value();
        assertEquals("[null]", c.text(t));
        assertEquals(JsonSpan.Kind.NULL, c.elements().get(0).kind());
        assertNull(root.elements().get(0).value());
    }

    @Test
    void deeplyNested_500levels() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("{\"a\":[");
        }
        sb.append('1');
        for (int i = 0; i < 500; i++) {
            sb.append("]}");
        }
        String t = sb.toString();
        assertEquals(JsonParser.parse(t), JsonParser.parseSpans(t).root().toValue());
    }

    // -------------------------------------------------------------------------
    // scalars: kinds, raw text and decoded value
    // -------------------------------------------------------------------------

    @Test
    void numberRawText() {
        String t = "[1.0, 1e2, -0, 1E+2, -0.0, 99999999999999999999, 2147483648]";
        List<JsonSpan> els = roundTrip(t).root().elements();
        assertEquals("1.0", els.get(0).text(t));
        assertEquals(1.0, els.get(0).value());
        assertEquals("1e2", els.get(1).text(t));
        assertEquals(100.0, els.get(1).value());
        assertEquals("-0", els.get(2).text(t));
        assertEquals(0, els.get(2).value());
        assertEquals("1E+2", els.get(3).text(t));
        assertEquals("-0.0", els.get(4).text(t));
        assertEquals(-0.0, els.get(4).value());
        assertEquals("99999999999999999999", els.get(5).text(t));
        assertEquals(Long.class, els.get(6).value().getClass());
        for (JsonSpan e : els) {
            assertEquals(JsonSpan.Kind.NUMBER, e.kind());
        }
    }

    @Test
    void literalKinds() {
        String t = "[true,false,null,\"s\",1]";
        List<JsonSpan> els = roundTrip(t).root().elements();
        assertEquals(JsonSpan.Kind.TRUE, els.get(0).kind());
        assertEquals(Boolean.TRUE, els.get(0).value());
        assertEquals(JsonSpan.Kind.FALSE, els.get(1).kind());
        assertEquals(JsonSpan.Kind.NULL, els.get(2).kind());
        assertEquals(JsonSpan.Kind.STRING, els.get(3).kind());
        assertEquals(JsonSpan.Kind.NUMBER, els.get(4).kind());
    }

    @Test
    void scalarRoot() {
        String t = "  \"x\"  ";
        JsonSpan root = roundTrip(t).root();
        assertEquals(2, root.start());
        assertEquals(5, root.end());
        assertEquals("x", root.value());
    }

    @Test
    void escapesStayInTheRawText() {
        String t = "{\"a\\\"b\": \"x\\ny\\\\z\\/\\t\\u00e9\"}";
        JsonSpan.Member m = roundTrip(t).root().members().get(0);
        assertEquals("a\"b", m.key());
        assertEquals("\"a\\\"b\"", t.substring(m.keyStart(), m.keyEnd()));
        assertEquals("\"x\\ny\\\\z\\/\\t\\u00e9\"", m.value().text(t));
        assertEquals("x\ny\\z/\té", m.value().value());
    }

    @Test
    void unicodeEscapeAndSupplementaryOffsets() {
        // the raw U+1F600 is two UTF-16 units, its escaped form twelve
        String t = "{\"\uD83D\uDE00\":\"\\uD83D\\uDE00\",\"é\":[\"\uD83D\uDE00\",1]}";
        JsonSpan root = roundTrip(t).root();
        JsonSpan.Member first = root.members().get(0);
        assertEquals("\uD83D\uDE00", first.key());
        assertEquals(1, first.keyStart());
        assertEquals(5, first.keyEnd());
        assertEquals(6, first.value().start());
        assertEquals(20, first.value().end());
        assertEquals("\uD83D\uDE00", first.value().value());
        JsonSpan.Member second = root.members().get(1);
        assertEquals("é", second.key());
        assertEquals(21, second.keyStart());
        assertEquals(24, second.keyEnd());
        JsonSpan arr = second.value();
        assertEquals(25, arr.start());
        assertEquals(26, arr.elements().get(0).start());
        assertEquals(30, arr.elements().get(0).end());
        assertEquals(31, arr.elements().get(1).start());
        assertEquals(t.length() - 1, arr.end());
    }

    // -------------------------------------------------------------------------
    // duplicate keys and BOM
    // -------------------------------------------------------------------------

    @Test
    void duplicateKeysAllKept_lastWinsInValue() {
        String t = "{\"a\":1,\"b\":2,\"a\":3,\"\\u0061\":4}";
        JsonSpan root = roundTrip(t).root();
        List<JsonSpan.Member> ms = root.members();
        assertEquals(4, ms.size());
        assertEquals(List.of("a", "b", "a", "a"), ms.stream().map(JsonSpan.Member::key).toList());
        assertEquals("\"\\u0061\"", t.substring(ms.get(3).keyStart(), ms.get(3).keyEnd()));
        Map<?, ?> m = (Map<?, ?>) root.toValue();
        assertEquals(List.of("a", "b"), List.copyOf(m.keySet()));
        assertEquals(4, m.get("a"));
    }

    @Test
    void bomAccepted() {
        String t = "\uFEFF{\"a\":1}\n";
        JsonSpan.Doc doc = roundTrip(t);
        assertTrue(doc.bom());
        assertEquals(1, doc.root().start());
        assertEquals("{\"a\":1}", doc.root().text(t));
        assertEquals(JsonParser.parse(t.substring(1)), doc.root().toValue());
        assertFalse(JsonParser.parseSpans("{}").bom());
        // parse() itself still rejects a BOM
        assertThrows(JsErrorException.class, () -> JsonParser.parse(t));
    }

    @Test
    void bomOnlyAtTheStart() {
        assertSameError("[1,\uFEFF2]");
        assertSameError(" \uFEFF1");
        JsErrorException e = assertThrows(JsErrorException.class, () -> JsonParser.parseSpans("\uFEFF"));
        assertEquals(JsErrorException.syntaxError("Unexpected end of JSON input at position 1").getMessage(), e.getMessage());
    }

    // -------------------------------------------------------------------------
    // malformed input fails exactly as parse() does
    // -------------------------------------------------------------------------

    @Test
    void errorsMatchParse() {
        String[] bad = {
                "", "   ", "123 garbage", "{} garbage", "[1,2,3]extra",
                "{a:1}", "{\"a\":1,}", "[1,2,]", "'hello'", "{'a':1}",
                "NaN", "Infinity", "-Infinity", "// line\n42", "{\"a\":1/*c*/}",
                "True", "FALSE", "Null", "nil", "+1", "01", "-01", ".5", "1.", "-.5",
                "1e", "1e+", "-", "\"abc", "\"a\nb\"", "\"\\x41\"", "\"\\v\"", "\"\\\"",
                "\"\\u00\"", "\"\\uXXXX\"", "\"\\u\"", "{", "{\"a\"", "{\"a\":", "{\"a\":1",
                "{\"a\":1,", "[", "[1", "[1,", "{1:2}", "{true:1}", "{null:1}",
                "[1 2]", "{\"a\":1 \"b\":2}", "\u000B42", "\u00A042"
        };
        for (String t : bad) {
            assertSameError(t);
        }
        assertThrows(JsErrorException.class, () -> JsonParser.parseSpans(null));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static JsonSpan.Doc roundTrip(String t) {
        JsonSpan.Doc doc = JsonParser.parseSpans(t);
        JsonSpan root = doc.root();
        int lead = doc.bom() ? 1 : 0;
        assertTrue(t.substring(lead, root.start()).isBlank(), "only BOM and whitespace before the root");
        assertTrue(t.substring(root.end()).isBlank(), "only whitespace after the root");
        assertEquals(JsonParser.parse(t.substring(lead)), root.toValue());
        checkNode(t, root);
        return doc;
    }

    private static void checkNode(String t, JsonSpan n) {
        assertEquals(JsonParser.parse(n.text(t)), n.toValue(), "span text " + n.text(t));
        for (JsonSpan.Member m : n.members()) {
            assertEquals(m.key(), JsonParser.parse(t.substring(m.keyStart(), m.keyEnd())));
            assertTrue(m.keyEnd() <= m.value().start());
            checkNode(t, m.value());
        }
        for (JsonSpan e : n.elements()) {
            checkNode(t, e);
        }
    }

    private static void assertSameError(String t) {
        String expected;
        try {
            Object out = JsonParser.parse(t);
            fail("parse accepted " + t + ": " + out);
            return;
        } catch (JsErrorException e) {
            expected = e.getMessage();
        }
        JsErrorException e = assertThrows(JsErrorException.class, () -> JsonParser.parseSpans(t), t);
        assertSame(JsErrorPrototype.SYNTAX_ERROR, e.payload.getPrototype());
        assertEquals(expected, e.getMessage(), t);
    }

}
