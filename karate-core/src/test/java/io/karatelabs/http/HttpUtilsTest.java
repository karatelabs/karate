package io.karatelabs.http;

import io.karatelabs.common.ResourceType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpUtilsTest {

    @Test
    void testExtractPath() {
        assertEquals("/test", HttpUtils.extractPath("/test"));
        assertEquals("/test", HttpUtils.extractPath("/test?name=john"));
        assertEquals("/api/users", HttpUtils.extractPath("/api/users?id=1&id=2"));
        assertEquals("/", HttpUtils.extractPath("/?foo=bar"));
    }

    @Test
    void testExtractQueryString() {
        assertNull(HttpUtils.extractQueryString("/test"));
        assertEquals("name=john", HttpUtils.extractQueryString("/test?name=john"));
        assertEquals("id=1&id=2", HttpUtils.extractQueryString("/api/users?id=1&id=2"));
        assertEquals("foo=bar", HttpUtils.extractQueryString("/?foo=bar"));
    }

    @Test
    void testParseQueryParams() {
        // No params
        Map<String, List<String>> params = HttpUtils.parseQueryParams("/test");
        assertTrue(params.isEmpty());

        // Single param
        params = HttpUtils.parseQueryParams("/test?name=john");
        assertEquals(List.of("john"), params.get("name"));

        // Multiple params
        params = HttpUtils.parseQueryParams("/test?name=john&age=25");
        assertEquals(List.of("john"), params.get("name"));
        assertEquals(List.of("25"), params.get("age"));

        // Multi-value param
        params = HttpUtils.parseQueryParams("/api/users?id=1&id=2&id=3");
        assertEquals(List.of("1", "2", "3"), params.get("id"));

        // URL encoded values
        params = HttpUtils.parseQueryParams("/test?msg=hello%20world");
        assertEquals(List.of("hello world"), params.get("msg"));
    }

    @Test
    void testBuildUri() {
        // No params
        assertEquals("/test", HttpUtils.buildUri("/test", null));
        assertEquals("/test", HttpUtils.buildUri("/test", Map.of()));

        // Single param
        String uri = HttpUtils.buildUri("/test", Map.of("name", List.of("john")));
        assertEquals("/test?name=john", uri);

        // Multiple params (order may vary)
        uri = HttpUtils.buildUri("/test", Map.of("name", List.of("john"), "age", List.of("25")));
        assertTrue(uri.startsWith("/test?"));
        assertTrue(uri.contains("name=john"));
        assertTrue(uri.contains("age=25"));

        // Multi-value param
        uri = HttpUtils.buildUri("/api", Map.of("id", List.of("1", "2")));
        assertTrue(uri.contains("id=1"));
        assertTrue(uri.contains("id=2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testParsePathAndParams() {
        var result = HttpUtils.parsePathAndParams("/test?name=john&age=25");
        assertEquals("/test", result.left);

        Map<String, List<String>> params = (Map<String, List<String>>) result.right;
        assertEquals(List.of("john"), params.get("name"));
        assertEquals(List.of("25"), params.get("age"));
    }

    @Test
    void testParseUriPattern() {
        // Simple match
        Map<String, String> params = HttpUtils.parseUriPattern("/users/{id}", "/users/123");
        assertEquals("123", params.get("id"));

        // Multiple params
        params = HttpUtils.parseUriPattern("/users/{userId}/posts/{postId}", "/users/1/posts/42");
        assertEquals("1", params.get("userId"));
        assertEquals("42", params.get("postId"));

        // No match - different segment count
        assertNull(HttpUtils.parseUriPattern("/users/{id}", "/users/123/extra"));

        // No match - literal mismatch
        assertNull(HttpUtils.parseUriPattern("/users/{id}", "/posts/123"));

        // Strips query string before matching
        params = HttpUtils.parseUriPattern("/users/{id}", "/users/123?foo=bar");
        assertEquals("123", params.get("id"));
    }

    @Test
    void testNormaliseUriPath() {
        assertEquals("/test", HttpUtils.normaliseUriPath("/test"));
        assertEquals("/test", HttpUtils.normaliseUriPath("/test/"));
        assertEquals("/test", HttpUtils.normaliseUriPath("test"));
        assertEquals("/test", HttpUtils.normaliseUriPath("/test?foo=bar"));
        assertEquals("/", HttpUtils.normaliseUriPath("/"));
    }

    @Test
    void testInvalidJsonFallsBackToString() {
        // JSONValue.parseKeepingOrder returns null for invalid JSON instead of throwing
        // This test verifies we fall back to raw string
        String invalidJson = "{ \"foo\": }";
        Object result = HttpUtils.fromString(invalidJson, false, null);
        assertEquals(invalidJson, result);
    }

    @Test
    void testTextBodyThatOnlyLooksLikeJson() {
        // a TOML document opens with a table header, and the lenient parser would happily
        // truncate it to a one-element list - an explicit non-JSON content-type has to be believed
        String toml = "[agent]\n\tinterval = \"1s\"\n\tround_interval = true\n\n[[processors.date]]\n\torder=1";
        assertEquals(toml, HttpUtils.fromString(toml, false, ResourceType.TEXT));
        assertEquals(toml, HttpUtils.fromString(toml, false, ResourceType.HTML));
        assertEquals(toml, HttpUtils.fromString(toml, false, ResourceType.fromContentType("application/toml")));
        // with no content-type there is nothing to believe, but the guess still has to cover the
        // WHOLE body - the lenient parser used to stop after "[agent]" and hand back a one-element list
        assertEquals(toml, HttpUtils.fromString(toml, false, null));
    }

    @Test
    void testNdJsonIsNotTruncatedToItsFirstLine() {
        // newline-delimited json is not one json document, and the lenient parser silently kept
        // only the first line - the whole body has to survive as a string so it can be split
        String ndJson = "{\"id\":0}\n{\"id\":1}\n{\"id\":2}\n";
        assertEquals(ndJson, HttpUtils.fromString(ndJson, false, ResourceType.JSON));
        assertEquals(ndJson, HttpUtils.fromString(ndJson, false, null));
        assertEquals(ndJson, HttpUtils.fromString(ndJson, false, ResourceType.fromContentType("application/x-ndjson")));
        // and the leniency that a json content-type does buy is untouched
        assertEquals(Map.of("a", 1), HttpUtils.fromString("{a:1,}", false, ResourceType.JSON));
    }

    @Test
    void testTextBodyThatIsWhollyJson() {
        // a server mislabelling JSON as text/plain is common enough that the body still wins,
        // provided the WHOLE document parses - here with a leading linefeed for good measure
        String json = "\n{ \"success\": true }";
        assertEquals(Map.of("success", true), HttpUtils.fromString(json, false, ResourceType.TEXT));
        assertEquals(Map.of("success", true), HttpUtils.fromString(json, false, ResourceType.JSON));
        assertEquals(Map.of("success", true), HttpUtils.fromString(json, false, null));
    }

    // ===== the declared-type lane (configure strictResponseParsing = true) =====

    private static byte[] bytes(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void testDeclaredLaneNeverSniffs() {
        // the exact case the lenient lane parses anyway (wholly-JSON text/plain, above):
        // under the declared lane the Content-Type is authoritative and the String survives
        String json = "\n{ \"success\": true }";
        assertEquals(json, HttpUtils.fromBytesDeclared(bytes(json), ResourceType.TEXT));
        assertEquals(json, HttpUtils.fromBytesDeclared(bytes(json), ResourceType.HTML));
        // declared JSON still parses, with the lenient lane's grammar tolerance
        assertEquals(Map.of("success", true), HttpUtils.fromBytesDeclared(bytes(json), ResourceType.JSON));
        assertEquals(Map.of("a", 1), HttpUtils.fromBytesDeclared(bytes("{a:1,}"), ResourceType.JSON));
    }

    @Test
    void testDeclaredLaneKeepsTheIssueShapesRaw() {
        // the TOML shape (#2977): text-family declared type -> raw, no brace guessing at all
        String toml = "[agent]\nname = \"jarvis\"";
        assertEquals(toml, HttpUtils.fromBytesDeclared(bytes(toml), ResourceType.TEXT));
        assertEquals(toml, HttpUtils.fromBytesDeclared(bytes(toml),
                ResourceType.fromContentType("application/toml")));
        // the NDJSON shape (#2990): even DECLARED as json it does not parse whole -> raw string
        String ndJson = "{\"a\":1}\n{\"a\":2}";
        assertEquals(ndJson, HttpUtils.fromBytesDeclared(bytes(ndJson), ResourceType.JSON));
    }

    @Test
    void testDeclaredLaneFallbacks() {
        // no declared type -> nothing to be strict about: same sniff as the lenient lane
        assertEquals(Map.of("a", 1), HttpUtils.fromBytesDeclared(bytes("{\"a\":1}"), null));
        // declared XML parses; malformed declared XML keeps the raw string
        assertInstanceOf(org.w3c.dom.Node.class,
                HttpUtils.fromBytesDeclared(bytes("<a><b/></a>"), ResourceType.XML));
        assertEquals("<a><b>", HttpUtils.fromBytesDeclared(bytes("<a><b>"), ResourceType.XML));
        // binary passes through untouched; null and empty stay themselves
        byte[] raw = {1, 2, 3};
        assertSame(raw, HttpUtils.fromBytesDeclared(raw, ResourceType.BINARY));
        assertNull(HttpUtils.fromBytesDeclared(null, ResourceType.JSON));
        assertEquals("", HttpUtils.fromBytesDeclared(bytes(""), ResourceType.JSON));
    }

}
