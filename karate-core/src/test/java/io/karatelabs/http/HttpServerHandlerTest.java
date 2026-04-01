package io.karatelabs.http;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpServerHandlerTest {

    @Test
    void testToRequestSetsUrlBaseFromHostHeader() {
        FullHttpRequest nettyRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/signin");
        nettyRequest.headers().set("Host", "localhost:8080");

        HttpRequest request = HttpServerHandler.toRequest(nettyRequest);

        assertEquals("http://localhost:8080", request.jsGet("urlBase"));
        assertEquals("/signin", request.getPath());
    }

    @Test
    void testToRequestSetsHttpsFromForwardedProto() {
        FullHttpRequest nettyRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/callback");
        nettyRequest.headers().set("Host", "myapp.example.com");
        nettyRequest.headers().set("X-Forwarded-Proto", "https");

        HttpRequest request = HttpServerHandler.toRequest(nettyRequest);

        assertEquals("https://myapp.example.com", request.jsGet("urlBase"));
    }

    @Test
    void testToRequestWithoutHostHeader() {
        FullHttpRequest nettyRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/test");

        HttpRequest request = HttpServerHandler.toRequest(nettyRequest);

        assertNull(request.jsGet("urlBase"));
    }

    @Test
    void testIsSseRequestWithAcceptHeader() {
        FullHttpRequest req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/events");
        req.headers().set("Accept", "text/event-stream");
        assertTrue(HttpServerHandler.isSseRequest(req));
    }

    @Test
    void testIsSseRequestWithoutAcceptHeader() {
        FullHttpRequest req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/events");
        assertFalse(HttpServerHandler.isSseRequest(req));
    }

    @Test
    void testIsSseRequestWithJsonAccept() {
        FullHttpRequest req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/data");
        req.headers().set("Accept", "application/json");
        assertFalse(HttpServerHandler.isSseRequest(req));
    }

    @Test
    void testToRequestWithBody() {
        byte[] body = "{\"name\":\"test\"}".getBytes();
        FullHttpRequest nettyRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/test",
                Unpooled.wrappedBuffer(body));
        nettyRequest.headers().set("Host", "localhost:8080");
        nettyRequest.headers().set("Content-Type", "application/json");

        HttpRequest request = HttpServerHandler.toRequest(nettyRequest);

        assertEquals("http://localhost:8080", request.jsGet("urlBase"));
        assertEquals("POST", request.getMethod());
        assertArrayEquals(body, request.getBody());
    }

}
