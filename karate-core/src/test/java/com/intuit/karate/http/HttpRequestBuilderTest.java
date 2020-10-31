package com.intuit.karate.http;

import com.intuit.karate.Match;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class HttpRequestBuilderTest {

    @Test
    void testRemoveHeaderIgnoreCase() {
        HttpRequestBuilder request = new HttpRequestBuilder();
        request.setHeader("Content-Length", "100");
        Match.equals(request.getHeaders(), "{ 'Content-Length': ['100'] }");
        request.removeHeaderIgnoreCase("content-length");
        Match.equals(request.getHeaders(), "{}");
    }

    @Test
    void testGetUrlAndPath() {
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
