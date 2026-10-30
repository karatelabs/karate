package io.karatelabs.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MultiPartBuilderTest {

    static final String CRLF = "\r\n";

    @TempDir
    File tempDir;

    private static String body(MultiPartBuilder builder) {
        return new String(builder.build(), StandardCharsets.UTF_8);
    }

    @Test
    void testTextPartHasOnlyContentDisposition() {
        MultiPartBuilder builder = new MultiPartBuilder(true, "UTF-8");
        builder.part("greeting", "hello");
        String body = body(builder);
        String b = builder.getBoundary();
        assertEquals("multipart/form-data; boundary=" + b, builder.getContentTypeHeader());
        assertEquals("--" + b + CRLF
                + "Content-Disposition: form-data; name=\"greeting\"" + CRLF + CRLF
                + "hello" + CRLF
                + "--" + b + "--" + CRLF, body);
    }

    @Test
    void testTextPartWithExplicitContentType() {
        MultiPartBuilder builder = new MultiPartBuilder(true, "UTF-8");
        Map<String, Object> map = new HashMap<>();
        map.put("name", "data");
        map.put("value", "{\"a\":1}");
        map.put("contentType", "application/json");
        builder.part(map);
        String body = body(builder);
        String b = builder.getBoundary();
        assertEquals("--" + b + CRLF
                + "Content-Disposition: form-data; name=\"data\"" + CRLF
                + "Content-Type: application/json" + CRLF + CRLF
                + "{\"a\":1}" + CRLF
                + "--" + b + "--" + CRLF, body);
    }

    @Test
    void testFilePart() throws Exception {
        File file = new File(tempDir, "f.txt");
        Files.writeString(file.toPath(), "file content");
        MultiPartBuilder builder = new MultiPartBuilder(true, "UTF-8");
        builder.part("text", "one");
        builder.part("upload", file);
        String body = body(builder);
        String b = builder.getBoundary();
        assertEquals("--" + b + CRLF
                + "Content-Disposition: form-data; name=\"text\"" + CRLF + CRLF
                + "one" + CRLF
                + "--" + b + CRLF
                + "Content-Disposition: form-data; name=\"upload\"; filename=\"f.txt\"" + CRLF
                + "Content-Type: text/plain" + CRLF + CRLF
                + "file content" + CRLF
                + "--" + b + "--" + CRLF, body);
    }

    @Test
    void testNonAsciiFilename() throws Exception {
        File file = new File(tempDir, "résumé.txt");
        Files.writeString(file.toPath(), "x");
        MultiPartBuilder builder = new MultiPartBuilder(true, "UTF-8");
        builder.part("upload", file);
        String body = body(builder);
        assertTrue(body.contains("Content-Disposition: form-data; name=\"upload\"; filename=\"résumé.txt\"; filename*=UTF-8''r%C3%A9sum%C3%A9.txt" + CRLF), body);
    }

    @Test
    void testUserContentTypeBoundaryIsUsed() {
        HttpRequestBuilder http = new HttpRequestBuilder(null);
        http.url("http://localhost/upload");
        http.method("post");
        http.header("Content-Type", "multipart/form-data; boundary=myboundary");
        Map<String, Object> map = new HashMap<>();
        map.put("name", "a");
        map.put("value", "1");
        http.multiPart(map);
        HttpRequest request = http.build();
        assertEquals("multipart/form-data; boundary=myboundary", request.getHeader("Content-Type"));
        assertEquals("--myboundary" + CRLF
                + "Content-Disposition: form-data; name=\"a\"" + CRLF + CRLF
                + "1" + CRLF
                + "--myboundary--" + CRLF, request.getBodyString());
    }

}
