package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsRegexTest extends EvalBase {

    @Test
    void testDev() {

    }

    @Test
    void testRegexLiteral() {
        eval("var re = /test/; var str = re.toString()");
        assertInstanceOf(JsRegex.class, get("re"));
        assertEquals("/test/", get("str"));
        eval("var re = /test/i; var str = re.toString()");
        assertInstanceOf(JsRegex.class, get("re"));
        assertEquals("/test/i", get("str"));
        eval("var x = 5 / 2;"); // division not regex
        assertEquals(2.5, get("x"));
        eval("var isDigit = /\\d/.test('5');"); // regex literal
        assertEquals(true, get("isDigit"));
        // contexts where regex is allowed
        eval("var a = { re: /test/ };");
        eval("var b = [/test/];");
        eval("var c = (/test/);");
        eval("function test() { return /test/; }");
        eval("var x = 1; var d = x > 2 ? /yes/ : /no/;");
        // regex after operators
        eval("var e = 5 + /test/;");
        eval("var f = !(/test/.test('no test'));");
    }

    @Test
    void testRegexConstructor() {
        eval("var re = /test/; var str = re.toString()");
        assertInstanceOf(JsRegex.class, get("re"));
        assertEquals("/test/", get("str"));

        eval("var re = new RegExp('test'); var str = re.toString()");
        assertInstanceOf(JsRegex.class, get("re"));
        assertEquals("/test/", get("str"));

        eval("var re = RegExp('test'); var str = re.toString()");
        assertInstanceOf(JsRegex.class, get("re"));
        assertEquals("/test/", get("str"));

        eval("var re = /test/i; var str = re.toString()");
        assertEquals("/test/i", get("str"));
        eval("var re = new RegExp('test', 'i'); var str = re.toString()");
        assertEquals("/test/i", get("str"));
    }

    @Test
    void testRegexTest() {
        eval("var re = /hello/; var res1 = re.test('hello world'); res2 = re.test('world');");
        assertEquals(true, get("res1"));
        assertEquals(false, get("res2"));

        // case insensitive
        eval("var re = /hello/i; var res = re.test('Hello World')");
        assertEquals(true, get("res"));

        // multi line
        eval("var re = /^hello/m; var res = re.test('first line\nhello second line')");
        assertEquals(true, get("res"));

        assertEquals(true, eval("/\\d+/.test('abc123')"));
        assertEquals(false, eval("/\\d+/.test('abcdef')"));
    }

    @Test
    void testRegexExec() {
        eval("var re = /hello/; var res = re.exec('hello world'); var res0 = res[0]; var ind = res.index;");
        assertEquals("hello", get("res0"));
        assertEquals(0, get("ind"));

        // capture groups
        eval("var re = /(he)(ll)(o)/; var res = re.exec('hello world');"
                + "var res0 = res[0]; var res1 = res[1]; var res2 = res[2]; var res3 = res[3]");
        assertEquals("hello", get("res0"));
        assertEquals("he", get("res1"));
        assertEquals("ll", get("res2"));
        assertEquals("o", get("res3"));

        // global flag
        eval("var re = /\\w+/g; var str = 'hello world'; var res = re.exec(str); var res0 = res[0];"
                + "var res2 = re.exec(str); var res20 = res2[0];"
                + "var res3 = re.exec(str); var ind = re.lastIndex;");
        assertEquals("hello", get("res0"));
        // second exec should find next match due to lastIndex
        assertEquals("world", get("res20"));
        // third exec should return null (no more matches)
        assertNull(get("res3"));
        // lastIndex should be reset to 0 after no match found
        assertEquals(0, get("ind"));
    }

    @Test
    void testRegexProperties() {
        eval("var re = /test/i; var res = re.source");
        assertEquals("test", get("res"));

        // with escape characters
        eval("var re = /test\\.com/; var res = re.source");
        assertEquals("test\\.com", get("res"));

        // flags
        eval("var re = /test/; var flags = re.flags; var global = re.global; var ignore = re.ignoreCase; var multi = re.multiline;");
        assertEquals("", get("flags"));
        assertEquals(false, get("global"));
        assertEquals(false, get("ignore"));
        assertEquals(false, get("multi"));

        eval("var re = /test/i; var flags = re.flags; var global = re.global; var ignore = re.ignoreCase; var multi = re.multiline;");
        assertEquals("i", get("flags"));
        assertEquals(false, get("global"));
        assertEquals(true, get("ignore"));
        assertEquals(false, get("multi"));

        eval("var re = /test/im; var flags = re.flags; var global = re.global; var ignore = re.ignoreCase; var multi = re.multiline;");
        assertEquals("im", get("flags"));
        assertEquals(false, get("global"));
        assertEquals(true, get("ignore"));
        assertEquals(true, get("multi"));
        eval("var re = /test/g; var flags = re.flags; var global = re.global; var ignore = re.ignoreCase; var multi = re.multiline;");
        assertEquals("g", get("flags"));
        assertEquals(true, get("global"));
        assertEquals(false, get("ignore"));
        assertEquals(false, get("multi"));

        // lastIndex
        eval("var re = /test/g; var ind1 = re.lastIndex; re.test('test'); var ind2 = re.lastIndex; re.lastIndex = 0; var ind3 = re.lastIndex");
        assertEquals(0, get("ind1"));
        assertTrue((Integer) get("ind2") > 0);
        // mutation of lastIndex
        // todo prototype "property" set probably needs work for re-use to work
        assertEquals(0, get("ind3"));
    }

    @Test
    void testRegexInArrowFunction() {
        // Arrow function body starting with regex literal
        // The parser must recognize that after =>, a / starts a regex (not division)
        eval("var fn = s => /^test/.test(s); var r1 = fn('testing'); var r2 = fn('no match')");
        assertEquals(true, get("r1"));
        assertEquals(false, get("r2"));

        // More complex regex pattern
        eval("var startsWithDigit = s => /^\\d/.test(s); var r1 = startsWithDigit('123abc'); var r2 = startsWithDigit('abc123')");
        assertEquals(true, get("r1"));
        assertEquals(false, get("r2"));

        // Arrow function with regex containing special chars
        eval("var matchPattern = s => /^1.*/.test(s); var r1 = matchPattern('100'); var r2 = matchPattern('1000'); var r3 = matchPattern('200')");
        assertEquals(true, get("r1"));
        assertEquals(true, get("r2"));
        assertEquals(false, get("r3"));

        // Used in higher-order function context (like isEach in TagSelector)
        eval("var items = ['100', '1000', '10']; var allMatch = items.every(s => /^1/.test(s))");
        assertEquals(true, get("allMatch"));
    }

    @Test
    void testInvalidRegex() {
        try {
            eval("var re = /(/;"); // Unbalanced parenthesis
            fail("Should have thrown an exception for invalid regex");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("invalid regex"));
        }
    }

}
