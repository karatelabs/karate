package com.intuit.karate.runtime;

import com.intuit.karate.server.*;
import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class MockHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(MockHandlerTest.class);

    MockHandler handler;
    FeatureBuilder feature;
    HttpRequestBuilder request;
    Response response;

    @BeforeEach
    void beforeEach() {
        request = new HttpRequestBuilder(null).method("GET");
    }

    FeatureBuilder background(String... lines) {
        feature = FeatureBuilder.background(lines);
        return feature;
    }

    private Response handle() {
        handler = new MockHandler(feature.build());
        response = handler.handle(request.build().toRequest());
        request = new HttpRequestBuilder(null).method("GET");
        return response;
    }

    private void match(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }

    @Test
    void testSimpleResponse() {
        background().scenario("pathMatches('/hello')", "def response = 'hello world'");
        request.path("/hello");
        handle();
        match(response.getBodyAsString(), "hello world");
    }

    @Test
    void testPathParams() {
        background().scenario("pathMatches('/hello/{name}')", "def response = 'hello ' + pathParams.name");
        request.path("/hello/john");
        handle();
        match(response.getBodyAsString(), "hello john");
    }

}
