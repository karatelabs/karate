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

/**
 * Fast ANSI colorizer for JSON text.
 * Uses a simple state-machine scanner without full JSON parsing.
 * Designed for console output of HTTP request/response bodies.
 */
public class AnsiJson {

    private AnsiJson() {
        // static methods only
    }

    /**
     * Colorize JSON text with ANSI codes.
     * Returns the original text unchanged if colors are disabled.
     *
     * @param json the JSON text to colorize
     * @return colorized JSON string
     */
    public static String colorize(String json) {
        if (!Console.isColorsEnabled() || json == null || json.isEmpty()) {
            return json;
        }
        StringBuilder sb = new StringBuilder(json.length() + 256);
        int len = json.length();
        int i = 0;

        while (i < len) {
            char c = json.charAt(i);

            if (c == '"') {
                // Start of string - scan to find the end
                int start = i;
                i++; // skip opening quote
                while (i < len) {
                    char sc = json.charAt(i);
                    if (sc == '\\' && i + 1 < len) {
                        i += 2; // skip escaped char
                    } else if (sc == '"') {
                        i++; // include closing quote
                        break;
                    } else {
                        i++;
                    }
                }
                String str = json.substring(start, i);

                // Check if this is a key (followed by ':')
                int j = i;
                while (j < len && Character.isWhitespace(json.charAt(j))) {
                    j++;
                }
                if (j < len && json.charAt(j) == ':') {
                    // It's a key
                    sb.append(Console.CYAN).append(str).append(Console.RESET);
                } else {
                    // It's a string value
                    sb.append(Console.GREEN).append(str).append(Console.RESET);
                }
            } else if (c == '{' || c == '}' || c == '[' || c == ']' || c == ':' || c == ',') {
                // Structural characters - dim
                sb.append(Console.DIM).append(c).append(Console.RESET);
                i++;
            } else if (c == '-' || (c >= '0' && c <= '9')) {
                // Number - scan the full number
                int start = i;
                while (i < len) {
                    char nc = json.charAt(i);
                    if (nc == '-' || nc == '+' || nc == '.' || nc == 'e' || nc == 'E' || (nc >= '0' && nc <= '9')) {
                        i++;
                    } else {
                        break;
                    }
                }
                sb.append(Console.YELLOW).append(json, start, i).append(Console.RESET);
            } else if (c == 't' && i + 4 <= len && json.substring(i, i + 4).equals("true")) {
                sb.append(Console.MAGENTA).append("true").append(Console.RESET);
                i += 4;
            } else if (c == 'f' && i + 5 <= len && json.substring(i, i + 5).equals("false")) {
                sb.append(Console.MAGENTA).append("false").append(Console.RESET);
                i += 5;
            } else if (c == 'n' && i + 4 <= len && json.substring(i, i + 4).equals("null")) {
                sb.append(Console.MAGENTA).append("null").append(Console.RESET);
                i += 4;
            } else {
                // Whitespace or other - pass through
                sb.append(c);
                i++;
            }
        }

        return sb.toString();
    }

}
