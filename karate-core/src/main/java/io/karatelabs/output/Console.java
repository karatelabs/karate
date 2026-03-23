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

    private Console() {
    }

    /**
     * Strip ANSI escape codes from text for logging.
     */
    private static String stripAnsi(String text) {
        return ANSI_PATTERN.matcher(text).replaceAll("");
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
