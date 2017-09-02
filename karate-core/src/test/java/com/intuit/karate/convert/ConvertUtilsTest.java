package com.intuit.karate.convert;

import com.intuit.karate.FileUtils;
import java.io.InputStream;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ConvertUtilsTest {
    
     private static final Logger logger = LoggerFactory.getLogger(ConvertUtilsTest.class);
    
    @Test
    public void testReading() {
        InputStream is = getClass().getResourceAsStream("postman-echo-single.postman_collection");
        String json = FileUtils.toString(is);
        List<PostmanRequest> requests = ConvertUtils.readPostmanJson(json);
        logger.debug("list: {}", requests);
        assertEquals(1, requests.size());
        PostmanRequest request = requests.get(0);
        assertEquals("OAuth1.0 Verify Signature", request.getName());
        assertEquals("https://echo.getpostman.com/oauth1", request.getUrl());
        assertEquals("GET", request.getMethod());
        assertEquals(1, request.getHeaders().size());
        assertEquals("OAuth oauth_consumer_key=\"RKCGzna7bv9YD57c\",oauth_signature_method=\"HMAC-SHA1\",oauth_timestamp=\"1442394747\",oauth_nonce=\"UIGipk\",oauth_version=\"1.0\",oauth_signature=\"CaeyGPr2mns1WCq4Cpm5aLvz6Gs=\"", request.getHeaders().get("Authorization"));
        logger.debug(request.getBody());
    }
    
}
