/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The handler runs off the event loop: a handler that calls back into its own server (the console's Send
 * re-sending to the console) would otherwise wait on the loop thread it is holding. One worker thread makes the
 * collision certain.
 */
class HttpServerReentrantHandlerTest {

    static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    static String get(int port, String path) throws Exception {
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return CLIENT.send(req, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
    }

    /** One response off the wire, as "status body": the status line and headers, then exactly Content-Length
     *  body characters. */
    static String response(BufferedReader in) throws Exception {
        String status = in.readLine().split(" ")[1];
        int length = -1;
        for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
            if (line.toLowerCase().startsWith("content-length:")) {
                length = Integer.parseInt(line.substring(15).trim());
            }
        }
        assertTrue(length >= 0, "a Content-Length header");
        char[] body = new char[length];
        for (int read = 0; read < length; ) {
            int n = in.read(body, read, length - read);
            assertTrue(n > 0, "the body ended early");
            read += n;
        }
        return status + " " + new String(body);
    }

    static List<String> pipelined(HttpServer server, String first, String second) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", server.getPort()), 2000);
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + first + " HTTP/1.1\r\nHost: localhost\r\n\r\n"
                    + "GET " + second + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            return List.of(response(in), response(in));
        }
    }

    @Test
    void aHandlerThatCallsBackIntoItsOwnServerIsAnswered() throws Exception {
        int[] port = new int[1];
        HttpServer server = HttpServer.builder().workerThreads(1).handler(req -> {
            if (req.getPath().endsWith("inner")) {
                return HttpResponse.text("inner");
            }
            try {
                return HttpResponse.text("outer+" + get(port[0], "/inner"));
            } catch (Exception e) {
                return HttpResponse.text(500, e.toString());
            }
        }).start();
        port[0] = server.getPort();
        try {
            assertEquals("outer+inner", get(port[0], "/outer"));
            assertEquals("outer+inner", get(port[0], "/outer"), "the loop serves the next request too");
        } finally {
            server.stopAndWait();
        }
    }

    /** One connection's answers come back in request order, a delayed one included. */
    @Test
    void aKeptConnectionAnswersInOrderAcrossADelay() throws Exception {
        HttpServer server = HttpServer.builder().workerThreads(1).handler(req -> {
            HttpResponse response = HttpResponse.text(req.getPath());
            if (req.getPath().endsWith("slow")) {
                response.setDelay(300);
            }
            return response;
        }).start();
        try {
            assertEquals(List.of("200 /slow", "200 /fast"), pipelined(server, "/slow", "/fast"));
        } finally {
            server.stopAndWait();
        }
    }

    /** A request the server cannot parse is answered in its turn, after the one before it. */
    @Test
    void aMalformedRequestBehindABlockingOneIsAnsweredInOrder() throws Exception {
        HttpServer server = HttpServer.builder().workerThreads(1).handler(req -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return HttpResponse.text(req.getPath());
        }).start();
        try {
            List<String> answers = pipelined(server, "/slow", "/?state=%zz");
            assertEquals("200 /slow", answers.get(0));
            assertTrue(answers.get(1).startsWith("400 "), answers.get(1));
        } finally {
            server.stopAndWait();
        }
    }

    /** A handler still blocked when the grace period ends is interrupted, and the stop waits for it to finish. */
    @Test
    void stopInterruptsAHandlerStillBlockedAfterTheGracePeriod() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        HttpServer server = HttpServer.builder().stopTimeout(Duration.ofMillis(500)).handler(req -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
            return HttpResponse.text("late");
        }).start();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/block"))
                .timeout(Duration.ofSeconds(10)).GET().build();
        CLIENT.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertTrue(entered.await(5, TimeUnit.SECONDS), "the handler was entered");
        long started = System.nanoTime();
        server.stopAndWait();
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5), "the stop waited out neither timeout");
        assertTrue(interrupted.get(), "the blocked handler was interrupted before stop returned");
    }
}
