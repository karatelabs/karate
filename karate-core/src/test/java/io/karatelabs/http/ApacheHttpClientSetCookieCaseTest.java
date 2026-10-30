package io.karatelabs.http;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApacheHttpClientSetCookieCaseTest {

    HttpServer server;

    @BeforeEach
    void start() {
        server = HttpServer.start(0, request -> {
            HttpResponse response = HttpResponse.json("{}");
            switch (request.getPath()) {
                case "/lower" -> response.setHeader("set-cookie", "sid=1; Max-Age=3600; Path=/");
                case "/mixed" -> response.setHeader("SeT-CoOkIe", "sid=1; Max-Age=3600; Path=/");
                case "/hop" -> {
                    response.setStatus(302);
                    response.setHeader("Location", "/plain");
                    response.setHeader("set-cookie", "hop=1; Max-Age=3600; Path=/");
                }
                default -> {
                }
            }
            return response;
        });
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    Cookie onlyCookie(String path) throws Exception {
        try (ApacheHttpClient client = new ApacheHttpClient()) {
            HttpRequest request = new HttpRequest();
            request.setUrl("http://localhost:" + server.getPort() + path);
            request.setMethod("GET");
            HttpResponse response = client.invoke(request);
            assertEquals(200, response.getStatus());
            List<String> values = new ArrayList<>();
            response.getHeaders().forEach((k, v) -> {
                if (k.equalsIgnoreCase("Set-Cookie")) {
                    values.addAll(v);
                }
            });
            assertEquals(1, values.size(), values.toString());
            return ClientCookieDecoder.LAX.decode(values.getFirst());
        }
    }

    @Test
    void aLowercaseSetCookieIsNotDuplicated() throws Exception {
        Cookie cookie = onlyCookie("/lower");
        assertEquals("sid", cookie.name());
        assertEquals(3600, cookie.maxAge());
    }

    @Test
    void aMixedCaseSetCookieIsNotDuplicated() throws Exception {
        Cookie cookie = onlyCookie("/mixed");
        assertEquals("sid", cookie.name());
        assertEquals(3600, cookie.maxAge());
    }

    @Test
    void aRedirectHopsCookieMergesWithMaxAgeInSeconds() throws Exception {
        Cookie cookie = onlyCookie("/hop");
        assertEquals("hop", cookie.name());
        assertTrue(cookie.maxAge() > 3590 && cookie.maxAge() <= 3600, "max-age " + cookie.maxAge());
    }

}
