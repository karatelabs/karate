package com.intuit.karate.formats.postman;

import com.intuit.karate.FileUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author pthomas3
 */
public class ConvertUtilsTest {

    private static final Logger logger = LoggerFactory.getLogger(ConvertUtilsTest.class);

    @Test
    public void testReadingSinglePostmanItemWithOneRequest() {
        InputStream is = getClass().getResourceAsStream("postman-echo-single.postman_collection");
        String json = FileUtils.toString(is);
        List<PostmanItem> items = PostmanUtils.readPostmanJson(json);
        logger.debug("list: {}", items);
        assertEquals(1, items.size());
        PostmanItem item = items.get(0);
        PostmanRequest request = item.getRequest();
        assertEquals("OAuth1.0 Verify Signature", item.getName());
        assertEquals("https://echo.getpostman.com/oauth1", request.getUrl());
        assertEquals("GET", request.getMethod());
        assertEquals(1, request.getHeaders().size());
        assertEquals("OAuth oauth_consumer_key=\"RKCGzna7bv9YD57c\",oauth_signature_method=\"HMAC-SHA1\",oauth_timestamp=\"1442394747\",oauth_nonce=\"UIGipk\",oauth_version=\"1.0\",oauth_signature=\"CaeyGPr2mns1WCq4Cpm5aLvz6Gs=\"", request.getHeaders().get("Authorization"));
        logger.debug(request.getBody());
    }

    @Test
    public void testReadingItemListWithSubItems() {
        String collectionFileName = "postman-multiple-items-and-sub-items.postman_collection";
        InputStream is = getClass().getResourceAsStream(collectionFileName);
        String json = FileUtils.toString(is);
        List<PostmanItem> items = PostmanUtils.readPostmanJson(json);
        logger.debug("list: {}", items);
        String featureJson = PostmanUtils.toKarateFeature(collectionFileName, items).trim();
        assertTrue(featureJson.startsWith("Feature: " + collectionFileName)); // assert feature name
        assertTrue(featureJson.contains("Scenario: rootItem-1")); // assert scenario names
        assertTrue(featureJson.contains("Scenario: rootItem-2"));
        assertTrue(featureJson.contains("Scenario: rootItem-3"));
        assertTrue(featureJson.contains("# subitem-1-1")); // assert comment for each sub request
        assertTrue(featureJson.contains("# subitem-1-2"));
        assertTrue(featureJson.contains("# subitem-2-1"));
    }

}
