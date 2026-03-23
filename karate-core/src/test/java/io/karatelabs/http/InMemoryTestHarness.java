package io.karatelabs.http;

import io.karatelabs.common.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * In-memory test harness that bypasses the HTTP/Netty layer entirely.
 * Directly connects request to handler without network overhead.
 * <p>
 * Use this for fast unit tests that don't need real HTTP networking.
 * For tests that need actual TCP connections, use {@link ServerTestHarness}.
 * <p>
 * Usage:
 * <pre>
 * InMemoryTestHarness harness = new InMemoryTestHarness(request -> {
 *     HttpResponse response = new HttpResponse();
 *     response.setBody("hello");
 *     return response;
 * });
 *
 * HttpResponse response = harness.get("/test");
 * assertEquals("hello", response.getBodyString());
 *
 * // With headers (e.g., cookies)
 * response = harness.request()
 *     .path("/api/test")
 *     .header("Cookie", "session=abc123")
 *     .get();
 * </pre>
 */
public class InMemoryTestHarness {

    private Function<HttpRequest, HttpResponse> handler;

    public InMemoryTestHarness() {
    }

    public InMemoryTestHarness(Function<HttpRequest, HttpResponse> handler) {
        this.handler = handler;
    }

    public void setHandler(Function<HttpRequest, HttpResponse> handler) {
        this.handler = handler;
    }

    /**
     * Execute a request directly against the handler, bypassing HTTP entirely.
     */
    public HttpResponse execute(HttpRequest request) {
        if (handler == null) {
            HttpResponse response = new HttpResponse();
            response.setStatus(404);
            response.setBody("no handler configured");
            return response;
        }
        try {
            return handler.apply(request);
        } catch (Exception e) {
            HttpResponse response = new HttpResponse();
            response.setStatus(500);
            response.setBody("error: " + e.getMessage());
            return response;
        }
    }

    /**
     * Execute a GET request.
     */
    public HttpResponse get(String path) {
        return request("GET", path, null);
    }

    /**
     * Execute a POST request.
     */
    public HttpResponse post(String path, Object body) {
        return request("POST", path, body);
    }

    /**
     * Execute a request with the given method, path, and optional body.
     */
    public HttpResponse request(String method, String path, Object body) {
        return request().path(path).body(body).method(method);
    }

    /**
     * Create a request builder for more complex requests (with headers, etc.)
     */
    public RequestBuilder request() {
        return new RequestBuilder();
    }

    /**
     * Returns a fake base URL for compatibility with code that expects URLs.
     * Since this is in-memory, no actual server is running.
     */
    public String getBaseUrl() {
        return "http://in-memory-test";
    }

    /**
     * Returns a fake port for compatibility. Always returns -1 since no server is running.
     */
    public int getPort() {
        return -1;
    }

    /**
     * Builder for constructing requests with headers, body, etc.
     */
    public class RequestBuilder {
        private String path;
        private Object body;
        private final Map<String, List<String>> headers = new HashMap<>();

        public RequestBuilder path(String path) {
            this.path = path;
            return this;
        }

        public RequestBuilder body(Object body) {
            this.body = body;
            return this;
        }

        public RequestBuilder header(String name, String value) {
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            return this;
        }

        public HttpResponse get() {
            return method("GET");
        }

        public HttpResponse post() {
            return method("POST");
        }

        public HttpResponse method(String method) {
            HttpRequest request = new HttpRequest();
            request.setMethod(method);
            request.setUrl(path);
            if (!headers.isEmpty()) {
                request.setHeaders(headers);
            }
            if (body != null) {
                if (body instanceof byte[]) {
                    request.setBody((byte[]) body);
                } else if (body instanceof String) {
                    request.setBody(FileUtils.toBytes((String) body));
                } else {
                    request.setBody(FileUtils.toBytes(body.toString()));
                }
            }
            return execute(request);
        }
    }

}
