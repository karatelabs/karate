package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathSecurityTest {

    @Test
    void testSafePathsAllowed() {
        assertTrue(PathSecurity.isSafe("/pub/app.js"));
        assertTrue(PathSecurity.isSafe("/api/users"));
        assertTrue(PathSecurity.isSafe("/signin"));
        assertTrue(PathSecurity.isSafe("/"));
        assertTrue(PathSecurity.isSafe("/some/deep/nested/path.html"));
        assertTrue(PathSecurity.isSafe("index.html"));
        assertTrue(PathSecurity.isSafe("pub/styles.css"));
    }

    @Test
    void testNullPathSafe() {
        assertTrue(PathSecurity.isSafe(null));
    }

    @Test
    void testDotDotSlashBlocked() {
        assertFalse(PathSecurity.isSafe("../etc/passwd"));
        assertFalse(PathSecurity.isSafe("/pub/../../../etc/passwd"));
        assertFalse(PathSecurity.isSafe("/api/../../secret"));
        assertFalse(PathSecurity.isSafe("foo/../bar/../../../etc/passwd"));
    }

    @Test
    void testSlashDotDotBlocked() {
        assertFalse(PathSecurity.isSafe("/pub/.."));
        assertFalse(PathSecurity.isSafe("/foo/bar/.."));
    }

    @Test
    void testStandaloneDotDotBlocked() {
        assertFalse(PathSecurity.isSafe(".."));
        assertFalse(PathSecurity.isSafe("../"));
    }

    @Test
    void testBackslashVariantsBlocked() {
        assertFalse(PathSecurity.isSafe("..\\etc\\passwd"));
        assertFalse(PathSecurity.isSafe("/pub\\..\\..\\secret"));
        assertFalse(PathSecurity.isSafe("/foo\\.."));
    }

    @Test
    void testUrlEncodedDotDotBlocked() {
        // %2e = '.'
        assertFalse(PathSecurity.isSafe("/%2e%2e/etc/passwd"));
        assertFalse(PathSecurity.isSafe("/%2E%2E/etc/passwd"));
        assertFalse(PathSecurity.isSafe("/pub/%2e%2e/%2e%2e/secret"));
    }

    @Test
    void testUrlEncodedSlashBlocked() {
        // %2f = '/'
        assertFalse(PathSecurity.isSafe("/pub/..%2f..%2fetc/passwd"));
        assertFalse(PathSecurity.isSafe("/pub/..%2Fetc"));
    }

    @Test
    void testDoubleEncodedBlocked() {
        // %252e = '%2e' after first decode
        assertFalse(PathSecurity.isSafe("/%252e%252e/etc/passwd"));
        assertFalse(PathSecurity.isSafe("/pub/%252e%252e/%252e%252e/secret"));
    }

    @Test
    void testMixedEncodingBlocked() {
        assertFalse(PathSecurity.isSafe("/pub/..%2f../etc/passwd"));
        assertFalse(PathSecurity.isSafe("/pub/%2e./secret"));
    }

    @Test
    void testMalformedEncodingUnsafe() {
        // Incomplete encoding sequences
        assertFalse(PathSecurity.isSafe("/pub/%2"));
        assertFalse(PathSecurity.isSafe("/pub/%GG"));
    }

    @Test
    void testValidateThrowsOnUnsafePath() {
        PathSecurity.PathTraversalException ex = assertThrows(
                PathSecurity.PathTraversalException.class,
                () -> PathSecurity.validate("/../etc/passwd")
        );
        assertTrue(ex.getMessage().contains("Path traversal detected"));
        assertEquals("/../etc/passwd", ex.getPath());
    }

    @Test
    void testValidateAcceptsSafePath() {
        assertDoesNotThrow(() -> PathSecurity.validate("/pub/app.js"));
        assertDoesNotThrow(() -> PathSecurity.validate("/api/users"));
        assertDoesNotThrow(() -> PathSecurity.validate(null));
    }

    @Test
    void testPathWithDotsButNotTraversal() {
        // Files with dots in the name should be allowed
        assertTrue(PathSecurity.isSafe("/pub/app.min.js"));
        assertTrue(PathSecurity.isSafe("/api/v1.0/users"));
        assertTrue(PathSecurity.isSafe("/.hidden/file"));
        assertTrue(PathSecurity.isSafe("/path/to/file.tar.gz"));
    }

}
