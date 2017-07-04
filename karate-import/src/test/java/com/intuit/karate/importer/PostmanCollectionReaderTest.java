package com.intuit.karate.importer;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class PostmanCollectionReaderTest {
    
     private static final Logger logger = LoggerFactory.getLogger(PostmanCollectionReaderTest.class);
    
    @Test
    public void testReading() {
        List<PostmanRequest> requests = PostmanCollectionReader.parse("src/test/resources/postman-echo-single.postman_collection");
        logger.debug("list: {}", requests);
        assertEquals(1, requests.size());
        assertEquals("Set Cookies", requests.get(0).getName());
    }
    
}
