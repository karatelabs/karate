package com.intuit.karate.http;

import com.intuit.karate.Match;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class HttpRequestBuilderTest {
    
    @Test
    public void testRemoveHeaderIgnoreCase() {
        HttpRequestBuilder request = new HttpRequestBuilder();
        request.setHeader("Content-Length", "100");
        Match.equals(request.getHeaders(), "{ 'Content-Length': ['100'] }");
        request.removeHeaderIgnoreCase("content-length");
        Match.equals(request.getHeaders(), "{}");
    }
    
}
