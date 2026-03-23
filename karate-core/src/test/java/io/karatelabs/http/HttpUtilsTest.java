package io.karatelabs.http;

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

}
