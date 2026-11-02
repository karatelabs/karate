package io.karatelabs.http;

import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A URL that {@link java.net.URI} rejects (a bare %, or characters such as | ^ [ ] { } left raw)
 * reaches the request line exactly as given.
 */
class ApacheHttpClientRawTargetTest {

    ServerSocket serverSocket;
    Thread acceptor;
    final List<String> requestLines = new CopyOnWriteArrayList<>();
    final List<String> hostHeaders = new CopyOnWriteArrayList<>();
    final List<String> cookies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        serverSocket = new ServerSocket(0);
        acceptor = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                    String requestLine = in.readLine();
                    requestLines.add(requestLine);
                    String line;
                    String cookie = null;
                    while ((line = in.readLine()) != null && !line.isEmpty()) {
                        if (line.toLowerCase().startsWith("host:")) {
                            hostHeaders.add(line.substring(5).trim());
                        } else if (line.toLowerCase().startsWith("cookie:")) {
                            cookie = line.substring(7).trim();
                        }
                    }
                    cookies.add(String.valueOf(cookie));
                    OutputStream out = socket.getOutputStream();
                    String whatwg = requestLine.contains("/to-absolute") ? "http://localhost:" + serverSocket.getLocalPort() + "/query?q={(\"*\")}&b=a b#frag"
                            : requestLine.contains("/to-relative") ? "query/%|{x}?q={(\"*\")}" : null;
                    String response = whatwg != null
                            ? "HTTP/1.1 302 Found\r\nLocation: " + whatwg + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            : requestLine.contains("/redirect")
                            ? "HTTP/1.1 302 Found\r\nLocation: landed?x=1\r\nSet-Cookie: a=1; Path=/\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            : "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok";
                    out.write(response.getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } catch (Exception e) {
                    // closed
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @AfterEach
    void stop() throws Exception {
        serverSocket.close();
    }

    String send(String pathAndQuery) {
        try (ApacheHttpClient client = new ApacheHttpClient()) {
            HttpRequest request = new HttpRequest();
            request.setUrl("http://localhost:" + serverSocket.getLocalPort() + pathAndQuery);
            request.setMethod("GET");
            HttpResponse response = client.invoke(request);
            assertEquals(200, response.getStatus());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return requestLines.get(requestLines.size() - 1);
    }

    @Test
    void bracesAndEscapesInQuery() {
        assertEquals("GET /query?q={(%22*%22)} HTTP/1.1", send("/query?q={(%22*%22)}"));
    }

    @Test
    void barepercentInQuery() {
        assertEquals("GET /get?q=(%{^*^}%) HTTP/1.1", send("/get?q=(%{^*^}%)"));
    }

    @Test
    void rawCharactersInPath() {
        assertEquals("GET /start/%/|/[/]/^/end HTTP/1.1", send("/start/%/|/[/]/^/end"));
        assertEquals("localhost:" + serverSocket.getLocalPort(), hostHeaders.get(hostHeaders.size() - 1));
    }

    @Test
    void viaBuilderWithCookieJarAndRelativeRedirect() throws Exception {
        try (ApacheHttpClient client = new ApacheHttpClient(new BasicCookieStore())) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.url("http://localhost:" + serverSocket.getLocalPort() + "/start/%/|/redirect?q={^}");
            http.method("GET");
            HttpResponse response = http.invoke();
            assertEquals(200, response.getStatus());
        }
        assertEquals(List.of(
                "GET /start/%/|/redirect?q={^} HTTP/1.1",
                "GET /start/%25/%7C/landed?x=1 HTTP/1.1"), requestLines);
        assertEquals("a=1", cookies.get(1));
    }

    @Test
    void aLocationUriRejectsIsFollowedAsAWhatwgParserSendsIt() {
        assertEquals("GET /query?q={(%22*%22)}&b=a%20b HTTP/1.1", send("/to-absolute"));
        assertEquals("GET /dir/query/%|%7Bx%7D?q={(%22*%22)} HTTP/1.1", send("/dir/to-relative"));
    }

    @Test
    void noDefaultHostSendsNone() throws Exception {
        try (ApacheHttpClient client = new ApacheHttpClient()) {
            HttpRequest request = new HttpRequest();
            request.setUrl("http://localhost:" + serverSocket.getLocalPort() + "/");
            request.setMethod("GET");
            request.setDefaultHost(false);
            assertEquals(200, client.invoke(request).getStatus());
            request = new HttpRequest();
            request.setUrl("http://localhost:" + serverSocket.getLocalPort() + "/");
            request.setMethod("GET");
            request.putHeader("Host", "mine");
            request.setDefaultHost(false);
            client.invoke(request);
        }
        assertEquals(List.of("mine"), hostHeaders, "none, then the request's own");
    }

    @Test
    void validUrlUnchanged() {
        assertEquals("GET /a%20b/c?x=%2F&y=1 HTTP/1.1", send("/a%20b/c?x=%2F&y=1"));
    }

}
