package com.intuit.karate.fatjar;

import com.intuit.karate.http.ProxyServer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class ProxyServerRunner {

    private static final Logger logger = LoggerFactory.getLogger(ProxyServerRunner.class);

    @Test
    void testProxy() {
        ProxyServer proxy = new ProxyServer(5000, req -> {
            logger.info("*** {}", req.uri());
            return null;
        }, null);
        proxy.waitSync();
    }

}
