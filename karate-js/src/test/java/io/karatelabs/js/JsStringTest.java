package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsStringTest extends EvalBase {

    @Test
    void testDev() {

    }

    @Test
    void testStringConcat() {
        assertEquals("foobar", eval("'foo' + 'bar'"));
        assertEquals("abc", eval("'a' + 'b' + 'c'"));
        assertEquals("a1", eval("'a' + 1"));
        assertEquals("1a", eval("1 + 'a'"));
    }

    @Test
    void testStringWithEscapes() {
        assertEquals("foo\nbar", eval("'foo\nbar'"));
        assertEquals("foo\nbar", eval("\"foo\nbar\""));
        assertEquals("foo\nbarxxxbaz", eval("var a = 'xxx'; 'foo\nbar' + a + 'baz'"));
        assertEquals("fooxxxbar", eval("'foo\nbar'.replaceAll('\n', 'xxx')"));
        assertEquals("fooxxxbar", eval("'foo\nbar'.replaceAll(\"\n\", 'xxx')"));
    }

    @Test
    void testStringConcatExpr() {
        assertEquals("foobar", eval("var a = function(){ return 'bar' }; 'foo' + a()"));
        assertEquals("foobar", eval("var a = ['bar']; b = 'foo' + a[0]; b"));
    }

    @Test
    void testStringAsArray() {
        assertEquals("o", eval("var a = 'foo'; a[1]"));
    }

    @Test
    void testStringTemplate() {
        assertEquals("foobar", eval("var a = 'foo'; `${a}bar`"));
        assertEquals("$foobar", eval("var a = 'foo'; `$${a}bar`"));
        assertEquals("Cost New: $100", eval("var a = 100; `Cost New: $${a}`"));
        assertEquals("Cost New: $$100", eval("var a = 100; `Cost New: $$${a}`"));
        assertEquals("foobar", eval("var a = x => 'foo'; `${a()}bar`"));
        assertEquals("[1, 2, 3]", eval("`[${[].map.call([1,2,3], String).join(', ')}]`"));
    }

    @Test
    void testCallPrototype() {
        assertEquals(2, eval("''.indexOf.call('hello', 'll')"));
        assertEquals("el", eval("''.slice.call('hello', 1, 3)"));
        assertEquals("aaaaa", eval("''.repeat.call('a', 5)"));
    }

    @Test
    void testStringTemplateException() {
        try {
            eval("`${foo}`");
            fail("should throw exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("foo is not defined"));
        }
    }

    @Test
    void testStringTemplateNested() {
        assertEquals("foofoo", eval("var name = 'foo'; `${ name + `${name}` }`"));
        assertEquals("[ xxfooxx ]", eval("var name = 'foo'; `[ ${name ? `xx${name}xx` : ''} ]`"));
    }

    @Test
    void testStringConstructor() {
        assertEquals("", eval("a = String()"));
        match(get("a"), "''");
        assertEquals("undefined", eval("String(undefined)"));
        assertEquals("", eval("String(null)"));
        assertEquals("42", eval("String(42)"));
        assertEquals("true", eval("String(true)"));
        assertEquals(true, eval("typeof String() === 'string'"));
        assertEquals("", eval("new String().valueOf()"));
        assertEquals("hello", eval("new String('hello').valueOf()"));
        assertEquals("", eval("new String"));
        assertEquals("", eval("new String()"));
        // intentional js spec non-compliance
        // assertEquals(true, eval("typeof new String() === 'object'"));
        // assertEquals(true, eval("new String('hello') instanceof String"));
    }

    @Test
    void testStringApi() {
        assertEquals(3, eval("a = 'foobar'; a.indexOf('bar')"));
        assertEquals(3, eval("a = 'foo'; a.length"));
        assertEquals(true, eval("a = 'foobar'; a.startsWith('foo')"));
        assertEquals("FOObar", eval("a = 'foobar'; a.replaceAll('foo', 'FOO')"));
        assertEquals(List.of("foo", "bar"), eval("a = 'foo bar'; a.split(' ')"));
        assertEquals(3, eval("a = 'foobar'; a.indexOf('bar', 1)"));
        assertEquals(-1, eval("a = 'foobar'; a.indexOf('bar', 4)"));
        assertEquals(true, eval("a = 'foobar'; a.startsWith('bar', 3)"));
        assertEquals("o", eval("a = 'foobar'; a.charAt(1)"));
        assertEquals("", eval("a = 'foobar'; a.charAt(10)"));
        assertEquals(111, eval("a = 'foobar'; a.charCodeAt(1)")); // 'o' is 111
        assertEquals(Double.NaN, eval("a = 'foobar'; a.charCodeAt(10)"));
        assertEquals(111, eval("a = 'foobar'; a.codePointAt(1)")); // 'o' is 111
        assertNull(eval("a = 'foobar'; a.codePointAt(10)"));
        assertEquals("foobarbaz", eval("a = 'foobar'; a.concat('baz')"));
        assertEquals("foobarbazqux", eval("a = 'foobar'; a.concat('baz', 'qux')"));
        assertEquals(true, eval("a = 'foobar'; a.endsWith('bar')"));
        assertEquals(false, eval("a = 'foobar'; a.endsWith('foo')"));
        assertEquals(true, eval("a = 'foobar'; a.endsWith('foo', 3)"));
        assertEquals(true, eval("a = 'foobar'; a.includes('bar')"));
        assertEquals(false, eval("a = 'foobar'; a.includes('baz')"));
        assertEquals(false, eval("a = 'foobar'; a.includes('foo', 3)"));
        assertEquals(3, eval("a = 'foobar'; a.lastIndexOf('bar')"));
        assertEquals(0, eval("a = 'foobar'; a.lastIndexOf('foo')"));
        assertEquals(0, eval("a = 'foobar'; a.lastIndexOf('foo', 0)"));
        assertEquals(3, eval("a = 'foofoobar'; a.lastIndexOf('foo', 4)"));
        assertEquals(0, eval("a = 'foofoobar'; a.lastIndexOf('foo', 2)"));
        assertEquals(-1, eval("a = 'foobar'; a.lastIndexOf('bar', 2)"));
        assertEquals("abc  ", eval("a = 'abc'; a.padEnd(5)"));
        assertEquals("abcxy", eval("a = 'abc'; a.padEnd(5, 'xyz')"));
        assertEquals("abc", eval("a = 'abc'; a.padEnd(3)"));
        assertEquals("  abc", eval("a = 'abc'; a.padStart(5)"));
        assertEquals("xyabc", eval("a = 'abc'; a.padStart(5, 'xyz')"));
        assertEquals("abc", eval("a = 'abc'; a.padStart(3)"));
        assertEquals("abcabcabc", eval("a = 'abc'; a.repeat(3)"));
        assertEquals("", eval("a = 'abc'; a.repeat(0)"));
        assertEquals("fxxbar", eval("a = 'foobar'; a.replace('oo', 'xx')"));
        assertEquals("bar", eval("a = 'foobar'; a.slice(3)"));
        assertEquals("ob", eval("a = 'foobar'; a.slice(2, 4)"));
        assertEquals("ob", eval("a = 'foobar'; a.slice(-4, -2)"));
        assertEquals("bar", eval("a = 'foobar'; a.substring(3)"));
        assertEquals("ob", eval("a = 'foobar'; a.substring(2, 4)"));
        assertEquals("oob", eval("a = 'foobar'; a.substring(4, 1)"));  // should swap indices
        assertEquals("foobar", eval("a = 'FOOBAR'; a.toLowerCase()"));
        assertEquals("FOOBAR", eval("a = 'foobar'; a.toUpperCase()"));
        assertEquals("foo", eval("a = '  foo  '; a.trim()"));
        assertEquals("  foo", eval("a = '  foo  '; a.trimEnd()"));
        assertEquals("foo  ", eval("a = '  foo  '; a.trimStart()"));
        assertEquals("  foo", eval("a = '  foo  '; a.trimRight()"));
        assertEquals("foo  ", eval("a = '  foo  '; a.trimLeft()"));
        assertEquals("foobar", eval("a = 'foobar'; a.valueOf()"));
        assertEquals("ABC", eval("String.fromCharCode(65, 66, 67)"));
        assertEquals("😀", eval("String.fromCodePoint(128512)"));
    }

    @Test
    void testSearch() {
        assertEquals(4, eval("'hey JudE'.search(/[A-Z]/)"));
    }

    @Test
    void testReplace() {
        assertEquals("xxFOOxx", eval("a = 'xxfooxx'; a.replace('foo', 'FOO')"));
        assertEquals("xxFOOxx", eval("a = 'xxfooxx'; a.replace(/foo/, 'FOO')"));
        assertEquals("xxFOOxx", eval("a = 'xxfooxx'; a.replace(/f../, 'FOO')"));
        assertEquals("the cat is", eval("'the Dog is'.replace(/dog/i, 'cat')"));
        assertEquals("oranges are round, oranges are juicy", eval("'Apples are round, apples are juicy'.replace(/apple/ig, 'orange')"));
        assertEquals("oranges are round, oranges are juicy", eval("'Apples are round, apples are juicy'.replaceAll(/apple/ig, 'orange')"));
    }

    @Test
    void testToString() {
        assertEquals("1234", eval("a = '1234'; a.toString()"));
        assertEquals("1234", eval("a = 1234; a.toString()"));
    }

    @Test
    void testMatch() {
        matchEval("a = 'xxfooxx'; a.match('foo')", "['foo']");
        matchEval("'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'.match(/[A-E]/gi)", "['A','B','C','D','E','a','b','c','d','e']");
    }

    @Test
    void testEncoding() {
        byte[] bytes = (byte[]) eval("new TextEncoder().encode('hello')");
        assertEquals("hello", new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void testDecoding() {
        assertEquals("hello world", eval("new TextDecoder().decode(new TextEncoder().encode('hello world'))"));
    }

    @Test
    void testByteArray() {
        byte[] bytes = (byte[]) eval("new Uint8Array(10)");
        assertEquals(10, bytes.length);
        for (byte b : bytes) {
            assertEquals(0, b);
        }
        bytes = (byte[]) eval("new Uint8Array([72, 101, 108, 108, 111])");
        assertEquals("Hello", new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void testByteArrayIndexAccess() {
        assertEquals(72, eval("let b = new Uint8Array([72, 101, 108]); b[0]"));
        byte[] bytes = (byte[]) eval("let b = new Uint8Array([72, 101, 108]); b[1] = 65; b");
        assertEquals(65, bytes[1]);
    }

}
