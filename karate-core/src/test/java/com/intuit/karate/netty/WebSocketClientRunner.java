package com.intuit.karate.netty;

import org.junit.Test;

import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author pthomas3
 */
public class WebSocketClientRunner  {
    private String result;
    
    @Test
    public void testWebSockets() throws Exception {
        String url = "ws://echo.websocket.org";
        Consumer<String> textMsgHandler = text -> {
            synchronized(this) {
                result = text;
                notify();
            }
        };
        WebSocketClient client = new WebSocketClient(url, null, Optional.of(textMsgHandler));
        client.send("hello world !");
        synchronized (this) {
            wait();
        }
        assertEquals("hello world !", result);
    }

}
