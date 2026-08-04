package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>No request leaves this server unanswered.</b>
 *
 * <p>A malformed request line fails while the request is still being <i>built</i> — before any handler
 * exists to answer it. A bad percent-escape in the query string (<code>?state=%zz</code>) makes Netty's
 * {@code QueryStringDecoder} throw inside {@code toRequest}, which used to sit outside every {@code
 * try/catch} in {@code channelRead0}: the exception reached the pipeline tail, Netty logged it, and
 * <b>nothing was ever written back</b>. The client then waited out its own timeout against a server that
 * was serving every other connection normally — the failure mode that is worse than any wrong status,
 * because it looks like the client's fault.</p>
 *
 * <p>Driven over a raw socket on purpose: every HTTP client worth using rejects such a URI locally, so a
 * client-based test cannot reach the defect at all.</p>
 */
class HttpServerMalformedRequestTest {

    /** Send a raw request line and return the status line, failing rather than hanging on silence. */
    private static String rawGet(int port, String requestTarget) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            socket.setSoTimeout(5000);      // the whole point: a hung server must FAIL this test, not stall it
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + requestTarget + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = in.readLine();
            return statusLine == null ? "<connection closed with no response>" : statusLine;
        }
    }

    @Test
    void aMalformedQueryEscapeIsAnsweredWithFourHundredRatherThanSilence() throws Exception {
        HttpServer server = HttpServer.start(0, req -> HttpResponse.text("ok"));
        try {
            assertTrue(rawGet(server.getPort(), "/?state=%zz").startsWith("HTTP/1.1 400"),
                    "a request URI we cannot parse is a 400 — never an unanswered connection");
            // an unterminated escape at the very end is the other shape of the same fault
            assertTrue(rawGet(server.getPort(), "/?state=%2").startsWith("HTTP/1.1 400"));
            // …and the server is still healthy afterwards: one bad request may not poison the next
            assertEquals("HTTP/1.1 200 OK", rawGet(server.getPort(), "/?state=CA"));
        } finally {
            server.stopAndWait();
        }
    }
}
