package com.intuit.karate.netty;

import com.intuit.karate.StringUtils;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class NettyUtilsTest {

    private static void test(String raw, String left, String right) {
        StringUtils.Pair pair = NettyUtils.parseUriIntoUrlBaseAndPath(raw);
        assertEquals(left, pair.left);
        assertEquals(right, pair.right);
    }

    @Test
    public void testUriParsing() {
        test("http://foo/bar", "http://foo", "/bar");
        test("/bar", null, "/bar");
        test("/bar?baz=ban", null, "/bar?baz=ban");
        test("http://foo/bar?baz=ban", "http://foo", "/bar?baz=ban");
        test("localhost:50856", null, "");
        test("127.0.0.1:50856", null, "");
        test("http://foo:8080/bar", "http://foo:8080", "/bar");
        test("http://foo.com:8080/bar", "http://foo.com:8080", "/bar");
        test("https://api.randomuser.me/?nat=us", "https://api.randomuser.me", "/?nat=us");
    }

}
