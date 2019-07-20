package com.intuit.karate.netty;


import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class HostAndPortTest {
    
    private static void test(String uri, boolean ssl, String host, int port) {
        ProxyContext hp = new ProxyContext(uri, ssl);
        assertEquals(host, hp.host);
        assertEquals(port, hp.port);
    }
    
    @Test
    public void testHostAndPort() {
        test("http://localhost:8080", false, "localhost", 8080);
        test("localhost:8080", false, "localhost", 8080);
        test("localhost", false, "localhost", 80);
        test("http://localhost", false, "localhost", 80);
    }
    
}
