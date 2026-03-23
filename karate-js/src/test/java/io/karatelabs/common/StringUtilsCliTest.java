package io.karatelabs.common;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CLI command utilities in StringUtils.
 * These tests are designed to work across all operating systems.
 */
class StringUtilsCliTest {

    static final Logger logger = LoggerFactory.getLogger(StringUtilsCliTest.class);

    // ==================== tokenizeCliCommand Tests ====================

    @Test
    void testTokenizeSimpleCommand() {
        String[] result = StringUtils.tokenizeCliCommand("curl https://example.com");
        assertArrayEquals(new String[]{"curl", "https://example.com"}, result);
    }

    @Test
    void testTokenizeSingleQuotes() {
        String[] result = StringUtils.tokenizeCliCommand("curl 'https://example.com'");
        assertArrayEquals(new String[]{"curl", "https://example.com"}, result);
    }

    @Test
    void testTokenizeDoubleQuotes() {
        String[] result = StringUtils.tokenizeCliCommand("curl \"https://example.com\"");
        assertArrayEquals(new String[]{"curl", "https://example.com"}, result);
    }

    @Test
    void testTokenizeWithSpacesInQuotes() {
        String[] result = StringUtils.tokenizeCliCommand("echo 'hello world'");
        assertArrayEquals(new String[]{"echo", "hello world"}, result);
    }

    @Test
    void testTokenizeMixedQuotes() {
        String[] result = StringUtils.tokenizeCliCommand("cmd 'single quote' \"double quote\" noQuote");
        assertArrayEquals(new String[]{"cmd", "single quote", "double quote", "noQuote"}, result);
    }

    @Test
    void testTokenizeEmptyQuotes() {
        String[] result = StringUtils.tokenizeCliCommand("cmd '' \"\"");
        assertArrayEquals(new String[]{"cmd", "", ""}, result);
    }

    @Test
    void testTokenizeSpecialCharsInQuotes() {
        String[] result = StringUtils.tokenizeCliCommand("curl -H 'Content-Type: application/json'");
        assertArrayEquals(new String[]{"curl", "-H", "Content-Type: application/json"}, result);
    }

    @Test
    void testTokenizeMultipleSpaces() {
        String[] result = StringUtils.tokenizeCliCommand("curl  https://example.com   -v");
        assertArrayEquals(new String[]{"curl", "https://example.com", "-v"}, result);
    }

    @Test
    void testTokenizeWithNewlines() {
        String[] result = StringUtils.tokenizeCliCommand("curl\n'https://example.com'");
        // Should handle newlines as whitespace
        assertArrayEquals(new String[]{"curl", "https://example.com"}, result);
    }

    @Test
    void testTokenizeComplexCurlCommand() {
        String cmd = "curl -X POST 'https://api.example.com/users' -H 'Content-Type: application/json' -d '{\"name\":\"John\"}'";
        String[] result = StringUtils.tokenizeCliCommand(cmd);
        assertArrayEquals(new String[]{
            "curl", "-X", "POST",
            "https://api.example.com/users",
            "-H", "Content-Type: application/json",
            "-d", "{\"name\":\"John\"}"
        }, result);
    }

    // ==================== shellEscapeUnix Tests ====================

    @Test
    void testUnixEscapeSimpleString() {
        String result = StringUtils.shellEscapeUnix("hello");
        assertEquals("'hello'", result);
    }

    @Test
    void testUnixEscapeWithSingleQuote() {
        String result = StringUtils.shellEscapeUnix("O'Brien");
        assertEquals("'O'\\''Brien'", result);
    }

    @Test
    void testUnixEscapeWithMultipleSingleQuotes() {
        String result = StringUtils.shellEscapeUnix("It's John's book");
        assertEquals("'It'\\''s John'\\''s book'", result);
    }

    @Test
    void testUnixEscapeSpecialChars() {
        String result = StringUtils.shellEscapeUnix("test & special");
        assertEquals("'test & special'", result);
    }

    @Test
    void testUnixEscapeDollarSign() {
        String result = StringUtils.shellEscapeUnix("$PATH");
        assertEquals("'$PATH'", result);
    }

    @Test
    void testUnixEscapeBackslash() {
        String result = StringUtils.shellEscapeUnix("C:\\Users\\test");
        assertEquals("'C:\\Users\\test'", result);
    }

    @Test
    void testUnixEscapeNull() {
        String result = StringUtils.shellEscapeUnix(null);
        assertEquals("''", result);
    }

    @Test
    void testUnixEscapeEmptyString() {
        String result = StringUtils.shellEscapeUnix("");
        assertEquals("''", result);
    }

    @Test
    void testUnixEscapeNewline() {
        String result = StringUtils.shellEscapeUnix("line1\nline2");
        assertEquals("'line1\nline2'", result);
    }

    // ==================== shellEscapeWindows Tests ====================

    @Test
    void testWindowsEscapeSimpleString() {
        String result = StringUtils.shellEscapeWindows("hello");
        assertEquals("\"hello\"", result);
    }

    @Test
    void testWindowsEscapeWithDoubleQuote() {
        String result = StringUtils.shellEscapeWindows("say \"hello\"");
        assertEquals("\"say \"\"hello\"\"\"", result);
    }

    @Test
    void testWindowsEscapeAmpersand() {
        String result = StringUtils.shellEscapeWindows("test & special");
        assertEquals("\"test ^& special\"", result);
    }

    @Test
    void testWindowsEscapePipe() {
        String result = StringUtils.shellEscapeWindows("cmd | grep test");
        assertEquals("\"cmd ^| grep test\"", result);
    }

    @Test
    void testWindowsEscapeRedirection() {
        String result = StringUtils.shellEscapeWindows("output > file.txt");
        assertEquals("\"output ^> file.txt\"", result);
    }

    @Test
    void testWindowsEscapeLessThan() {
        String result = StringUtils.shellEscapeWindows("input < file.txt");
        assertEquals("\"input ^< file.txt\"", result);
    }

    @Test
    void testWindowsEscapePercent() {
        String result = StringUtils.shellEscapeWindows("PATH=%PATH%");
        assertEquals("\"PATH=%%PATH%%\"", result);
    }

    @Test
    void testWindowsEscapeCaret() {
        String result = StringUtils.shellEscapeWindows("test^test");
        assertEquals("\"test^^test\"", result);
    }

    @Test
    void testWindowsEscapeNull() {
        String result = StringUtils.shellEscapeWindows(null);
        assertEquals("\"\"", result);
    }

    @Test
    void testWindowsEscapeEmptyString() {
        String result = StringUtils.shellEscapeWindows("");
        assertEquals("\"\"", result);
    }

    @Test
    void testWindowsEscapeAllSpecialChars() {
        String result = StringUtils.shellEscapeWindows("^&|<>%");
        assertEquals("\"^^^&^|^<^>%%\"", result);
    }

    // ==================== shellEscape (OS-aware) Tests ====================

    @Test
    void testShellEscapeDetectsOS() {
        String result = StringUtils.shellEscape("test");
        // Should wrap in quotes (either single or double depending on OS)
        assertTrue(result.startsWith("'") || result.startsWith("\""));
        assertTrue(result.endsWith("'") || result.endsWith("\""));
    }

    @Test
    void testShellEscapeWithQuotes() {
        String result = StringUtils.shellEscape("It's a test");

        // Verify it's properly escaped for the OS
        if (OsUtils.isWindows()) {
            assertTrue(result.startsWith("\"") && result.endsWith("\""));
        } else {
            assertTrue(result.startsWith("'"));
            assertTrue(result.contains("'\\''") || result.contains("'\\'"));
        }
    }

    // ==================== buildShellCommand Tests ====================

    @Test
    void testBuildShellCommandSimple() {
        String result = StringUtils.buildShellCommand("curl", "https://example.com");

        assertTrue(result.startsWith("curl "));
        assertTrue(result.contains("example.com"));
    }

    @Test
    void testBuildShellCommandMultipleArgs() {
        String result = StringUtils.buildShellCommand("curl", "-X", "POST", "https://example.com");

        assertTrue(result.startsWith("curl "));
        assertTrue(result.contains("POST"));
        assertTrue(result.contains("example.com"));
    }

    @Test
    void testBuildShellCommandWithSpecialChars() {
        String result = StringUtils.buildShellCommand("echo", "Hello World");
        assertTrue(result.startsWith("echo "));
        // Should be quoted
        assertTrue(result.contains("'") || result.contains("\""));
    }

    @Test
    void testBuildShellCommandNoArgs() {
        String result = StringUtils.buildShellCommand("ls");
        assertEquals("ls", result);
    }

    // ==================== Edge Cases and Integration Tests ====================

    @Test
    void testRoundTripTokenizeAndEscape() {
        // Test that we can parse and re-escape correctly
        String original = "curl 'https://example.com' -H 'Content-Type: application/json'";
        String[] tokens = StringUtils.tokenizeCliCommand(original);

        // Re-escape the URL and header
        String escapedUrl = StringUtils.shellEscape("https://example.com");
        String escapedHeader = StringUtils.shellEscape("Content-Type: application/json");

        // Verify tokens were extracted correctly
        assertEquals(4, tokens.length);
        assertEquals("curl", tokens[0]);
        assertEquals("https://example.com", tokens[1]);
        assertEquals("-H", tokens[2]);
        assertEquals("Content-Type: application/json", tokens[3]);
    }

    @Test
    void testJsonInCommand() {
        String json = "{\"name\":\"O'Brien\",\"age\":30}";
        String escaped = StringUtils.shellEscape(json);

        // Should be properly escaped
        assertNotNull(escaped);
        assertTrue(escaped.length() > json.length());
    }

    @Test
    void testUrlWithSpecialChars() {
        String url = "https://example.com/search?q=test&category=books";
        String escaped = StringUtils.shellEscape(url);

        // Should wrap in quotes to protect special chars
        assertTrue(escaped.startsWith("'") || escaped.startsWith("\""));
    }

    @Test
    void testEmptyCommand() {
        String[] result = StringUtils.tokenizeCliCommand("");
        assertEquals(0, result.length);
    }

    @Test
    void testWhitespaceOnlyCommand() {
        String[] result = StringUtils.tokenizeCliCommand("   ");
        assertEquals(0, result.length);
    }

    @Test
    void testUnicodeInCommand() {
        String unicode = "Hello 世界 🌍";
        String escaped = StringUtils.shellEscape(unicode);

        assertNotNull(escaped);
        assertTrue(escaped.contains("世界"));
        assertTrue(escaped.contains("🌍"));
    }

    @Test
    void testPathSeparators() {
        // Unix path
        String unixPath = "/usr/local/bin/myapp";
        String escapedUnix = StringUtils.shellEscapeUnix(unixPath);
        assertEquals("'/usr/local/bin/myapp'", escapedUnix);

        // Windows path
        String winPath = "C:\\Program Files\\MyApp\\app.exe";
        String escapedWin = StringUtils.shellEscapeWindows(winPath);
        assertEquals("\"C:\\Program Files\\MyApp\\app.exe\"", escapedWin);
    }
}
