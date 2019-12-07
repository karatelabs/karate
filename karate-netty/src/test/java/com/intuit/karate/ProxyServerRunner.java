package com.intuit.karate;

import com.intuit.karate.netty.ProxyServer;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ProxyServerRunner {
    
    @Test
    public void testProxy() {
        ProxyServer proxy = new ProxyServer(5000, req -> { System.out.println("*** " + req.uri()); return null; } , null);
        proxy.waitSync();
    }
    
}
