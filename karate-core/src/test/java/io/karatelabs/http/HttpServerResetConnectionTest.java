package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The transport-fault seam ({@link HttpResponse#setResetConnection(boolean)}): a handler that marks its
 * response {@code resetConnection} makes the server drop the connection with a TCP RST instead of
 * answering — what a resilience/chaos mock needs to simulate a dependency dying mid-request, and the one
 * transport fault the {@code HttpRequest -> HttpResponse} handler seam could not otherwise express
 * ({@code delay} covers slow/timeout).
 */
class HttpServerResetConnectionTest {

    @Test
    void aResetConnectionResponseDropsTheSocketAndTheServerStaysHealthy() throws Exception {
        HttpServer server = HttpServer.start(0, req -> {
            HttpResponse response = HttpResponse.text("ok");
            if (req.getPath().contains("reset")) {
                response.setResetConnection(true);
            }
            return response;
        });
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", server.getPort()), 2000);
            socket.setSoTimeout(5000);      // a hung server must FAIL this test, not stall it
            OutputStream out = socket.getOutputStream();
            out.write("GET /reset HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            try {
                int read = socket.getInputStream().read();
                // an RST usually surfaces as an IOException; a plain EOF (-1, the FIN shape) still proves
                // the server wrote nothing — either way, no HTTP response reached the client
                assertEquals(-1, read, "no response byte may be written before the connection drops");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().toLowerCase().contains("reset"),
                        "the drop is a reset, not a timeout: " + expected);
            }
        }
        try (Socket socket = new Socket()) {
            // one reset connection may not poison the next request
            socket.connect(new InetSocketAddress("127.0.0.1", server.getPort()), 2000);
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write("GET /ok HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = in.readLine();
            if (statusLine == null) {
                fail("the server stopped answering after a reset");
            }
            assertEquals("HTTP/1.1 200 OK", statusLine);
        } finally {
            server.stopAndWait();
        }
    }
}
