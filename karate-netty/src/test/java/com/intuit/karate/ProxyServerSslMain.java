package com.intuit.karate;

import com.intuit.karate.netty.ProxyServer;
import java.io.File;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ProxyServerSslMain {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ProxyServerSslMain.class);

    private String html = FileUtils.toString(new File("src/test/java/com/intuit/karate/temp.html"));

    @Test
    public void testProxy() {
        ProxyServer server = new ProxyServer(8090,
                req -> {
                    if ("httpbin.org".equals(req.context.host)) {
                        return req.fake(200, html).header("Content-Type", "text/html");
                    }
                    return null;
                },
                res -> {
                    if ("corte.si".equals(res.context.host) && res.uri().contains("/index.html")) {
                        return res.fake(200, html).header("Content-Type", "text/html");
                    }
                    return null;
                });
        server.waitSync();
    }

}
