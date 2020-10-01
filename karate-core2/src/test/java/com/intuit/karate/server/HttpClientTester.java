package com.intuit.karate.server;

import com.intuit.karate.server.HttpClient;
import com.intuit.karate.FileUtils;
import com.intuit.karate.server.Response;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class HttpClientTester {
    
    static final Logger logger = LoggerFactory.getLogger(HttpClientTester.class);
    
    @Test
    void testGet() {
        HttpClient http = new HttpClient(null, "https://google.com");
        Response response = http.invoke();
        String body = FileUtils.toString(response.getBody());
        logger.debug("response: {}", body);
    }    
    
    
}
