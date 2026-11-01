package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ServerSocketChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The bind-host hardening: {@code HttpServer.start(host, ...)} listens on the requested interface only.
 * A {@code null}/blank/{@code "0.0.0.0"} host keeps the historical wildcard (all interfaces); a specific
 * host (e.g. {@code "localhost"}) binds loopback only — so a host-dev {@code karate serve} is not exposed
 * to the network, while the container opts back into {@code 0.0.0.0} for Docker {@code -p} forwarding.
 */
class HttpServerBindTest {

    private static HttpResponse<String> get(int port) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void wildcardBindListensOnAllInterfaces() throws Exception {
        HttpServer server = HttpServer.start(0, req -> io.karatelabs.http.HttpResponse.text("ok"));
        try {
            assertTrue(server.getLocalAddress().getAddress().isAnyLocalAddress(),
                    "wildcard host should bind 0.0.0.0: " + server.getLocalAddress());
            // and it still serves on loopback (a subset of the wildcard)
            assertEquals(200, get(server.getPort()).statusCode());
        } finally {
            server.stopAndWait();
        }
    }

    @Test
    void localhostBindListensOnLoopbackOnly() throws Exception {
        HttpServer server = HttpServer.start("localhost", 0, req -> io.karatelabs.http.HttpResponse.text("ok"), null, null);
        try {
            assertTrue(server.getLocalAddress().getAddress().isLoopbackAddress(),
                    "localhost host should bind loopback: " + server.getLocalAddress());
            assertFalse(server.getLocalAddress().getAddress().isAnyLocalAddress(),
                    "localhost host must NOT bind the wildcard: " + server.getLocalAddress());
            // loopback is still reachable via 127.0.0.1 (the dev/in-process path)
            assertEquals(200, get(server.getPort()).statusCode());
        } finally {
            server.stopAndWait();
        }
    }

    /**
     * macOS hands a wildcard port-0 bind a port another socket holds on 127.0.0.1, and a loopback
     * client then reaches that socket, not the server. The kernel allocates in sequence there, so
     * stepping it to the port before the held one makes the next allocation the held port.
     */
    @Test
    void anEphemeralWildcardBindSkipsAPortHeldOnLoopback() throws Exception {
        try (ServerSocketChannel held = ServerSocketChannel.open()) {
            held.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = ((InetSocketAddress) held.getLocalAddress()).getPort();
            assumeTrue(stepTo(port - 1), "ephemeral ports are not handed out in sequence here");
            HttpServer server = HttpServer.start(0, req -> io.karatelabs.http.HttpResponse.text("ok"));
            try {
                assertNotEquals(port, server.getPort(), "the wildcard bind took a port 127.0.0.1 answers for");
                assertEquals(200, get(server.getPort()).statusCode());
            } finally {
                server.stopAndWait();
            }
        }
    }

    private static boolean stepTo(int port) throws Exception {
        for (int i = 0; i < 20_000; i++) {
            try (ServerSocketChannel probe = ServerSocketChannel.open()) {
                probe.bind(new InetSocketAddress(0));
                if (((InetSocketAddress) probe.getLocalAddress()).getPort() == port) {
                    return true;
                }
            }
        }
        return false;
    }
}
