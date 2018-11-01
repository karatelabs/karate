package com.intuit.karate.netty;

import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class WebSocketClientRunner  {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketClientRunner.class);    
    private String result;
    
    @Test
    public void testWebSockets() throws Exception {
        String url = "ws://echo.websocket.org";
        WebSocketClient client = new WebSocketClient(url, text -> {
            synchronized(this) {
                result = text;
                notify();
            }                        
        });
        client.send("hello world !");
        synchronized (this) {
            wait();
        }
        assertEquals("hello world !", result);
    }

}
