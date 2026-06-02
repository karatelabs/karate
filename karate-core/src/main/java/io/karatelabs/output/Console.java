/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.regex.Pattern;

/**
 * Console output utilities with ANSI color support.
 * Also sends a copy (stripped of ANSI codes) to the karate.console logger at TRACE level.
 */
public final class Console {

    private static final Logger logger = LogContext.CONSOLE_LOGGER;

    // Pattern to strip ANSI escape codes for logger
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[;\\d]*m");

    // In-band markers wrapping a highlightable body inside the report log buffer.
    // HttpLogger writes:  BODY_OPEN + lang + BODY_LANG_END + <body text> + BODY_CLOSE
    // The chars are C0 controls that never appear literally in JSON/XML/text bodies
    // (they would be escaped), so they are safe as invisible delimiters. The HTML
    // report splits on them (splitLog); every other consumer strips them out.
    public static final char BODY_OPEN = '\u0000';
    public static final char BODY_LANG_END = '\u0001';
    public static final char BODY_CLOSE = '\u0002';

    // Removes the open+lang prefix and the close marker, leaving the body text.
    private static final Pattern SENTINEL_PATTERN =
            Pattern.compile("\u0000[^\u0001]*\u0001|\u0002");

    private Console() {
    }

    /**
     * Strip ANSI escape codes <em>and</em> body sentinels — everything a plain-text
     * consumer (JUnit XML, Cucumber JSON, JSONL, console mirror) should never see.
     * The HTML report is the only consumer that wants the sentinels; it reads the
     * raw log via {@link #splitLog(String)} instead.
     */
    public static String stripAnsi(String text) {
        if (text == null) {
            return null;
        }
        String noAnsi = ANSI_PATTERN.matcher(text).replaceAll("");
        return SENTINEL_PATTERN.matcher(noAnsi).replaceAll("");
    }

    /**
     * Remove body sentinels but keep ANSI color codes — for the console/SLF4J
     * mirror, which wants the colored body shown but not the invisible markers.
     */
    public static String stripSentinels(String text) {
        if (text == null) {
            return null;
        }
        return SENTINEL_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * Split a raw report log into ordered segments for the HTML report. Each segment
     * is either {@code {"text": <plain text>}} or, for a body wrapped in sentinels,
     * {@code {"lang": <grammar>, "code": <body text>}}. ANSI codes are stripped from
     * every segment; only the sentinels select where a highlightable code block
     * begins and ends. The request/header lines around a body stay as plain-text
     * segments, so whitespace is preserved verbatim.
     */
    public static java.util.List<java.util.Map<String, String>> splitLog(String text) {
        java.util.List<java.util.Map<String, String>> segments = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }
        int i = 0;
        int len = text.length();
        while (i < len) {
            int open = text.indexOf(BODY_OPEN, i);
            if (open < 0) {
                addTextSegment(segments, text.substring(i));
                break;
            }
            int langEnd = text.indexOf(BODY_LANG_END, open + 1);
            int close = langEnd < 0 ? -1 : text.indexOf(BODY_CLOSE, langEnd + 1);
            if (langEnd < 0 || close < 0) {
                // Malformed / truncated markers — treat the rest as plain text.
                addTextSegment(segments, text.substring(i));
                break;
            }
            addTextSegment(segments, text.substring(i, open));
            java.util.Map<String, String> seg = new java.util.LinkedHashMap<>();
            seg.put("lang", text.substring(open + 1, langEnd));
            seg.put("code", ANSI_PATTERN.matcher(text.substring(langEnd + 1, close)).replaceAll(""));
            segments.add(seg);
            i = close + 1;
        }
        return segments;
    }

    private static void addTextSegment(java.util.List<java.util.Map<String, String>> segments, String raw) {
        if (raw.isEmpty()) {
            return;
        }
        String stripped = ANSI_PATTERN.matcher(raw).replaceAll("");
        if (stripped.isEmpty()) {
            return;
        }
        java.util.Map<String, String> seg = new java.util.LinkedHashMap<>();
        seg.put("text", stripped);
        segments.add(seg);
    }

    // ANSI escape codes
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";

    // Colors
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    // Bright colors
    public static final String BRIGHT_RED = "\u001B[91m";
    public static final String BRIGHT_GREEN = "\u001B[92m";
    public static final String BRIGHT_YELLOW = "\u001B[93m";
    public static final String BRIGHT_CYAN = "\u001B[96m";

    // Dim/muted
    public static final String DIM = "\u001B[2m";
    public static final String GREY = "\u001B[90m";

    private static boolean colorsEnabled = detectColorSupport();
    private static PrintStream out = System.out;

    private static boolean detectColorSupport() {
        // Check common environment indicators
        String term = System.getenv("TERM");
        String colorterm = System.getenv("COLORTERM");
        String forceColor = System.getenv("FORCE_COLOR");
        String noColor = System.getenv("NO_COLOR");

        // NO_COLOR takes precedence (https://no-color.org/)
        if (noColor != null) {
            return false;
        }

        // FORCE_COLOR overrides detection
        if (forceColor != null && !forceColor.equals("0")) {
            return true;
        }

        // Check if terminal supports colors
        if (term != null && (term.contains("color") || term.contains("xterm") || term.contains("256"))) {
            return true;
        }

        if (colorterm != null) {
            return true;
        }

        // On Windows, check for modern terminal
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            // Windows 10+ supports ANSI in Windows Terminal and newer cmd.exe
            String wtSession = System.getenv("WT_SESSION");
            return wtSession != null;
        }

        // Default to enabled on Unix-like systems with a TTY
        return System.console() != null;
    }

    public static void setColorsEnabled(boolean enabled) {
        colorsEnabled = enabled;
    }

    public static boolean isColorsEnabled() {
        return colorsEnabled;
    }

    public static void setOutput(PrintStream output) {
        out = output;
    }

    // ========== Formatting helpers ==========

    public static String color(String text, String... codes) {
        if (!colorsEnabled || codes.length == 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (String code : codes) {
            sb.append(code);
        }
        sb.append(text);
        sb.append(RESET);
        return sb.toString();
    }

    public static String bold(String text) {
        return color(text, BOLD);
    }

    public static String red(String text) {
        return color(text, RED);
    }

    public static String green(String text) {
        return color(text, GREEN);
    }

    public static String yellow(String text) {
        return color(text, YELLOW);
    }

    public static String cyan(String text) {
        return color(text, CYAN);
    }

    public static String brightRed(String text) {
        return color(text, BRIGHT_RED);
    }

    public static String brightGreen(String text) {
        return color(text, BRIGHT_GREEN);
    }

    public static String brightYellow(String text) {
        return color(text, BRIGHT_YELLOW);
    }

    public static String grey(String text) {
        return color(text, GREY);
    }

    public static String dim(String text) {
        return color(text, DIM);
    }

    // ========== Semantic formatting ==========

    public static String pass(String text) {
        return color(text, BRIGHT_GREEN);
    }

    public static String fail(String text) {
        return color(text, BRIGHT_RED, BOLD);
    }

    public static String warn(String text) {
        return color(text, BRIGHT_YELLOW);
    }

    public static String info(String text) {
        return color(text, CYAN);
    }

    public static String label(String text) {
        return color(text, WHITE, BOLD);
    }

    // ========== Output ==========

    public static void println(String text) {
        out.println(text);
        // Send stripped copy to logger at TRACE level (avoids double-logging)
        if (logger.isTraceEnabled()) {
            logger.trace(stripAnsi(text));
        }
    }

    public static void println() {
        out.println();
    }

    public static void print(String text) {
        out.print(text);
        // Send stripped copy to logger at TRACE level
        if (logger.isTraceEnabled()) {
            logger.trace(stripAnsi(text));
        }
    }

    // ========== Utility ==========

    public static String line(int length) {
        return "=".repeat(length);
    }

    public static String line() {
        return line(60);
    }

}
