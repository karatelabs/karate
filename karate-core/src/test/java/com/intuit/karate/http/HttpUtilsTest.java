package com.intuit.karate.http;

import com.intuit.karate.Match;
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
    
}
