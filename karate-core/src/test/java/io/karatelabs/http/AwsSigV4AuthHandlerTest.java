package io.karatelabs.http;

import io.karatelabs.common.ResourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The handler applied through karate's HTTP client against an in-process server that
 * recomputes the signature from what arrived on the wire: host, path, query, the signed
 * headers and the body bytes.
 */
class AwsSigV4AuthHandlerTest {

    static final String ACCESS_KEY = "AKIDEXAMPLE";
    static final String SECRET_KEY = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";
    static final Pattern AUTHORIZATION = Pattern.compile("AWS4-HMAC-SHA256 Credential=([^/]+)/\\d{8}/([^/]+)/([^/]+)/aws4_request,"
            + " SignedHeaders=([^,]+), Signature=[0-9a-f]{64}");

    static HttpServer server;
    static String baseUrl;

    @BeforeAll
    static void startServer() {
        server = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            if (request.getPath().equals("/redirect")) {
                response.setStatus(302);
                response.setHeader("Location", baseUrl + "/prod/items");
                return response;
            }
            String failure = verify(request);
            response.setStatus(failure == null ? 200 : 403);
            response.setBody(failure == null ? "ok" : failure, ResourceType.TEXT);
            return response;
        });
        baseUrl = "http://localhost:" + server.getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    static String verify(HttpRequest request) {
        String authorization = request.getHeader("authorization");
        if (authorization == null) {
            return "no authorization header";
        }
        Matcher m = AUTHORIZATION.matcher(authorization);
        if (!m.matches()) {
            return "malformed authorization: " + authorization;
        }
        String service = m.group(3);
        Map<String, Object> config = new HashMap<>();
        config.put("accessKey", m.group(1));
        config.put("secretKey", SECRET_KEY);
        config.put("region", m.group(2));
        config.put("service", service);
        config.put("sessionToken", request.getHeader("x-amz-security-token"));
        Clock clock = Clock.fixed(AwsSigV4AuthHandler.AMZ_DATE.parse(request.getHeader("x-amz-date"), Instant::from), ZoneOffset.UTC);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : m.group(4).split(";")) {
            if (name.startsWith("x-amz-")) { // re-added by sign()
                continue;
            }
            headers.put(name, request.getHeaderValues(name));
        }
        String urlAndPath = request.getUrlAndPath();
        int q = urlAndPath.indexOf('?');
        String rawPath = q < 0 ? urlAndPath : urlAndPath.substring(0, q);
        String rawQuery = q < 0 ? null : urlAndPath.substring(q + 1);
        byte[] body = request.getBody() == null ? new byte[0] : request.getBody();
        AwsSigV4AuthHandler.Signed signed = new AwsSigV4AuthHandler(config, clock)
                .sign(request.getMethod(), rawPath, rawQuery, headers, body);
        if (!authorization.equals(signed.authorization())) {
            return "signature mismatch, server canonical request:\n" + signed.canonicalRequest();
        }
        if ("s3".equals(service) && !AwsSigV4AuthHandler.hex(AwsSigV4AuthHandler.sha256(body)).equals(request.getHeader("x-amz-content-sha256"))) {
            return "x-amz-content-sha256 mismatch";
        }
        return null;
    }

    static Map<String, Object> config(String service, String sessionToken) {
        Map<String, Object> config = new HashMap<>();
        config.put("accessKey", ACCESS_KEY);
        config.put("secretKey", SECRET_KEY);
        config.put("region", "us-east-1");
        config.put("service", service);
        config.put("sessionToken", sessionToken);
        return config;
    }

    @Test
    void testGetWithQueryParams() throws Exception {
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(new AwsSigV4AuthHandler(config("execute-api", null)));
            http.url(baseUrl + "/prod/items");
            http.param("filter", "a b&c=d");
            http.param("tag", "y", "x");
            http.param("unicode", "ሴ");
            http.param("empty", "");
            http.header("X-Custom", "  spaced   value  ");
            HttpResponse response = http.invoke("get");
            assertEquals(200, response.getStatus(), response.getBodyString());
            String authorization = response.getRequest().getHeader("Authorization");
            assertTrue(authorization.contains("SignedHeaders=host;x-amz-date;x-custom,"), authorization);
            assertNull(response.getRequest().getHeader("x-amz-security-token"));
        }
    }

    @Test
    void testPostJsonWithSessionToken() throws Exception {
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(new AwsSigV4AuthHandler(config("execute-api", "FwoGZXIvYXdzEBYaD/session+token==")));
            http.url(baseUrl + "/prod/items");
            http.bodyJson("{ name: 'hello', tags: ['a', 'b'], n: 1 }");
            HttpResponse response = http.invoke("post");
            assertEquals(200, response.getStatus(), response.getBodyString());
            String authorization = response.getRequest().getHeader("Authorization");
            assertTrue(authorization.contains("SignedHeaders=content-type;host;x-amz-date;x-amz-security-token,"), authorization);
        }
    }

    @Test
    void testS3PathSignedAsSent() throws Exception {
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(new AwsSigV4AuthHandler(config("s3", null)));
            http.url(baseUrl + "/bucket/dir//my%20key.txt");
            HttpResponse response = http.invoke("put", "hello");
            assertEquals(200, response.getStatus(), response.getBodyString());
            String authorization = response.getRequest().getHeader("Authorization");
            assertTrue(authorization.contains("/us-east-1/s3/aws4_request, SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date,"), authorization);
            assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    response.getRequest().getHeader("x-amz-content-sha256"));
        }
    }

    @Test
    void testPathSegments() throws Exception {
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(new AwsSigV4AuthHandler(config("execute-api", null)));
            http.url(baseUrl);
            http.paths("prod", "items", "my item", "$special");
            HttpResponse response = http.invoke("delete");
            assertEquals(200, response.getStatus(), response.getBodyString());
            assertTrue(response.getRequest().getUrlAndPath().contains("/prod/items/my%20item/"), response.getRequest().getUrlAndPath());
        }
    }

    @Test
    void testRedirectReturnedNotFollowed() throws Exception {
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(new AwsSigV4AuthHandler(config("execute-api", null)));
            http.url(baseUrl + "/redirect");
            HttpResponse response = http.invoke("get");
            assertEquals(302, response.getStatus(), response.getBodyString());
            assertEquals(baseUrl + "/prod/items", response.getHeader("Location"));
        }
    }

    @Test
    void testTransferEncodingNotSigned() throws Exception {
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(new AwsSigV4AuthHandler(config("execute-api", null)));
            http.url(baseUrl + "/prod/items");
            http.header("Transfer-Encoding", "chunked");
            http.bodyJson("{ name: 'hello' }");
            HttpResponse response = http.invoke("post");
            assertEquals(200, response.getStatus(), response.getBodyString());
            String authorization = response.getRequest().getHeader("Authorization");
            assertTrue(authorization.contains("SignedHeaders=content-type;host;x-amz-date,"), authorization);
        }
    }

    @Test
    void testNonAsciiHeaderValueRefused() throws Exception {
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(new AwsSigV4AuthHandler(config("execute-api", null)));
            http.url(baseUrl + "/prod/items");
            http.header("X-City", "東京");
            RuntimeException e = assertThrows(RuntimeException.class, () -> http.invoke("get"));
            assertEquals("auth type 'aws' cannot sign header 'X-City': the value is not ASCII", e.getMessage());
        }
    }

    @Test
    void testWrongSecretRejected() throws Exception {
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            Map<String, Object> config = config("execute-api", null);
            config.put("secretKey", "not-the-key");
            http.auth(new AwsSigV4AuthHandler(config));
            http.url(baseUrl + "/prod/items");
            HttpResponse response = http.invoke("get");
            assertEquals(403, response.getStatus());
            assertTrue(response.getBodyString().startsWith("signature mismatch"), response.getBodyString());
        }
    }

    @Test
    void testMissingConfig() {
        Map<String, Object> config = config("s3", null);
        config.remove("region");
        RuntimeException e = assertThrows(RuntimeException.class, () -> new AwsSigV4AuthHandler(config));
        assertEquals("auth type 'aws' requires 'region'", e.getMessage());
        assertEquals("aws", new AwsSigV4AuthHandler(config("s3", null)).getType());
    }

}
