package com.intuit.karate.http;

import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import com.intuit.karate.core.Config;
import com.intuit.karate.core.DummyClient;
import com.intuit.karate.core.MockHandler;
import com.intuit.karate.core.Variable;
import com.intuit.karate.shell.StringLogAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intuit.karate.TestUtils.FeatureBuilder;
import static com.intuit.karate.TestUtils.match;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test body and content type handling for request and response logging.
 * @author edwardsph
 */
class HttpLoggerTest {

    HttpClient client = new DummyClient();
    MockHandler handler;
    FeatureBuilder feature;
    HttpRequestBuilder httpRequestBuilder;
    HttpRequest request;
    Logger testLogger = new Logger();
    Config config;
    LogAppender logAppender = new StringLogAppender(false);
    HttpLogger httpLogger;

    private static final String TURTLE_SAMPLE = "<http://example.org/hello> <http://example.org/#linked> <http://example.org/world> .";

    @BeforeEach
    void beforeEach() {
        httpRequestBuilder = new HttpRequestBuilder(client).method("GET");
        testLogger.setAppender(logAppender);
        httpLogger = new HttpLogger(testLogger);
        config = new Config();
    }

    void setup(String path, String body, String contentType) {
        feature = FeatureBuilder.background().scenario(
                "pathMatches('/"+ path + "')",
                "def response = '" + body + "'",
                "def responseHeaders = {'Content-Type': '" + contentType + "'}"
        );
    }

    private Response handle() {
        handler = new MockHandler(feature.build());
        request = httpRequestBuilder.build();
        Response response = handler.handle(request.toRequest());
        httpRequestBuilder = new HttpRequestBuilder(client).method("GET");
        return response;
    }

    @Test
    void testRequestLoggingPlain() {
        HttpRequest httpRequest = httpRequestBuilder.body("hello").contentType("text/plain").path("/plain").build();
        httpLogger.logRequest(config, httpRequest);
        String logs = logAppender.collect();
        assertTrue(logs.contains("hello"));
        assertTrue(logs.contains("Content-Type: text/plain"));
    }

    @Test
    void testRequestLoggingJson() {
        HttpRequest httpRequest = httpRequestBuilder.body("{a: 1}").contentType("application/json").path("/ttl").build();
        httpLogger.logRequest(config, httpRequest);
        String logs = logAppender.collect();
        assertTrue(logs.contains("{a: 1}"));
        assertTrue(logs.contains("Content-Type: application/json"));
    }

    @Test
    void testRequestLoggingXml() {
        HttpRequest httpRequest = httpRequestBuilder.body("<hello>world</hello>").contentType("application/xml").path("/ttl").build();
        httpLogger.logRequest(config, httpRequest);
        String logs = logAppender.collect();
        assertTrue(logs.contains("<hello>world</hello>"));
        assertTrue(logs.contains("Content-Type: application/xml"));
    }

    @Test
    void testRequestLoggingTurtle() {
        HttpRequest httpRequest = httpRequestBuilder.body(TURTLE_SAMPLE).contentType("text/turtle").path("/ttl").build();
        httpLogger.logRequest(config, httpRequest);
        String logs = logAppender.collect();
        assertTrue(logs.contains(TURTLE_SAMPLE));
        assertTrue(logs.contains("Content-Type: text/turtle"));
    }

    @Test
    void testRequestLoggingTurtleWithCharset() {
        HttpRequest httpRequest = httpRequestBuilder.body(TURTLE_SAMPLE).contentType("text/turtle; charset=UTF-8").path("/ttl").build();
        httpLogger.logRequest(config, httpRequest);
        String logs = logAppender.collect();
        assertTrue(logs.contains(TURTLE_SAMPLE));
        assertTrue(logs.contains("Content-Type: text/turtle; charset=UTF-8"));
    }

    @Test
    void testRequestLoggingJsonPretty() {
        config.configure("logPrettyRequest", new Variable(true));
        HttpRequest httpRequest = httpRequestBuilder.body("{a: 1}").contentType("application/json").path("/ttl").build();
        httpLogger.logRequest(config, httpRequest);
        String logs = logAppender.collect();
        assertTrue(logs.contains("{\n  \"a\": 1\n}"));
        assertTrue(logs.contains("Content-Type: application/json"));
    }

    @Test
    void testRequestLoggingXmlPretty() {
        config.configure("logPrettyRequest", new Variable(true));
        HttpRequest httpRequest = httpRequestBuilder.body("<hello>world</hello>").contentType("application/xml").path("/ttl").build();
        httpLogger.logRequest(config, httpRequest);
        String logs = logAppender.collect();
        assertTrue(logs.contains("<hello>world</hello>"));
        assertTrue(logs.contains("Content-Type: application/xml"));
    }

    @Test
    void testRequestLoggingTurtlePretty() {
        config.configure("logPrettyRequest", new Variable(true));
        HttpRequest httpRequest = httpRequestBuilder.body(TURTLE_SAMPLE).contentType("text/turtle").path("/ttl").build();
        httpLogger.logRequest(config, httpRequest);
        String logs = logAppender.collect();
        assertTrue(logs.contains(TURTLE_SAMPLE));
        assertTrue(logs.contains("Content-Type: text/turtle"));
    }

    @Test
    void testResponseLoggingPlain() {
        setup("plain", "hello", "text/plain");
        httpRequestBuilder.path("/plain");
        Response response = handle();
        match(response.getBodyAsString(), "hello");
        match(response.getContentType(), "text/plain");

        httpLogger.logResponse(config, request, response);
        String logs = logAppender.collect();
        assertTrue(logs.contains("hello"));
        assertTrue(logs.contains("Content-Type: text/plain"));
    }

    @Test
    void testResponseLoggingJson() {
        setup("json", "{a: 1}", "application/json");
        httpRequestBuilder.path("/json");
        Response response = handle();
        match(response.getBodyAsString(), "{a: 1}");
        match(response.getContentType(), "application/json");

        httpLogger.logResponse(config, request, response);
        String logs = logAppender.collect();
        assertTrue(logs.contains("{a: 1}"));
        assertTrue(logs.contains("Content-Type: application/json"));
    }

    @Test
    void testResponseLoggingXml() {
        setup("xml", "<hello>world</hello>", "application/xml");
        httpRequestBuilder.path("/xml");
        Response response = handle();
        match(response.getBodyAsString(), "<hello>world</hello>");
        match(response.getContentType(), "application/xml");

        httpLogger.logResponse(config, request, response);
        String logs = logAppender.collect();
        assertTrue(logs.contains("<hello>world</hello>"));
        assertTrue(logs.contains("Content-Type: application/xml"));
    }

    @Test
    void testResponseLoggingTurtle() {
        setup("ttl", TURTLE_SAMPLE, "text/turtle");
        httpRequestBuilder.path("/ttl");
        Response response = handle();
        assertEquals(response.getBodyAsString(), TURTLE_SAMPLE);
        assertTrue(response.getContentType().contains("text/turtle"));

        httpLogger.logResponse(config, request, response);
        String logs = logAppender.collect();
        assertTrue(logs.contains(TURTLE_SAMPLE));
        assertTrue(logs.contains("Content-Type: text/turtle"));
    }

    @Test
    void testResponseLoggingTurtleWithCharset() {
        setup("ttl", TURTLE_SAMPLE, "text/turtle; charset=UTF-8");
        httpRequestBuilder.path("/ttl");
        Response response = handle();
        assertEquals(response.getBodyAsString(), TURTLE_SAMPLE);
        assertEquals(response.getContentType(), "text/turtle; charset=UTF-8");

        httpLogger.logResponse(config, request, response);
        String logs = logAppender.collect();
        assertTrue(logs.contains(TURTLE_SAMPLE));
        assertTrue(logs.contains("Content-Type: text/turtle; charset=UTF-8"));
    }

    @Test
    void testResponseLoggingJsonPretty() {
        config.configure("logPrettyResponse", new Variable(true));
        setup("json", "{a: 1}", "application/json");
        httpRequestBuilder.path("/json");
        Response response = handle();
        match(response.getBodyAsString(), "{a: 1}");
        match(response.getContentType(), "application/json");

        httpLogger.logResponse(config, request, response);
        String logs = logAppender.collect();
        assertTrue(logs.contains("{\n  \"a\": 1\n}"));
        assertTrue(logs.contains("Content-Type: application/json"));
    }

    @Test
    void testResponseLoggingXmlPretty() {
        config.configure("logPrettyResponse", new Variable(true));
        setup("xml", "<hello>world</hello>", "application/xml");
        httpRequestBuilder.path("/xml");
        Response response = handle();
        match(response.getBodyAsString(), "<hello>world</hello>");
        match(response.getContentType(), "application/xml");

        httpLogger.logResponse(config, request, response);
        String logs = logAppender.collect();
        assertTrue(logs.contains("<hello>world</hello>"));
        assertTrue(logs.contains("Content-Type: application/xml"));
    }

    @Test
    void testResponseLoggingTurtlePretty() {
        config.configure("logPrettyResponse", new Variable(true));
        setup("ttl", TURTLE_SAMPLE, "text/turtle");
        httpRequestBuilder.path("/ttl");
        Response response = handle();
        assertEquals(response.getBodyAsString(), TURTLE_SAMPLE);
        assertTrue(response.getContentType().contains("text/turtle"));

        httpLogger.logResponse(config, request, response);
        String logs = logAppender.collect();
        assertTrue(logs.contains(TURTLE_SAMPLE));
        assertTrue(logs.contains("Content-Type: text/turtle"));
    }
}
