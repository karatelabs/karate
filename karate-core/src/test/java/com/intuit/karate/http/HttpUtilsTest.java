package com.intuit.karate.http;

import static com.intuit.karate.TestUtils.*;
import com.intuit.karate.StringUtils;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class HttpUtilsTest {    

    @Test
    void testParseContentTypeCharset() {
        assertEquals(StandardCharsets.UTF_8, HttpUtils.parseContentTypeCharset("application/json; charset=UTF-8"));
        assertEquals(StandardCharsets.UTF_8, HttpUtils.parseContentTypeCharset("application/json; charset = UTF-8 "));
        assertEquals(StandardCharsets.UTF_8, HttpUtils.parseContentTypeCharset("application/json; charset=UTF-8; version=1.2.3"));
        assertEquals(StandardCharsets.UTF_8, HttpUtils.parseContentTypeCharset("application/json; charset = UTF-8 ; version=1.2.3"));
    }

    @Test
    void testParseContentTypeParams() {
        Map<String, String> map = HttpUtils.parseContentTypeParams("application/json");
        assertNull(map);
        map = HttpUtils.parseContentTypeParams("application/json; charset=UTF-8");
        match(map, "{ charset: 'UTF-8' }");
        map = HttpUtils.parseContentTypeParams("application/json; charset = UTF-8 ");
        match(map, "{ charset: 'UTF-8' }");
        map = HttpUtils.parseContentTypeParams("application/json; charset=UTF-8; version=1.2.3");
        match(map, "{ charset: 'UTF-8', version: '1.2.3' }");
        map = HttpUtils.parseContentTypeParams("application/json; charset = UTF-8 ; version=1.2.3");
        match(map, "{ charset: 'UTF-8', version: '1.2.3' }");
        map = HttpUtils.parseContentTypeParams("application/vnd.app.test+json;ton-version=1");
        match(map, "{ 'ton-version': '1' }");
    }

    @Test
    void testParseUriPathPatterns() {
        Map<String, String> map = HttpUtils.parseUriPattern("/cats/{id}", "/cats/1");
        match(map, "{ id: '1' }");
        map = HttpUtils.parseUriPattern("/cats/{id}/", "/cats/1"); // trailing slash
        match(map, "{ id: '1' }");
        map = HttpUtils.parseUriPattern("/cats/{id}", "/cats/1/"); // trailing slash
        match(map, "{ id: '1' }");
        map = HttpUtils.parseUriPattern("/cats/{id}", "/foo/bar");
        match(map, null);
        map = HttpUtils.parseUriPattern("/cats", "/cats/1"); // exact match
        match(map, null);
        map = HttpUtils.parseUriPattern("/{path}/{id}", "/cats/1");
        match(map, "{ path: 'cats', id: '1' }");
        map = HttpUtils.parseUriPattern("/cats/{id}/foo", "/cats/1/foo");
        match(map, "{ id: '1' }");
        map = HttpUtils.parseUriPattern("/api/{img}", "/api/billie.jpg");
        match(map, "{ img: 'billie.jpg' }");
        map = HttpUtils.parseUriPattern("/hello/{raw}", "/hello/�Ill~Formed@RequiredString!");
        match(map, "{ raw: '�Ill~Formed@RequiredString!' }");
    }
    
    static void splitUrl(String raw, String left, String right) {
        StringUtils.Pair pair = HttpUtils.parseUriIntoUrlBaseAndPath(raw);
        assertEquals(left, pair.left);
        assertEquals(right, pair.right);
    }

    @Test
    void testUriParsing() {
        splitUrl("http://foo/bar", "http://foo", "/bar");
        splitUrl("/bar", null, "/bar");
        splitUrl("/bar?baz=ban", null, "/bar?baz=ban");
        splitUrl("http://foo/bar?baz=ban", "http://foo", "/bar?baz=ban");
        splitUrl("localhost:50856", null, "");
        splitUrl("127.0.0.1:50856", null, "");
        splitUrl("http://foo:8080/bar", "http://foo:8080", "/bar");
        splitUrl("http://foo.com:8080/bar", "http://foo.com:8080", "/bar");
        splitUrl("https://api.randomuser.me/?nat=us", "https://api.randomuser.me", "/?nat=us");
    }    

}
