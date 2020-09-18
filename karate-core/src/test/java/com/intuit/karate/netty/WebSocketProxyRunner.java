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
        String url = "ws://127.0.0.1:60168/97d3d6788b8e1063c0af0aae463f8d6d";
        String path = url.substring(url.lastIndexOf('/') + 1);
        logger.debug("path: {}", path);
        WebSocketProxyServer server = new WebSocketProxyServer(8090, path, url);
        server.waitSync();        
    }
    
}
