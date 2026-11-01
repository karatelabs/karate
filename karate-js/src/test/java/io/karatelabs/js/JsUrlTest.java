package io.karatelabs.js;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsUrlTest extends EvalBase {

    @BeforeEach
    void init() {
        engine = new Engine();
    }

    private Object js(String text) {
        return engine.eval(text);
    }

    private String href(String input) {
        return (String) js("new URL(" + input + ").href");
    }

    @Test
    void testParseComponents() {
        js("var u = new URL('HTTPS://user:p%40ss@Example.COM:8443/a/b.json?x=1&y=two#frag')");
        assertEquals("https://user:p%40ss@example.com:8443/a/b.json?x=1&y=two#frag", js("u.href"));
        assertEquals("https:", js("u.protocol"));
        assertEquals("user", js("u.username"));
        assertEquals("p%40ss", js("u.password"));
        assertEquals("example.com:8443", js("u.host"));
        assertEquals("example.com", js("u.hostname"));
        assertEquals("8443", js("u.port"));
        assertEquals("/a/b.json", js("u.pathname"));
        assertEquals("?x=1&y=two", js("u.search"));
        assertEquals("#frag", js("u.hash"));
        assertEquals("https://example.com:8443", js("u.origin"));
        assertEquals(js("u.href"), js("u.toString()"));
        assertEquals(js("u.href"), js("String(u)"));
        assertEquals(js("u.href"), js("`${u}`"));
        assertEquals("\"" + js("u.href") + "\"", js("JSON.stringify(u)"));
    }

    @Test
    void testDefaultsAndNormalization() {
        assertEquals("http://example.com/", href("'http://example.com'"));
        assertEquals("http://example.com/", href("'http://example.com:80/'"));
        assertEquals("", js("new URL('https://example.com:443').port"));
        assertEquals("http://example.com/a/c", href("'http://example.com/a/./b/../c'"));
        assertEquals("http://example.com/a%20b?q=a%20b#a%20b", href("'http://example.com/a b?q=a b#a b'"));
        assertEquals("http://example.com/%C3%A9?%C3%A9", href("'http://example.com/é?é'"));
        assertEquals("http://example.com/a/b", href("'http:\\\\\\\\example.com\\\\a\\\\b'"));
        assertEquals("http://127.0.0.1/", href("'http://0x7f.1/'"));
        assertEquals("http://[::1]:8080/", href("'http://[0:0:0:0:0:0:0:1]:8080'"));
        assertEquals("http://xn--bcher-kva.de/", href("'http://Bücher.de'"));
        assertEquals("", js("new URL('http://example.com').search"));
        assertEquals("", js("new URL('http://example.com?').search"));
        assertEquals("http://example.com/?", href("'http://example.com?'"));
    }

    @Test
    void testNonSpecialSchemes() {
        js("var u = new URL('mailto:Someone@Example.com?subject=hi')");
        assertEquals("mailto:", js("u.protocol"));
        assertEquals("Someone@Example.com", js("u.pathname"));
        assertEquals("", js("u.host"));
        assertEquals("?subject=hi", js("u.search"));
        assertEquals("null", js("u.origin"));
        assertEquals("urn:isbn:0451450523", href("'urn:isbn:0451450523'"));
        assertEquals("foo://Host/p", href("'foo://Host/p'"));
        assertEquals("file:///tmp/a.txt", href("'file:///tmp/a.txt'"));
    }

    @Test
    void testRelativeResolution() {
        String base = "'http://a/b/c/d;p?q'";
        assertEquals("http://a/b/c/g", href("'g', " + base));
        assertEquals("http://a/b/c/g/", href("'./g/', " + base));
        assertEquals("http://a/g", href("'/g', " + base));
        assertEquals("http://g/", href("'//g', " + base));
        assertEquals("http://a/b/c/d;p?y", href("'?y', " + base));
        assertEquals("http://a/b/c/d;p?q#s", href("'#s', " + base));
        assertEquals("http://a/b/", href("'..', " + base));
        assertEquals("http://a/g", href("'../../../g', " + base));
        assertEquals("http://a/b/c/d;p?q", href("'', " + base));
        assertEquals("https://other/x", href("'https://other/x', " + base));
        assertEquals("http://a/b/c/g", href("'g', new URL(" + base + ")"));
    }

    @Test
    void testInvalid() {
        assertEquals("TypeError: Invalid URL", js("try { new URL('nope') } catch (e) { e.name + ': ' + e.message }"));
        assertEquals("TypeError: Invalid URL", js("try { new URL('http://') } catch (e) { e.name + ': ' + e.message }"));
        assertEquals("TypeError: Invalid URL", js("try { new URL('http://a:99999') } catch (e) { e.name + ': ' + e.message }"));
        assertEquals("TypeError: Invalid URL", js("try { new URL('g', 'nope') } catch (e) { e.name + ': ' + e.message }"));
        assertEquals(true, js("URL.canParse('http://a')"));
        assertEquals(false, js("URL.canParse('a')"));
        assertEquals(true, js("URL.canParse('a', 'http://b')"));
        assertNull(js("URL.parse('a')"));
        assertEquals("http://b/a", js("URL.parse('a', 'http://b').href"));
        assertEquals("TypeError: Constructor URL requires 'new'", js("try { URL('http://a') } catch (e) { e.name + ': ' + e.message }"));
    }

    @Test
    void testSetters() {
        js("var u = new URL('http://example.com/a?x=1#h')");
        js("u.pathname = '/b c'; u.search = 'y=2'; u.hash = 'z'; u.port = '8080'; u.hostname = 'Other.org'");
        assertEquals("http://other.org:8080/b%20c?y=2#z", js("u.href"));
        js("u.port = '80'");
        assertEquals("other.org", js("u.host"));
        js("u.host = 'h.io:9'; u.username = 'me'; u.password = 'pw'; u.protocol = 'https'");
        assertEquals("https://me:pw@h.io:9/b%20c?y=2#z", js("u.href"));
        js("u.search = ''; u.hash = ''");
        assertEquals("https://me:pw@h.io:9/b%20c", js("u.href"));
        js("u.href = 'http://new.example/p?k=v'");
        assertEquals("v", js("u.searchParams.get('k')"));
        assertEquals("TypeError: Invalid URL", js("try { u.href = 'bad' } catch (e) { e.name + ': ' + e.message }"));
        // a special scheme does not become a non-special one
        js("u.protocol = 'foo'");
        assertEquals("http:", js("u.protocol"));
    }

    @Test
    void testUrlSearchParamsOfUrl() {
        js("var u = new URL('http://a/p?x=1&y=a+b&y=%C3%A9&z')");
        assertEquals("1", js("u.searchParams.get('x')"));
        assertEquals("a b", js("u.searchParams.get('y')"));
        match(js("u.searchParams.getAll('y')"), "['a b', 'é']");
        assertEquals("", js("u.searchParams.get('z')"));
        assertNull(js("u.searchParams.get('none')"));
        assertEquals(true, js("u.searchParams === u.searchParams"));
        js("u.searchParams.append('w', 'c d&e'); u.searchParams.delete('y')");
        assertEquals("?x=1&z=&w=c+d%26e", js("u.search"));
        assertEquals("http://a/p?x=1&z=&w=c+d%26e", js("u.href"));
        js("u.searchParams.delete('x'); u.searchParams.delete('z'); u.searchParams.delete('w')");
        assertEquals("http://a/p", js("u.href"));
        js("u.search = '?q=1'");
        assertEquals("1", js("u.searchParams.get('q')"));
    }

    @Test
    void testUrlSearchParams() {
        assertEquals("a=1&b=2", js("new URLSearchParams('?a=1&b=2').toString()"));
        assertEquals("a=1&b=x+y", js("new URLSearchParams({ a: 1, b: 'x y' }).toString()"));
        assertEquals("a=1&a=2", js("new URLSearchParams([['a', '1'], ['a', 2]]).toString()"));
        assertEquals("k=v", js("new URLSearchParams(new Map([['k', 'v']])).toString()"));
        assertEquals("a=1", js("new URLSearchParams(new URLSearchParams('a=1')).toString()"));
        assertEquals("", js("new URLSearchParams().toString()"));
        assertEquals("TypeError", js("try { new URLSearchParams([['a']]) } catch (e) { e.name }"));
        js("var p = new URLSearchParams('b=2&a=1&b=3')");
        assertEquals(3, js("p.size"));
        assertEquals(true, js("p.has('a')"));
        assertEquals(true, js("p.has('b', '3')"));
        assertEquals(false, js("p.has('b', '4')"));
        js("p.set('b', 'x')");
        assertEquals("b=x&a=1", js("p.toString()"));
        js("p.append('c', '1'); p.append('a', '0'); p.sort()");
        assertEquals("a=1&a=0&b=x&c=1", js("p.toString()"));
        js("p.delete('a', '0')");
        assertEquals("a=1&b=x&c=1", js("p.toString()"));
        match(js("[...p]"), "[['a', '1'], ['b', 'x'], ['c', '1']]");
        match(js("[...p.keys()]"), "['a', 'b', 'c']");
        match(js("[...p.values()]"), "['1', 'x', '1']");
        match(js("Array.from(p.entries())"), "[['a', '1'], ['b', 'x'], ['c', '1']]");
        assertEquals("a1bxc1", js("var s = ''; p.forEach((v, k) => s += k + v); s"));
        assertEquals("a1bxc1", js("var t = ''; for (const [k, v] of p) t += k + v; t"));
        assertEquals("a=%21%27%28%29%7E*-._", js("new URLSearchParams({ a: \"!'()~*-._\" }).toString()"));
        assertEquals(true, js("new URLSearchParams() instanceof URLSearchParams"));
        assertEquals(true, js("new URL('http://a') instanceof URL"));
        assertEquals("[object URLSearchParams]", js("Object.prototype.toString.call(new URLSearchParams())"));
    }

}
