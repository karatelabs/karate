package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BasicAuthHandlerTest {

    @Test
    void testBasicAuthHeader() {
        // Test handler directly without HTTP server
        BasicAuthHandler handler = new BasicAuthHandler("admin", "password");

        HttpRequestBuilder builder = new HttpRequestBuilder(null); // client not needed for header test
        handler.apply(builder);

        // Verify the Authorization header is correctly set
        String authHeader = builder.getHeaders().get("Authorization");
        assertEquals("Basic YWRtaW46cGFzc3dvcmQ=", authHeader);
    }

    @Test
    void testBasicAuthType() {
        BasicAuthHandler handler = new BasicAuthHandler("user", "pass");
        assertEquals("basic", handler.getType());
    }

    @Test
    void testBasicAuthCurlPreview() {
        BasicAuthHandler handler = new BasicAuthHandler("admin", "secret");
        String preview = handler.toCurlPreview("linux");
        assertEquals("-u 'admin:secret'", preview);
    }

}
