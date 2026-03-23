package io.karatelabs.http;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpRequestBuilderCurlTest {

    static final Logger logger = LoggerFactory.getLogger(HttpRequestBuilderCurlTest.class);

    @Test
    void testSimpleGetRequest() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/users");
        http.method("GET");

        String curl = http.toCurlCommand();
        logger.debug("Simple GET:\n{}", curl);

        assertTrue(curl.contains("curl -X GET"));
        assertTrue(curl.contains("'https://api.example.com/users'"));
    }

    @Test
    void testGetWithQueryParams() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/search");
        http.param("q", "test query");
        http.param("limit", "10");
        http.method("GET");

        String curl = http.toCurlCommand();
        logger.debug("GET with query params:\n{}", curl);

        assertTrue(curl.contains("curl -X GET"));
        assertTrue(curl.contains("api.example.com/search"));
        assertTrue(curl.contains("q=test"));
        assertTrue(curl.contains("limit=10"));
    }

    @Test
    void testPostWithJsonBody() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/users");
        http.method("POST");
        http.header("Content-Type", "application/json");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "John Doe");
        body.put("email", "john@example.com");
        http.body(body);

        String curl = http.toCurlCommand();
        logger.debug("POST with JSON:\n{}", curl);

        assertTrue(curl.contains("curl -X POST"));
        assertTrue(curl.contains("-H 'Content-Type: application/json'"));
        assertTrue(curl.contains("-d '{\"name\":\"John Doe\""));
        assertTrue(curl.contains("\"email\":\"john@example.com\"}'"));
    }

    @Test
    void testPostWithJsonBodyContainingSingleQuote() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/users");
        http.method("POST");
        http.header("Content-Type", "application/json");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "O'Brien");
        body.put("message", "It's a test");
        http.body(body);

        String curl = http.toCurlCommand();
        logger.debug("POST with single quotes in JSON:\n{}", curl);

        assertTrue(curl.contains("curl -X POST"));
        // Should properly escape single quotes
        assertTrue(curl.contains("O'\\''Brien") || curl.contains("O'\\'"));
    }

    @Test
    void testPostWithFormData() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/login");
        http.method("POST");
        http.formField("username", "admin");
        http.formField("password", "secret123");

        String curl = http.toCurlCommand();
        logger.debug("POST with form data:\n{}", curl);

        assertTrue(curl.contains("curl -X POST"));
        assertTrue(curl.contains("--data-urlencode"));
        assertTrue(curl.contains("username=admin"));
        assertTrue(curl.contains("password=secret123"));
    }

    @Test
    void testPostWithMultipartFormData() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/upload");
        http.method("POST");

        Map<String, Object> part1 = new HashMap<>();
        part1.put("name", "title");
        part1.put("value", "My Document");
        http.multiPart(part1);

        Map<String, Object> part2 = new HashMap<>();
        part2.put("name", "description");
        part2.put("value", "A test document");
        http.multiPart(part2);

        String curl = http.toCurlCommand();
        logger.debug("POST with multipart:\n{}", curl);

        assertTrue(curl.contains("curl -X POST"));
        assertTrue(curl.contains("-F"));
        assertTrue(curl.contains("title=My Document"));
        assertTrue(curl.contains("description=A test document"));
    }

    @Test
    void testPostWithUrlEncodedParams() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/login");
        http.method("POST");
        http.param("username", "admin");
        http.param("password", "pass@word!");
        http.header("Content-Type", "application/x-www-form-urlencoded");

        String curl = http.toCurlCommand();
        logger.debug("POST with URL-encoded params:\n{}", curl);

        assertTrue(curl.contains("curl -X POST"));
        assertTrue(curl.contains("--data-urlencode"));
        assertTrue(curl.contains("username=admin"));
        assertTrue(curl.contains("password=pass@word!"));
    }

    @Test
    void testWithHeaders() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/data");
        http.method("GET");
        http.header("Authorization", "Bearer token123");
        http.header("X-Custom-Header", "custom-value");
        http.header("Accept", "application/json");

        String curl = http.toCurlCommand();
        logger.debug("GET with headers:\n{}", curl);

        assertTrue(curl.contains("-H 'Authorization: Bearer token123'"));
        assertTrue(curl.contains("-H 'X-Custom-Header: custom-value'"));
        assertTrue(curl.contains("-H 'Accept: application/json'"));
    }

    @Test
    void testHeadersAreNotDuplicated() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/data");
        http.method("GET");
        http.header("Host", "example.com"); // Should be ignored
        http.header("User-Agent", "TestAgent"); // Should be ignored
        http.header("Content-Length", "123"); // Should be ignored
        http.header("Accept", "application/json"); // Should be included

        String curl = http.toCurlCommand();
        logger.debug("GET with filtered headers:\n{}", curl);

        assertFalse(curl.contains("Host:"));
        assertFalse(curl.contains("User-Agent:"));
        assertFalse(curl.contains("Content-Length:"));
        assertTrue(curl.contains("-H 'Accept: application/json'"));
    }

    @Test
    void testPostWithStringBody() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/data");
        http.method("POST");
        http.header("Content-Type", "text/plain");
        http.body("This is plain text");

        String curl = http.toCurlCommand();
        logger.debug("POST with string body:\n{}", curl);

        assertTrue(curl.contains("curl -X POST"));
        assertTrue(curl.contains("-d 'This is plain text'"));
    }

    @Test
    void testPostWithBinaryBody() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/data");
        http.method("POST");
        http.header("Content-Type", "application/octet-stream");
        http.body(new byte[]{0x01, 0x02, 0x03});

        String curl = http.toCurlCommand();
        logger.debug("POST with binary body:\n{}", curl);

        assertTrue(curl.contains("curl -X POST"));
        assertTrue(curl.contains("[binary data]"));
    }

    @Test
    void testSpecialCharactersInUrl() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/search");
        http.param("q", "test & special");
        http.method("GET");

        String curl = http.toCurlCommand();
        logger.debug("GET with special chars in URL:\n{}", curl);

        assertTrue(curl.contains("curl -X GET"));
        // URL should be properly encoded
        assertTrue(curl.contains("q=test"));
    }

    @Test
    void testComplexJsonBody() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/complex");
        http.method("POST");
        http.header("Content-Type", "application/json");

        Map<String, Object> body = new HashMap<>();
        body.put("string", "value");
        body.put("number", 42);
        body.put("boolean", true);
        body.put("null", null);

        Map<String, Object> nested = new HashMap<>();
        nested.put("nested", "data");
        body.put("object", nested);

        http.body(body);

        String curl = http.toCurlCommand();
        logger.debug("POST with complex JSON:\n{}", curl);

        assertTrue(curl.contains("curl -X POST"));
        assertTrue(curl.contains("\"string\":\"value\""));
        assertTrue(curl.contains("\"number\":42"));
        assertTrue(curl.contains("\"boolean\":true"));
        assertTrue(curl.contains("\"nested\":\"data\""));
    }

    @Test
    void testMultipleHeaderValues() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/data");
        http.method("GET");
        http.header("Accept", "application/json", "text/html");

        String curl = http.toCurlCommand();
        logger.debug("GET with multiple header values:\n{}", curl);

        assertTrue(curl.contains("-H 'Accept: application/json'"));
        assertTrue(curl.contains("-H 'Accept: text/html'"));
    }

    @Test
    void testFormattingAndLineBreaks() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/data");
        http.method("POST");
        http.header("Authorization", "Bearer token");
        http.header("Content-Type", "application/json");

        Map<String, Object> body = new HashMap<>();
        body.put("key", "value");
        http.body(body);

        String curl = http.toCurlCommand();
        logger.debug("POST with formatting:\n{}", curl);

        // Should have proper line continuations
        assertTrue(curl.contains(" \\"));
        // Should start with curl -X METHOD
        assertTrue(curl.startsWith("curl -X POST"));
    }

    @Test
    void testPostWithParamsWithoutBody() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/form");
        http.method("POST");
        http.param("field1", "value1");
        http.param("field2", "value2");

        String curl = http.toCurlCommand();
        logger.debug("POST with params (no body):\n{}", curl);

        assertTrue(curl.contains("curl -X POST"));
        // Should treat params as form data for POST without body
        assertTrue(curl.contains("field1=value1"));
        assertTrue(curl.contains("field2=value2"));
    }

    @Test
    void testWithBasicAuth() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/secure");
        http.method("GET");
        http.auth(new BasicAuthHandler("john", "secret123"));

        String curl = http.toCurlCommand();
        logger.debug("GET with basic auth:\n{}", curl);

        assertTrue(curl.contains("curl -X GET"));
        // Should use curl's native -u flag
        assertTrue(curl.contains("-u 'john:secret123'"));
        // Should NOT include Authorization header
        assertFalse(curl.contains("Authorization:"));
    }

    @Test
    void testWithBasicAuthSpecialChars() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/secure");
        http.method("GET");
        http.auth(new BasicAuthHandler("user@domain", "p@ss'word"));

        String curl = http.toCurlCommand();
        logger.debug("GET with basic auth (special chars):\n{}", curl);

        assertTrue(curl.contains("curl -X GET"));
        // Should properly escape single quotes in password
        assertTrue(curl.contains("-u 'user@domain:p@ss'\\''word'") ||
                   curl.contains("-u 'user@domain:p@ss'\\'"));
        assertFalse(curl.contains("Authorization:"));
    }

    @Test
    void testWithBearerAuth() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/secure");
        http.method("GET");
        http.auth(new BearerAuthHandler("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));

        String curl = http.toCurlCommand();
        logger.debug("GET with bearer auth:\n{}", curl);

        assertTrue(curl.contains("curl -X GET"));
        // Bearer auth should use Authorization header (default behavior)
        assertTrue(curl.contains("-H 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9'"));
    }

    @Test
    void testWithBasicAuthAndOtherHeaders() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/secure");
        http.method("GET");
        http.auth(new BasicAuthHandler("admin", "admin123"));
        http.header("Accept", "application/json");
        http.header("X-Custom-Header", "value");

        String curl = http.toCurlCommand();
        logger.debug("GET with basic auth and headers:\n{}", curl);

        assertTrue(curl.contains("-u 'admin:admin123'"));
        assertTrue(curl.contains("-H 'Accept: application/json'"));
        assertTrue(curl.contains("-H 'X-Custom-Header: value'"));
        assertFalse(curl.contains("Authorization:"));
    }

    @Test
    void testPreviewModeWithBasicAuth() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/secure");
        http.method("GET");
        http.auth(new BasicAuthHandler("user", "pass"));

        String curl = http.toCurlCommandPreview();
        logger.debug("Preview mode with basic auth:\n{}", curl);

        // Basic auth doesn't need network, so preview should be same as normal
        assertTrue(curl.contains("-u 'user:pass'"));
        assertFalse(curl.contains("Authorization:"));
    }

    @Test
    void testPreviewModeWithClientCredentials() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/resource");
        http.method("GET");

        Map<String, Object> config = new HashMap<>();
        config.put("url", "https://oauth.example.com/token");
        config.put("client_id", "my-client");
        config.put("client_secret", "my-secret");
        http.auth(new ClientCredentialsAuthHandler(config));

        String curl = http.toCurlCommandPreview();
        logger.debug("Preview mode with OAuth2 client credentials:\n{}", curl);

        // Should show placeholder without making network call
        assertTrue(curl.contains("curl -X GET"));
        assertTrue(curl.contains("-H 'Authorization: Bearer <your-oauth2-access-token>'"));
        // Should NOT have triggered the token fetch
    }

    @Test
    void testPreviewModeWithBearerAuth() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/secure");
        http.method("GET");
        http.auth(new BearerAuthHandler("existing-token-123"));

        String curl = http.toCurlCommandPreview();
        logger.debug("Preview mode with bearer auth:\n{}", curl);

        // Bearer auth with existing token should show normally
        assertTrue(curl.contains("-H 'Authorization: Bearer existing-token-123'"));
    }

    @Test
    void testPreviewModeWithoutAuth() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/public");
        http.method("GET");
        http.header("Accept", "application/json");

        String curl = http.toCurlCommandPreview();
        logger.debug("Preview mode without auth:\n{}", curl);

        assertTrue(curl.contains("curl -X GET"));
        assertTrue(curl.contains("'https://api.example.com/public'"));
        assertTrue(curl.contains("-H 'Accept: application/json'"));
    }

    @Test
    void testPreviewModeWithJsonBody() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/data");
        http.method("POST");
        http.header("Content-Type", "application/json");

        Map<String, Object> body = new HashMap<>();
        body.put("key", "value");
        http.body(body);

        String curl = http.toCurlCommandPreview();
        logger.debug("Preview mode with JSON body:\n{}", curl);

        assertTrue(curl.contains("curl -X POST"));
        assertTrue(curl.contains("-d '{\"key\":\"value\"}'"));
    }

    // Platform-specific tests

    @Test
    void testWindowsCmdPlatform() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/users");
        http.method("POST");
        http.header("Content-Type", "application/json");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "John Doe");
        http.body(body);

        String curl = http.toCurlCommand("cmd");
        logger.debug("Windows CMD format:\n{}", curl);

        // Should use double quotes instead of single quotes
        assertTrue(curl.contains("\"https://api.example.com/users\""));
        assertTrue(curl.contains("-H \"Content-Type: application/json\""));
        assertTrue(curl.contains("-d \"{\"\"name\"\":\"\"John Doe\"\"}\""));
        // Should use ^ for line continuation
        assertTrue(curl.contains(" ^\n"));
        assertFalse(curl.contains(" \\\n"));
    }

    @Test
    void testPowerShellPlatform() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/users");
        http.method("POST");
        http.header("Authorization", "Bearer token123");

        Map<String, Object> body = new HashMap<>();
        body.put("message", "It's a test");
        http.body(body);

        String curl = http.toCurlCommand("ps");
        logger.debug("PowerShell format:\n{}", curl);

        // Should use single quotes (PowerShell style)
        assertTrue(curl.contains("'https://api.example.com/users'"));
        assertTrue(curl.contains("-H 'Authorization: Bearer token123'"));
        // Single quotes in JSON should be doubled for PowerShell
        assertTrue(curl.contains("It''s a test"));
        // Should use ` (backtick) for line continuation
        assertTrue(curl.contains(" `\n"));
        assertFalse(curl.contains(" \\\n"));
    }

    @Test
    void testWindowsCmdWithBasicAuth() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/secure");
        http.method("GET");
        http.auth(new BasicAuthHandler("admin", "pass@123"));

        String curl = http.toCurlCommand("cmd");
        logger.debug("Windows CMD with basic auth:\n{}", curl);

        // Should use double quotes
        assertTrue(curl.contains("-u \"admin:pass@123\""));
        assertTrue(curl.contains("\"https://api.example.com/secure\""));
        // Should use ^ for line continuation
        assertTrue(curl.contains(" ^\n"));
    }

    @Test
    void testPowerShellWithSpecialCharacters() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("https://api.example.com/data");
        http.method("POST");
        http.body("Test's data with 'quotes'");

        String curl = http.toCurlCommand("ps");
        logger.debug("PowerShell with special chars:\n{}", curl);

        // PowerShell escapes single quotes by doubling them
        assertTrue(curl.contains("Test''s data with ''quotes''"));
        // Should use backtick for line continuation
        assertTrue(curl.contains(" `\n"));
    }
}
