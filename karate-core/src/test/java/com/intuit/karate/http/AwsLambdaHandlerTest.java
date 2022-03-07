package com.intuit.karate.http;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Json;
import com.intuit.karate.Match;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class AwsLambdaHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(AwsLambdaHandlerTest.class);

    AwsLambdaHandler handler;
    String sessionId;

    void init(boolean classpath) {
        ServerConfig config = classpath ? new ServerConfig("classpath:demo") : new ServerConfig("src/test/java/demo");
        config.autoCreateSession(true);
        handler = new AwsLambdaHandler(new RequestHandler(config));
    }

    String handle(String file) {
        Map<String, Object> res = handleAsMap(file);
        Map<String, String> headers = (Map) res.get("headers");
        String cookie = headers.get("Set-Cookie");
        if (cookie != null) {
            sessionId = cookie;
        }
        return (String) res.get("body");
    }

    Map<String, Object> handleAsMap(String file) {
        InputStream is = getClass().getResourceAsStream(file);
        String body = FileUtils.toString(is);
        if (sessionId != null) {
            body = body.replace("%%cookie%%", sessionId);
        }
        is = new ByteArrayInputStream(FileUtils.toBytes(body));
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            handler.handle(is, os);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        byte[] bytes = os.toByteArray();
        String json = FileUtils.toString(bytes);
        return Json.of(json).asMap();
    }

    void testFormInternal(boolean root) {
        String name = root ? "root.json" : "index.json";
        String body = handle(name);
        // logger.debug("{}", body);
        assertTrue(body.startsWith("<!doctype html>"));
        assertTrue(body.contains("<span>John Smith</span>"));
        assertTrue(body.contains("<td>Apple</td>"));
        assertTrue(body.contains("<td>Orange</td>"));
        assertTrue(body.contains("<span>Billie</span>"));
        body = handle("form.json");
        assertTrue(body.contains("<span>John</span>"));
    }

    @Test
    void testFormClassPath() {
        init(true);
        testFormInternal(false);
    }
    
    @Test
    void testRootClassPath() {
        init(true);
        testFormInternal(true);
    }    

    @Test
    void testRootFileSystem() {
        init(false);
        testFormInternal(true);
    }

    void testApiInternal() {
        Map<String, Object> res = handleAsMap("api.json");
        Map<String, String> headers = (Map) res.get("headers");
        String val = headers.get("foo");
        assertEquals(val, "bar");
        String body = (String) res.get("body");
        assertEquals("{\"hello\":\"world\"}", body);
        Integer status = (Integer) res.get("statusCode");
        assertEquals(201, status);
    }

    @Test
    void testApiClassPath() {
        init(true);
        testApiInternal();
    }

    @Test
    void testApiFileSystem() {
        init(false);
        testApiInternal();
    }
    
    void testCatsInternal() {
        Map<String, Object> res = handleAsMap("cats.json");
        Json body = Json.of(res.get("body"));
        Match.that(body.asMap()).isEqualTo("{ name: 'Billie', id: '#number' }");
        Integer status = (Integer) res.get("statusCode");
        assertEquals(200, status);
    }
    
    @Test
    void testCatsClassPath() {
        init(true);
        testCatsInternal();
    }    
    
    @Test
    void testCatsFileSystem() {
        init(false);
        testCatsInternal();
    }     

}
