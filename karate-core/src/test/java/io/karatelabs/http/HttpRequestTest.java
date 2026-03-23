package io.karatelabs.http;

import io.karatelabs.js.JavaInvokable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpRequestTest {

    @Test
    void testParam() {
        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost/test?name=john&age=25");

        assertEquals("john", request.getParam("name"));
        assertEquals("25", request.getParam("age"));
        assertNull(request.getParam("missing"));

        // Test via jsGet
        JavaInvokable param = (JavaInvokable) request.jsGet("param");
        assertEquals("john", param.invoke("name"));
        assertEquals("25", param.invoke("age"));
        assertNull(param.invoke("missing"));
    }

    @Test
    void testParamInt() {
        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost/test?count=42&page=1&invalid=abc");

        JavaInvokable paramInt = (JavaInvokable) request.jsGet("paramInt");

        // Valid integer params
        assertEquals(42, paramInt.invoke("count"));
        assertEquals(1, paramInt.invoke("page"));

        // Missing param returns null
        assertNull(paramInt.invoke("missing"));

        // Invalid integer throws NumberFormatException
        assertThrows(NumberFormatException.class, () -> paramInt.invoke("invalid"));
    }

    @Test
    void testParamIntMissingArgument() {
        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost/test");

        JavaInvokable paramInt = (JavaInvokable) request.jsGet("paramInt");
        assertThrows(RuntimeException.class, paramInt::invoke);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testParamJson() {
        HttpRequest request = new HttpRequest();
        // URL encoded JSON: {"name":"john","age":25}
        request.setUrl("http://localhost/test?data=%7B%22name%22%3A%22john%22%2C%22age%22%3A25%7D&arr=%5B1%2C2%2C3%5D");

        JavaInvokable paramJson = (JavaInvokable) request.jsGet("paramJson");

        // JSON object param
        Map<String, Object> data = (Map<String, Object>) paramJson.invoke("data");
        assertEquals("john", data.get("name"));
        assertEquals(25, data.get("age"));

        // JSON array param
        List<Object> arr = (List<Object>) paramJson.invoke("arr");
        assertEquals(3, arr.size());
        assertEquals(1, arr.get(0));
        assertEquals(2, arr.get(1));
        assertEquals(3, arr.get(2));

        // Missing param returns null
        assertNull(paramJson.invoke("missing"));
    }

    @Test
    void testParamJsonLenient() {
        HttpRequest request = new HttpRequest();
        // Non-JSON values are returned as raw strings
        request.setUrl("http://localhost/test?str=hello&num=42&bool=true");

        JavaInvokable paramJson = (JavaInvokable) request.jsGet("paramJson");

        // All non-JSON values return as raw strings
        assertEquals("hello", paramJson.invoke("str"));
        assertEquals("42", paramJson.invoke("num"));
        assertEquals("true", paramJson.invoke("bool"));
    }

    @Test
    void testParamJsonMissingArgument() {
        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost/test");

        JavaInvokable paramJson = (JavaInvokable) request.jsGet("paramJson");
        assertThrows(RuntimeException.class, paramJson::invoke);
    }

    @Test
    void testParamValues() {
        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost/test?tag=a&tag=b&tag=c");

        JavaInvokable paramValues = (JavaInvokable) request.jsGet("paramValues");
        List<?> values = (List<?>) paramValues.invoke("tag");
        assertEquals(3, values.size());
        assertEquals("a", values.get(0));
        assertEquals("b", values.get(1));
        assertEquals("c", values.get(2));
    }

    @Test
    void testMethodBooleans() {
        HttpRequest request = new HttpRequest();
        request.setMethod("GET");

        assertTrue((Boolean) request.jsGet("get"));
        assertFalse((Boolean) request.jsGet("post"));
        assertFalse((Boolean) request.jsGet("put"));
        assertFalse((Boolean) request.jsGet("delete"));

        request.setMethod("POST");
        assertFalse((Boolean) request.jsGet("get"));
        assertTrue((Boolean) request.jsGet("post"));
    }

    @Test
    void testPathAndUrl() {
        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost:8080/api/users?id=123");

        assertEquals("http://localhost:8080", request.jsGet("urlBase"));
        assertEquals("/api/users", request.jsGet("path"));
        assertEquals("/api/users?id=123", request.jsGet("pathRaw"));
        assertEquals("http://localhost:8080/api/users?id=123", request.jsGet("url"));
    }

    @Test
    void testHeaders() {
        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost/test");
        request.putHeader("Content-Type", "application/json");
        request.putHeader("X-Custom", "value1", "value2");

        JavaInvokable header = (JavaInvokable) request.jsGet("header");
        assertEquals("application/json", header.invoke("Content-Type"));
        assertEquals("value2", header.invoke("X-Custom")); // returns last value

        JavaInvokable headerValues = (JavaInvokable) request.jsGet("headerValues");
        List<?> values = (List<?>) headerValues.invoke("X-Custom");
        assertEquals(2, values.size());
        assertEquals("value1", values.get(0));
        assertEquals("value2", values.get(1));
    }

    @Test
    void testPathMatches() {
        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost/users/123/posts/456");

        JavaInvokable pathMatches = (JavaInvokable) request.jsGet("pathMatches");
        assertTrue((Boolean) pathMatches.invoke("/users/{userId}/posts/{postId}"));

        Map<?, ?> pathParams = (Map<?, ?>) request.jsGet("pathParams");
        assertEquals("123", pathParams.get("userId"));
        assertEquals("456", pathParams.get("postId"));
    }

    @Test
    void testBody() {
        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost/test");
        request.setContentType("application/json");
        request.setBody("{\"name\":\"john\"}".getBytes());

        assertEquals("{\"name\":\"john\"}", request.jsGet("bodyString"));
        assertArrayEquals("{\"name\":\"john\"}".getBytes(), (byte[]) request.jsGet("bodyBytes"));

        // body returns converted value (Map for JSON)
        Object body = request.jsGet("body");
        assertTrue(body instanceof Map);
        assertEquals("john", ((Map<?, ?>) body).get("name"));
    }

}
