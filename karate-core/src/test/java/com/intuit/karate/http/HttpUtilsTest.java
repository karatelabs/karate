package com.intuit.karate.http;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Match;
import com.intuit.karate.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class HttpUtilsTest {

    @Test
    public void testParseContentTypeCharset() {
        assertEquals(FileUtils.UTF8, HttpUtils.parseContentTypeCharset("application/json; charset=UTF-8"));
        assertEquals(FileUtils.UTF8, HttpUtils.parseContentTypeCharset("application/json; charset = UTF-8 "));
        assertEquals(FileUtils.UTF8, HttpUtils.parseContentTypeCharset("application/json; charset=UTF-8; version=1.2.3"));
        assertEquals(FileUtils.UTF8, HttpUtils.parseContentTypeCharset("application/json; charset = UTF-8 ; version=1.2.3"));
    }

    @Test
    public void testParseContentTypeParams() {
        Map<String, String> map = HttpUtils.parseContentTypeParams("application/json");
        assertNull(map);
        map = HttpUtils.parseContentTypeParams("application/json; charset=UTF-8");
        Match.equals(map, "{ charset: 'UTF-8' }");
        map = HttpUtils.parseContentTypeParams("application/json; charset = UTF-8 ");
        Match.equals(map, "{ charset: 'UTF-8' }");
        map = HttpUtils.parseContentTypeParams("application/json; charset=UTF-8; version=1.2.3");
        Match.equals(map, "{ charset: 'UTF-8', version: '1.2.3' }");
        map = HttpUtils.parseContentTypeParams("application/json; charset = UTF-8 ; version=1.2.3");
        Match.equals(map, "{ charset: 'UTF-8', version: '1.2.3' }");
        map = HttpUtils.parseContentTypeParams("application/vnd.app.test+json;ton-version=1");
        Match.equals(map, "{ 'ton-version': '1' }");
    }

    @Test
    public void testParseUriPathPatterns() {
        Map<String, String> map = HttpUtils.parseUriPattern("/cats/{id}", "/cats/1");
        Match.equals(map, "{ id: '1' }");
        map = HttpUtils.parseUriPattern("/cats/{id}/", "/cats/1"); // trailing slash
        Match.equals(map, "{ id: '1' }");
        map = HttpUtils.parseUriPattern("/cats/{id}", "/cats/1/"); // trailing slash
        Match.equals(map, "{ id: '1' }");
        map = HttpUtils.parseUriPattern("/cats/{id}", "/foo/bar");
        Match.equals(map, null);
        map = HttpUtils.parseUriPattern("/cats", "/cats/1"); // exact match
        Match.equals(map, null);
        map = HttpUtils.parseUriPattern("/{path}/{id}", "/cats/1");
        Match.equals(map, "{ path: 'cats', id: '1' }");
        map = HttpUtils.parseUriPattern("/cats/{id}/foo", "/cats/1/foo");
        Match.equals(map, "{ id: '1' }");
        map = HttpUtils.parseUriPattern("/api/{img}", "/api/billie.jpg");
        Match.equals(map, "{ img: 'billie.jpg' }");
        map = HttpUtils.parseUriPattern("/{greedyPath:.+}", "/cats/1");
        Match.equals(map, "{ greedyPath: 'cats/1' }");
        map = HttpUtils.parseUriPattern("/cats/v{partialPath}x", "/cats/v1x");
        Match.equals(map, "{ partialPath: '1' }");
        map = HttpUtils.parseUriPattern("/cats/{duplicate}/{duplicate}", "/cats/v1/1043");
        Match.equals(map, "{ duplicate: 'v1', 'duplicate@2': '1043' }");
        map = HttpUtils.parseUriPattern("/cats/{}/{}", "/cats/v1/1043");
        Match.equals(map, "{ ignored: 'v1', 'ignored@2': '1043' }");
    }

    @Test
    public void testCalculatePathMatchScore() {
        List<Integer> score = HttpUtils.calculatePathMatchScore("/cats/{id}");
        Match.equals(score, "[6,1,0]");
        score = HttpUtils.calculatePathMatchScore("/cats/1");
        Match.equals(score, "[7,0,0]");
        score = HttpUtils.calculatePathMatchScore("/cats/1/");
        Match.equals(score, "[7,0,0]");
        score = HttpUtils.calculatePathMatchScore("cats/1/");
        Match.equals(score, "[7,0,0]");
    }

    @Test
    public void testParseCookieString() {
        String header = "Set-Cookie: foo=\"bar\";Version=1";
        Map<String, Cookie> map = HttpUtils.parseCookieHeaderString(header);
        Match.equals(map, "{ foo: '#object' }"); // only one entry
        Match.contains(map.get("foo"), "{ name: 'foo', value: 'bar' }");
    }

    @Test
    public void testCreateCookieString() {
        Cookie c1 = new Cookie("foo", "bar");
        Cookie c2 = new Cookie("hello", "world");
        String header = HttpUtils.createCookieHeaderValue(Arrays.asList(c1, c2));
        Match.equalsText(header, "foo=bar; hello=world");
    }

}
