package com.intuit.karate.http;

import com.intuit.karate.Logger;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class WebSocketProxyRunner {

    static final Logger logger = new Logger();

    @Test
    void testProxy() {
        String url = "ws://127.0.0.1:4444/22c71715e7433fffe615b0b9b2583169";
        String path = url.substring(url.lastIndexOf('/'));
        logger.debug("path: {}", path);
        WebSocketProxyServer server = new WebSocketProxyServer(8090, url, path);
        server.waitSync();
    }

}
