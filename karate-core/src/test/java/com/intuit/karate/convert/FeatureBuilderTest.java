package com.intuit.karate.convert;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by rkumar32 on 7/5/17.
 */
public class FeatureBuilderTest {
    private static final Logger logger = LoggerFactory.getLogger(FeatureBuilderTest.class);

    @Test
    public void testConverting() {
        List<PostmanRequest> requests = PostmanCollectionReader.parse("src/test/java/postman-echo-single.postman_collection");
        logger.debug("list: {}", requests);
        assertEquals(1, requests.size());
        PostmanRequest request = requests.get(0);
        FeatureBuilder builder = new FeatureBuilder();
        builder.addName(request.getName());
        assertEquals("OAuth1.0 Verify Signature\n", builder.getName());
        builder.addUrl(request.getUrl());
        assertEquals("'https://echo.getpostman.com/oauth1'\n", builder.getUrl());
        builder.addHeaders(request.getHeaders());
        String authorizationValue = "OAuth oauth_consumer_key=\"RKCGzna7bv9YD57c\",oauth_signature_method=\"HMAC-SHA1\",oauth_timestamp=\"1442394747\",oauth_nonce=\"UIGipk\",oauth_version=\"1.0\",oauth_signature=\"CaeyGPr2mns1WCq4Cpm5aLvz6Gs=\"";
        assertEquals("And header Authorization = '"+ authorizationValue + "'\n", builder.getHeaders());
        builder.addMethod(request.getMethod());
        assertEquals("GET\n", builder.getMethod());
        builder.addBody(request.getBody());
        logger.debug(builder.getBody());
    }
}
