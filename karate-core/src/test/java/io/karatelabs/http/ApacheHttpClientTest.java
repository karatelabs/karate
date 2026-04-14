package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApacheHttpClientTest {

    @Test
    void testMatchNonProxyHostExact() {
        assertTrue(ApacheHttpClient.matchNonProxyHost("localhost", "localhost"));
        assertTrue(ApacheHttpClient.matchNonProxyHost("my-api.com", "my-api.com"));
        assertFalse(ApacheHttpClient.matchNonProxyHost("localhost", "other-host"));
    }

    @Test
    void testMatchNonProxyHostExactCaseInsensitive() {
        assertTrue(ApacheHttpClient.matchNonProxyHost("Localhost", "localhost"));
        assertTrue(ApacheHttpClient.matchNonProxyHost("MY-API.COM", "my-api.com"));
    }

    @Test
    void testMatchNonProxyHostWildcardPrefix() {
        // *.example.com should match any subdomain
        assertTrue(ApacheHttpClient.matchNonProxyHost("*.example.com", "api.example.com"));
        assertTrue(ApacheHttpClient.matchNonProxyHost("*.example.com", "www.example.com"));
        assertFalse(ApacheHttpClient.matchNonProxyHost("*.example.com", "example.com"));
        assertFalse(ApacheHttpClient.matchNonProxyHost("*.example.com", "other.com"));
    }

    @Test
    void testMatchNonProxyHostWildcardSuffix() {
        // 192.168.* should match any host starting with that prefix
        assertTrue(ApacheHttpClient.matchNonProxyHost("192.168.*", "192.168.1.1"));
        assertTrue(ApacheHttpClient.matchNonProxyHost("192.168.*", "192.168.0.100"));
        assertFalse(ApacheHttpClient.matchNonProxyHost("192.168.*", "10.0.0.1"));
    }

    @Test
    void testMatchNonProxyHostNulls() {
        assertFalse(ApacheHttpClient.matchNonProxyHost(null, "host"));
        assertFalse(ApacheHttpClient.matchNonProxyHost("pattern", null));
        assertFalse(ApacheHttpClient.matchNonProxyHost(null, null));
    }

}
