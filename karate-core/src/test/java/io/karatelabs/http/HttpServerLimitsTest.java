package io.karatelabs.http;

import io.karatelabs.core.MockServer;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpServerLimitsTest {

    private static String rawRequest(int port, String request) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = in.readLine();
            return statusLine == null ? "<connection closed with no response>" : statusLine;
        }
    }

    /** Everything the server sends until it closes the connection; fails if it never does. */
    private static String rawExchange(int port, String requests) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            socket.setSoTimeout(3000);
            OutputStream out = socket.getOutputStream();
            out.write(requests.getBytes(StandardCharsets.UTF_8));
            out.flush();
            try {
                return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (SocketTimeoutException e) {
                throw new AssertionError("connection left open", e);
            }
        }
    }

    private static final String NEXT_REQUEST = "GET /next HTTP/1.1\r\nHost: localhost\r\n\r\n";

    private static void assertRejectedAndClosed(String exchange, String statusLine) {
        assertTrue(exchange.startsWith(statusLine + "\r\n"), exchange);
        assertEquals(exchange.indexOf("HTTP/1.1 "), exchange.lastIndexOf("HTTP/1.1 "), exchange);
    }

    private static String getWithHeader(int port, int valueLength) throws Exception {
        return rawRequest(port, "GET / HTTP/1.1\r\nHost: localhost\r\nX-Big: " + "a".repeat(valueLength)
                + "\r\nConnection: close\r\n\r\n");
    }

    private static String postWithBody(int port, int length) throws Exception {
        return rawRequest(port, "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nContent-Length: "
                + length + "\r\nConnection: close\r\n\r\n" + "b".repeat(length));
    }

    @Test
    void aHeaderOverTheDefaultLimitIsRejectedAndAcceptedOnceTheLimitIsRaised() throws Exception {
        HttpServer server = HttpServer.start(0, req -> HttpResponse.text("ok"));
        try {
            assertEquals("HTTP/1.1 431 Request Header Fields Too Large", getWithHeader(server.getPort(), 9000));
            assertEquals("HTTP/1.1 200 OK", getWithHeader(server.getPort(), 7000));
        } finally {
            server.stopAndWait();
        }
        server = HttpServer.builder().maxHeaderSize(16 * 1024).handler(req -> HttpResponse.text("ok")).start();
        try {
            assertEquals("HTTP/1.1 200 OK", getWithHeader(server.getPort(), 9000));
        } finally {
            server.stopAndWait();
        }
    }

    @Test
    void aRequestLineOverTheConfiguredLimitIsRejected() throws Exception {
        HttpServer server = HttpServer.builder().maxInitialLineLength(100).handler(req -> HttpResponse.text("ok")).start();
        try {
            assertTrue(rawRequest(server.getPort(), "GET /" + "p".repeat(200) + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .startsWith("HTTP/1.1 414 "));
            assertEquals("HTTP/1.1 200 OK", rawRequest(server.getPort(), "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"));
        } finally {
            server.stopAndWait();
        }
    }

    @Test
    void aBodyOverTheConfiguredLimitIsRejected() throws Exception {
        HttpServer server = HttpServer.builder().maxContentLength(1000).handler(req -> HttpResponse.text("ok")).start();
        try {
            assertEquals("HTTP/1.1 200 OK", postWithBody(server.getPort(), 1000));
            assertEquals("HTTP/1.1 413 Request Entity Too Large", postWithBody(server.getPort(), 1001));
        } finally {
            server.stopAndWait();
        }
    }

    @Test
    void anOverLimitHeaderOnACorsPreflightIsRejected() throws Exception {
        AtomicInteger served = new AtomicInteger();
        HttpServer server = HttpServer.builder().maxHeaderSize(128)
                .handler(req -> { served.incrementAndGet(); return HttpResponse.text("ok"); }).start();
        try {
            String exchange = rawExchange(server.getPort(), "OPTIONS / HTTP/1.1\r\nHost: localhost\r\n"
                    + "Origin: http://example.com\r\nAccess-Control-Request-Method: GET\r\nX-Small: a\r\n"
                    + "X-Big: " + "a".repeat(200) + "\r\n\r\n" + NEXT_REQUEST);
            assertRejectedAndClosed(exchange, "HTTP/1.1 431 Request Header Fields Too Large");
            assertEquals(0, served.get());
        } finally {
            server.stopAndWait();
        }
    }

    @Test
    void anOverLimitHeaderIsRejectedBeforeAnOverLimitContentLength() throws Exception {
        AtomicInteger served = new AtomicInteger();
        HttpServer server = HttpServer.builder().maxHeaderSize(128).maxContentLength(10)
                .handler(req -> { served.incrementAndGet(); return HttpResponse.text("ok"); }).start();
        try {
            String exchange = rawExchange(server.getPort(), "POST / HTTP/1.1\r\nHost: localhost\r\n"
                    + "Content-Length: 20\r\nX-Small: a\r\nX-Big: " + "a".repeat(200) + "\r\n\r\n"
                    + "b".repeat(20) + NEXT_REQUEST);
            assertRejectedAndClosed(exchange, "HTTP/1.1 431 Request Header Fields Too Large");
            assertEquals(0, served.get());
        } finally {
            server.stopAndWait();
        }
    }

    @Test
    void anInvalidHeaderIsRejectedWithoutEchoingIt() throws Exception {
        HttpServer server = HttpServer.start(0, req -> HttpResponse.text("ok"));
        try {
            String exchange = rawExchange(server.getPort(), "GET / HTTP/1.1\r\nHost: localhost\r\n"
                    + "X-Big<script>: a\r\n\r\n" + NEXT_REQUEST);
            assertRejectedAndClosed(exchange, "HTTP/1.1 400 Bad Request");
            assertFalse(exchange.contains("<script>"), exchange);
        } finally {
            server.stopAndWait();
        }
    }

    @Test
    void aLimitThatIsNotPositiveIsRejected() {
        HttpServer.Builder builder = HttpServer.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.maxHeaderSize(0));
        assertThrows(IllegalArgumentException.class, () -> builder.maxInitialLineLength(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.maxContentLength(0));
        assertThrows(IllegalArgumentException.class, () -> MockServer.featureString(MOCK).maxHeaderSize(-8192));
    }

    private static final String MOCK = """
            Feature: limits
            Scenario:
              * def response = 'ok'
            """;

    @Test
    void aMockServerTakesTheSameLimits() throws Exception {
        MockServer mock = MockServer.featureString(MOCK).maxHeaderSize(16 * 1024).maxContentLength(1000).start();
        try {
            assertEquals("HTTP/1.1 200 OK", getWithHeader(mock.getPort(), 9000));
            assertEquals("HTTP/1.1 413 Request Entity Too Large", postWithBody(mock.getPort(), 1001));
        } finally {
            mock.stopAndWait();
        }
    }

}
