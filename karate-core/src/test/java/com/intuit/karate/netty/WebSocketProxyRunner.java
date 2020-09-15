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
        String url = "ws://127.0.0.1:49156/e543365b10ee4dca01cdd99171a58c5d";
        String path = url.substring(url.lastIndexOf('/') + 1);
        logger.debug("path: {}", path);
        WebSocketServer server = new WebSocketServer(8080, path, url);
        server.waitSync();        
    }
    
}
