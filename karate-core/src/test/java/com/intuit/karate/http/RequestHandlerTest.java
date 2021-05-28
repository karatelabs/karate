package com.intuit.karate.http;

import com.intuit.karate.Match;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class RequestHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(RequestHandlerTest.class);

    RequestHandler handler;
    HttpRequestBuilder request;
    Response response;
    List<String> cookies;
    String body;

    @BeforeEach
    void beforeEach() {
        ServerConfig config = new ServerConfig("classpath:demo");
        handler = new RequestHandler(config);
        request = new HttpRequestBuilder(null).method("GET");
    }

    private Response handle() {
        response = handler.handle(request.build().toRequest());
        body = response.getBodyAsString();
        cookies = response.getHeaderValues("Set-Cookie");
        request = new HttpRequestBuilder(null).method("GET");
        if (cookies != null) {
            request.header("Cookie", cookies);
        }
        return response;
    }

    private void matchHeaderEquals(String name, String expected) {
        Match.Result mr = Match.evaluate(response.getHeader(name)).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }

    private void matchHeaderContains(String name, String expected) {
        Match.Result mr = Match.evaluate(response.getHeader(name)).contains(expected);
        assertTrue(mr.pass, mr.message);
    }

    @Test
    void testIndexAndAjaxPost() {
        request.path("index");
        handle();
        matchHeaderContains("Set-Cookie", "karate.sid");
        matchHeaderEquals("Content-Type", "text/html");
        assertTrue(body.startsWith("<!doctype html>"));
        assertTrue(body.contains("<span>John Smith</span>"));
        assertTrue(body.contains("<td>Apple</td>"));
        assertTrue(body.contains("<td>Orange</td>"));
        assertTrue(body.contains("<span>Billie</span>"));
        request.path("person")
                .contentType("application/x-www-form-urlencoded")
                .header("HX-Request", "true")
                .body("firstName=John&lastName=Smith&email=john%40smith.com")
                .method("POST");
        handle();
        assertTrue(body.contains("<span>John</span>"));
    }

}
