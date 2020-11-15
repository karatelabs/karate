package com.intuit.karate.fatjar;

import com.intuit.karate.http.ProxyServer;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ProxyServerRunner {

    @Test
    void testProxy() {
        ProxyServer proxy = new ProxyServer(5000, req -> {
            System.out.println("*** " + req.uri());
            return null;
        }, null);
        proxy.waitSync();
    }

}
