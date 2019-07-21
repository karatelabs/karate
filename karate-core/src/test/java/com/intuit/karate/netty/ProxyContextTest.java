package com.intuit.karate.netty;


import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class ProxyContextTest {
    
    private static void test(String uri, boolean ssl, String host, int port) {
        ProxyContext hp = new ProxyContext(uri, ssl);
        assertEquals(host, hp.host);
        assertEquals(port, hp.port);
    }
    
    @Test
    public void testProxyContext() {
        test("http://localhost:8080", false, "localhost", 8080);
        test("http://localhost:8080/foo", false, "localhost", 8080);
        test("localhost:8080", false, "localhost", 8080);
        test("localhost:8080/foo", false, "localhost", 8080);
        test("localhost", false, "localhost", 80);
        test("localhost/foo", false, "localhost", 80);
        test("http://localhost", false, "localhost", 80);
        test("http://localhost/foo", false, "localhost", 80);
        test("httpbin.org:443", false, "httpbin.org", 443);
        test("httpbin.org:443", true, "httpbin.org", 443);
        test("httpbin.org:443/foo", true, "httpbin.org", 443);
        test("httpbin.org", true, "httpbin.org", 443);
        test("httpbin.org/foo", true, "httpbin.org", 443);
    }
    
}
