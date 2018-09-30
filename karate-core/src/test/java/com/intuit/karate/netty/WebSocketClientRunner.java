package com.intuit.karate.netty;

import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class WebSocketClientRunner implements WebSocketListener {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketClientRunner.class);
    
    @Override
    public void onMessage(String text) {
        logger.debug("*** {}", text);
    }

    @Override
    public void onMessage(byte[] bytes) {
        logger.debug("*** binary {}", FileUtils.toString(bytes));
    }    
    
    @Test
    public void testWebSockets() throws Exception {
        String url = "ws://localhost:9222/devtools/page/5133DAD1D14C8F11D17A83E91D70B2A9";
        WebSocketClient client = new WebSocketClient(url, this);
        String json = "{id: 1, method: 'Page.navigate', params: {url: 'https://www.github.com/'}}";        
        client.send(JsonUtils.toStrictJsonString(json));
        client.close();
    }

}
