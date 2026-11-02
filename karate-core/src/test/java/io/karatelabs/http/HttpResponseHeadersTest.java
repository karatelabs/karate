package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpResponseHeadersTest {

    @Test
    void headersKeepTheOrderTheyArrivedIn() {
        Map<String, List<String>> wire = new LinkedHashMap<>();
        for (String name : List.of("access-control-allow-origin", "access-control-allow-credentials", "content-type", "set-cookie",
                "Date", "Connection", "Keep-Alive", "Transfer-Encoding", "X-B", "X-A", "ETag", "Vary")) {
            wire.put(name, List.of("v"));
        }
        HttpResponse response = new HttpResponse();
        response.setHeaders(wire);
        response.setHeader("X-Last", "v");
        List<String> expected = new ArrayList<>(wire.keySet());
        expected.add("X-Last");
        assertEquals(expected, new ArrayList<>(response.getHeaders().keySet()));
    }

}
