package com.intuit.karate.http;

import com.intuit.karate.Match;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class HttpUtilsTest {
    
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
