package com.intuit.karate.http;

import com.intuit.karate.Match;
import org.junit.Test;
import static org.junit.Assert.*;

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
    
    @Test
    public void testGetUrlAndPath() {
        HttpRequestBuilder request = new HttpRequestBuilder();
        request.setUrl("http://foo");
        assertEquals("http://foo/", request.getUrlAndPath());
        request = new HttpRequestBuilder();
        request.setUrl("http://foo");
        request.addPath("bar");
        request.addPath("baz");
        assertEquals("http://foo/bar/baz", request.getUrlAndPath());      
    }
    
}
