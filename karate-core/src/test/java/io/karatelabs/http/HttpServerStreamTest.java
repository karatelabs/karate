package io.karatelabs.http;

import io.karatelabs.js.Engine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A response body given as an {@link InputStream} reaches the wire as it is read: Content-Length when the
 * length is known, chunked otherwise, and the stream is closed however the response ends.
 */
class HttpServerStreamTest {

    // over the chunk size and any socket buffer, so the body cannot arrive in one write
    static final int BIG = 3 * 1024 * 1024 + 17;

    HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stopAndWait();
        }
    }

    static byte[] random(int size) {
        byte[] bytes = new byte[size];
        new Random(42).nextBytes(bytes);
        return bytes;
    }

    /** Records its close and how much was read; optionally fails after {@code failAfter} bytes. */
    static class Tracked extends FilterInputStream {

        final CountDownLatch closed = new CountDownLatch(1);
        final AtomicLong read = new AtomicLong();
        final long failAfter;

        Tracked(InputStream in) {
            this(in, -1);
        }

        Tracked(InputStream in, long failAfter) {
            super(in);
            this.failAfter = failAfter;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (failAfter >= 0 && read.get() >= failAfter) {
                throw new IOException("disk went away");
            }
            int n = super.read(b, off, len);
            if (n > 0) {
                read.addAndGet(n);
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            closed.countDown();
            super.close();
        }

        boolean awaitClosed() throws InterruptedException {
            return closed.await(5, TimeUnit.SECONDS);
        }
    }

    /** Endless bytes: only a disconnect ends a response that streams this. */
    static class Endless extends InputStream {
        @Override
        public int read() {
            return 'x';
        }

        @Override
        public int read(byte[] b, int off, int len) {
            Arrays.fill(b, off, off + len, (byte) 'x');
            return len;
        }
    }

    record Wire(String statusLine, Map<String, String> headers, byte[] body, boolean complete) {
        String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }

    int start(Function<HttpRequest, HttpResponse> handler) {
        server = HttpServer.start(0, handler);
        return server.getPort();
    }

    static Socket connect(int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
        socket.setSoTimeout(10000);
        return socket;
    }

    static void send(Socket socket, String method, String path) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write((method + " " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    static String line(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
        }
        if (b < 0 && buf.size() == 0) {
            return null;
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    /** One response off the connection, decoding its framing; {@code complete} is false when it is cut short. */
    static Wire read(InputStream in, boolean head) throws IOException {
        String status = line(in);
        assertNotNull(status, "no response");
        Map<String, String> headers = new LinkedHashMap<>();
        String h;
        while ((h = line(in)) != null && !h.isEmpty()) {
            int colon = h.indexOf(':');
            headers.put(h.substring(0, colon).trim().toLowerCase(), h.substring(colon + 1).trim());
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (head) {
            return new Wire(status, headers, body.toByteArray(), true);
        }
        String length = headers.get("content-length");
        if (length != null) {
            long want = Long.parseLong(length);
            byte[] buf = new byte[8192];
            while (body.size() < want) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, want - body.size()));
                if (n < 0) {
                    return new Wire(status, headers, body.toByteArray(), false);
                }
                body.write(buf, 0, n);
            }
            return new Wire(status, headers, body.toByteArray(), true);
        }
        assertEquals("chunked", headers.get("transfer-encoding"), "neither a length nor chunked: " + headers);
        while (true) {
            String size = line(in);
            if (size == null) {
                return new Wire(status, headers, body.toByteArray(), false);
            }
            int n = Integer.parseInt(size.trim(), 16);
            if (n == 0) {
                String trailerEnd = line(in);
                return new Wire(status, headers, body.toByteArray(), trailerEnd != null);
            }
            byte[] chunk = in.readNBytes(n);
            body.write(chunk);
            if (chunk.length < n || line(in) == null) {
                return new Wire(status, headers, body.toByteArray(), false);
            }
        }
    }

    static Wire get(int port, String method, String path) throws IOException {
        try (Socket socket = connect(port)) {
            send(socket, method, path);
            return read(new BufferedInputStream(socket.getInputStream()), "HEAD".equals(method));
        }
    }

    @Test
    void aStreamWithAKnownLengthArrivesByteIdenticalUnderContentLength() throws Exception {
        byte[] bytes = random(BIG);
        Tracked stream = new Tracked(new ByteArrayInputStream(bytes));
        int port = start(req -> {
            HttpResponse r = new HttpResponse();
            r.setBodyStream(stream, bytes.length);
            r.setHeader("Content-Type", "application/octet-stream");
            return r;
        });
        Wire wire = get(port, "GET", "/blob");
        assertEquals("HTTP/1.1 200 OK", wire.statusLine());
        assertEquals(String.valueOf(BIG), wire.header("Content-Length"));
        assertNull(wire.header("Transfer-Encoding"));
        assertTrue(wire.complete());
        assertArrayEquals(bytes, wire.body());
        assertTrue(stream.awaitClosed(), "the stream is closed once the body is written");
    }

    @Test
    void aStreamWithAnUnknownLengthArrivesByteIdenticalChunked() throws Exception {
        byte[] bytes = random(BIG);
        Tracked stream = new Tracked(new ByteArrayInputStream(bytes));
        int port = start(req -> {
            HttpResponse r = new HttpResponse();
            r.setBodyStream(stream, -1);
            return r;
        });
        Wire wire = get(port, "GET", "/blob");
        assertEquals("HTTP/1.1 200 OK", wire.statusLine());
        assertNull(wire.header("Content-Length"));
        assertEquals("chunked", wire.header("Transfer-Encoding"));
        assertTrue(wire.complete(), "the terminal chunk is written");
        assertArrayEquals(bytes, wire.body());
        assertTrue(stream.awaitClosed());
    }

    @Test
    void aReadErrorMidStreamCutsTheResponseShortClosesTheStreamAndTheServerKeepsAnswering() throws Exception {
        Tracked chunked = new Tracked(new ByteArrayInputStream(random(BIG)), 100_000);
        Tracked sized = new Tracked(new ByteArrayInputStream(random(BIG)), 100_000);
        int port = start(req -> {
            HttpResponse r = new HttpResponse();
            switch (req.getPath()) {
                case "/chunked" -> r.setBodyStream(chunked, -1);
                case "/sized" -> r.setBodyStream(sized, BIG);
                default -> r.setBody("ok", io.karatelabs.common.ResourceType.TEXT);
            }
            return r;
        });
        Wire c = get(port, "GET", "/chunked");
        assertEquals("HTTP/1.1 200 OK", c.statusLine());
        assertFalse(c.complete(), "no terminal chunk: the client can tell the body is cut short");
        assertTrue(chunked.awaitClosed());
        Wire s = get(port, "GET", "/sized");
        assertFalse(s.complete(), "fewer bytes than the Content-Length promised");
        assertTrue(s.body().length < BIG);
        assertTrue(sized.awaitClosed());
        Wire ok = get(port, "GET", "/ok");
        assertEquals("ok", new String(ok.body(), StandardCharsets.UTF_8));
    }

    @Test
    void aStreamShorterThanItsDeclaredLengthIsCutShortNotPadded() throws Exception {
        Tracked stream = new Tracked(new ByteArrayInputStream(random(1000)));
        int port = start(req -> {
            HttpResponse r = new HttpResponse();
            r.setBodyStream(stream, 5000);
            return r;
        });
        Wire wire = get(port, "GET", "/short");
        assertEquals("5000", wire.header("Content-Length"));
        assertFalse(wire.complete());
        assertTrue(stream.awaitClosed());
    }

    @Test
    void aStreamLongerThanItsDeclaredLengthStopsAtTheLength() throws Exception {
        byte[] bytes = random(BIG);
        Tracked stream = new Tracked(new ByteArrayInputStream(bytes));
        int port = start(req -> {
            HttpResponse r = new HttpResponse();
            r.setBodyStream(stream, 1000);
            return r;
        });
        try (Socket socket = connect(port)) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            send(socket, "GET", "/long");
            Wire first = read(in, false);
            assertArrayEquals(Arrays.copyOf(bytes, 1000), first.body());
            // the connection still frames correctly: the next response is not swallowed by surplus bytes
            send(socket, "GET", "/long");
            assertEquals("HTTP/1.1 200 OK", line(in));
        }
        assertTrue(stream.awaitClosed());
    }

    @Test
    void aClientThatDisconnectsEarlyClosesTheStream() throws Exception {
        Tracked stream = new Tracked(new Endless());
        int port = start(req -> {
            HttpResponse r = new HttpResponse();
            r.setBodyStream(stream, -1);
            return r;
        });
        try (Socket socket = connect(port)) {
            send(socket, "GET", "/endless");
            InputStream in = socket.getInputStream();
            assertEquals("HTTP/1.1 200 OK", line(in));
            in.readNBytes(64 * 1024);
        }
        assertTrue(stream.awaitClosed(), "a disconnect ends an endless stream");
        Wire after = get(port, "HEAD", "/endless");
        assertEquals("HTTP/1.1 200 OK", after.statusLine());
    }

    @Test
    void headSendsTheHeadersWithoutReadingTheStream() throws Exception {
        Tracked stream = new Tracked(new ByteArrayInputStream(random(BIG)));
        int port = start(req -> {
            HttpResponse r = new HttpResponse();
            r.setBodyStream(req.getPath().equals("/head") ? stream : new ByteArrayInputStream(new byte[0]), BIG);
            return r;
        });
        try (Socket socket = connect(port)) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            send(socket, "HEAD", "/head");
            Wire head = read(in, true);
            assertEquals("HTTP/1.1 200 OK", head.statusLine());
            assertEquals(String.valueOf(BIG), head.header("Content-Length"));
            // no body bytes on the wire: the next response starts right after the headers
            send(socket, "HEAD", "/other");
            assertEquals("HTTP/1.1 200 OK", line(in));
        }
        assertTrue(stream.awaitClosed());
        assertEquals(0, stream.read.get());
    }

    @Test
    void anEmptyStreamIsAnEmptyBodyEitherWay() throws Exception {
        Tracked sized = new Tracked(new ByteArrayInputStream(new byte[0]));
        Tracked chunked = new Tracked(new ByteArrayInputStream(new byte[0]));
        int port = start(req -> {
            HttpResponse r = new HttpResponse();
            if (req.getPath().equals("/sized")) {
                r.setBodyStream(sized, 0);
            } else {
                r.setBodyStream(chunked, -1);
            }
            return r;
        });
        try (Socket socket = connect(port)) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            send(socket, "GET", "/sized");
            Wire s = read(in, false);
            assertEquals("0", s.header("Content-Length"));
            assertEquals(0, s.body().length);
            send(socket, "GET", "/chunked");
            Wire c = read(in, false);
            assertEquals("chunked", c.header("Transfer-Encoding"));
            assertTrue(c.complete());
            assertEquals(0, c.body().length);
        }
        assertTrue(sized.awaitClosed());
        assertTrue(chunked.awaitClosed());
    }

    @Test
    void aByteBodyIsUnchanged() throws Exception {
        byte[] bytes = random(100_000);
        int port = start(req -> HttpResponse.binary(bytes));
        Wire wire = get(port, "GET", "/bytes");
        assertEquals("100000", wire.header("Content-Length"));
        assertNull(wire.header("Transfer-Encoding"));
        assertArrayEquals(bytes, wire.body());
    }

    @Test
    void aBodyThatReplacesAStreamClosesIt() throws Exception {
        Tracked stream = new Tracked(new ByteArrayInputStream(random(10)));
        HttpResponse r = new HttpResponse();
        r.setBodyStream(stream, 10);
        r.setBody("Internal Server Error", io.karatelabs.common.ResourceType.TEXT);
        assertNull(r.getBodyStream());
        assertTrue(stream.awaitClosed());
    }

    @Test
    void aHandlerThatFailsAfterSettingAStreamStillClosesIt() throws Exception {
        Tracked stream = new Tracked(new ByteArrayInputStream(random(10)));
        int port = start(req -> {
            HttpResponse r = new HttpResponse();
            r.setBodyStream(stream, 10);
            r.setResetConnection(true);
            return r;
        });
        try (Socket socket = connect(port)) {
            send(socket, "GET", "/reset");
            try {
                socket.getInputStream().read();
            } catch (IOException expected) {
                // the reset
            }
        }
        assertTrue(stream.awaitClosed());
    }

    @Test
    void aJsHandlerHandsOverAJavaStreamAsTheBody() throws Exception {
        byte[] bytes = random(BIG);
        Tracked stream = new Tracked(new ByteArrayInputStream(bytes));
        int port = start(req -> {
            HttpResponse response = new HttpResponse();
            Engine engine = new Engine();
            engine.putRootBinding("response", response);
            // the shape a Java JS-API hands back: a map holding the stream and its size
            engine.put("file", new LinkedHashMap<>(Map.of("stream", stream, "size", (long) bytes.length)));
            engine.eval("response.headers['Content-Type'] = 'application/octet-stream';"
                    + "response.headers['Content-Length'] = file.size;"
                    + "response.body = file.stream;");
            return response;
        });
        Wire wire = get(port, "GET", "/js");
        assertEquals(String.valueOf(BIG), wire.header("Content-Length"));
        assertEquals("application/octet-stream", wire.header("Content-Type"));
        assertArrayEquals(bytes, wire.body());
        assertTrue(stream.awaitClosed());
    }

}
