package com.intuit.karate.netty;

import com.intuit.karate.Logger;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class WebSocketProxyRunner {
    
    private static final Logger logger = new Logger();
    
    @Test
    public void testProxy() {
        String url = "ws://127.0.0.1:4444/6b3c40c9ab70bb96c82c68ae580f0a53";
        String path = url.substring(url.lastIndexOf('/') + 1);
        logger.debug("path: {}", path);
        WebSocketProxyServer server = new WebSocketProxyServer(8090, path, url);
        server.waitSync();        
    }
    
}
