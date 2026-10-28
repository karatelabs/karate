package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpRequestBuilderUriTest {

    private static String uri(String url, String... paths) {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url(url);
        http.paths(paths);
        return http.getUri();
    }

    @Test
    void testPathKeepsAtAndColonRaw() {
        assertEquals("https://h/anything/users/john@example.com/items:search",
                uri("https://h/anything", "users/john@example.com/items:search"));
    }

    @Test
    void testPathSubDelimsRaw() {
        assertEquals("http://h/a!$&'()*+,;=:@b", uri("http://h", "a!$&'()*+,;=:@b"));
        assertEquals("http://h/-._~09azAZ", uri("http://h", "-._~09azAZ"));
    }

    @Test
    void testPathGenDelimsAndOthersEncoded() {
        assertEquals("http://h/a%3Fb%23c%20d%25e%5Bf%5Dg%C3%A9", uri("http://h", "a?b#c d%e[f]gé"));
        assertEquals("http://h/a%22b%3Cc%3Ed%5Ce%5Ef%60g%7Bh%7Ci%7D", uri("http://h", "a\"b<c>d\\e^f`g{h|i}"));
    }

    @Test
    void testSlashSplitsSegmentsUnlessEscaped() {
        assertEquals("http://h/a/b", uri("http://h", "a/b"));
        assertEquals("http://h/a/b", uri("http://h", "a", "b"));
        assertEquals("http://h/a%2Fb", uri("http://h", "a\\/b"));
    }

    @Test
    void testUrlPathPreservedWhenPathAppended() {
        assertEquals("http://h/v1:x/c@d/e", uri("http://h/v1:x/c@d", "e"));
        assertEquals("http://h/a%20b/c%2Fd/e", uri("http://h/a%20b/c%2Fd", "e"));
    }

    @Test
    void testTrailingAndLeadingSlash() {
        assertEquals("http://h/a/b/", uri("http://h", "a", "b", "/"));
        assertEquals("http://h/a/b/", uri("http://h/a/", "b", "/"));
        assertEquals("http://h/", uri("http://h", "/"));
        assertEquals("http://h/a", uri("http://h/", "/a"));
        assertEquals("http://h:8080/a", uri("http://h:8080", "a"));
    }

    @Test
    void testNoPathLeavesUrlUntouched() {
        assertEquals("http://h/v1:x/c@d", uri("http://h/v1:x/c@d"));
    }

    @Test
    void testUrlPathUntouchedWithParamsOnly() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("http://h/v1:x/c@d!$'()*,;=%20e");
        http.param("q", "a&b");
        assertEquals("http://h/v1:x/c@d!$'()*,;=%20e?q=a%26b", http.getUri());
    }

    @Test
    void testEdgePaths() {
        assertEquals("http://h/v1:x/", uri("http://h/v1:x", "/"));
        assertEquals("http://h/v1:x/a", uri("http://h/v1:x", "/a"));
        assertEquals("http://h/v1:x/", uri("http://h/v1:x", ""));
        assertEquals("http://h", uri("http://h", "//"));
        assertEquals("http://h", uri("http://h/", "//"));
    }

    @Test
    void testParamsEncodingUnchanged() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("http://h");
        http.path("a@b");
        http.param("q", "a&b=c+d e");
        http.param("x:y", "é");
        assertEquals("http://h/a@b?q=a%26b%3Dc%2Bd%20e&x%3Ay=%C3%A9", http.getUri());
    }

    @Test
    void testParamsAndFragmentWithPath() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("http://user@h/base?k=v#frag");
        http.path("p:q");
        http.param("z", "1");
        assertEquals("http://user@h/base/p:q?k=v&z=1#frag", http.getUri());
    }

}
