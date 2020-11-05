package com.intuit.karate.netty;

import com.intuit.karate.Logger;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class WebSocketTempRunner {
    
    private static final Logger logger = new Logger();

    @Test
    public void testWebSockets() throws Exception {
        String url = "ws://127.0.0.1:49156/e543365b10ee4dca01cdd99171a58c5d";
        WebSocketOptions options = new WebSocketOptions(url);
        options.setTextConsumer(text -> {
            logger.debug("<< {}", text);
        });
        WebSocketClient client = new WebSocketClient(options, logger);
        client.waitSync();
    }    
    
}
