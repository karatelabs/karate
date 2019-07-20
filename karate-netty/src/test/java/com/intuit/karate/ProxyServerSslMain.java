package com.intuit.karate;

import com.intuit.karate.netty.ProxyServer;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ProxyServerSslMain {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ProxyServerSslMain.class);

    @Test
    public void testProxy() {
        ProxyServer server = new ProxyServer(8090);
        server.waitSync();
    }

}
