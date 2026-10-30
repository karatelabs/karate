package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApacheHttpClientNoContentTypeTest {

    @Test
    void aBodyWithNoContentTypeHeaderIsSentWithoutOne() throws Exception {
        List<String> seen = new ArrayList<>();
        HttpServer server = HttpServer.start(0, request -> {
            seen.add(request.getHeader("Content-Type") + "|" + request.getBodyString());
            return HttpResponse.text(200, "ok");
        });
        try (ApacheHttpClient client = new ApacheHttpClient()) {
            HttpRequest request = new HttpRequest();
            request.setMethod("POST");
            request.setUrl("http://localhost:" + server.getPort() + "/raw");
            request.setBody("x".getBytes(StandardCharsets.UTF_8));
            assertEquals(200, client.invoke(request).getStatus());
            request = new HttpRequest();
            request.setMethod("POST");
            request.setUrl("http://localhost:" + server.getPort() + "/raw");
            request.putHeader("Content-Type", "text/plain");
            request.setBody("y".getBytes(StandardCharsets.UTF_8));
            assertEquals(200, client.invoke(request).getStatus());
        } finally {
            server.stopAsync();
        }
        assertEquals(List.of("null|x", "text/plain|y"), seen);
    }

}
