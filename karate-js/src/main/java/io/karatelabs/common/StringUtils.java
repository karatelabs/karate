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
package io.karatelabs.common;

import io.karatelabs.js.JsFunction;
import io.karatelabs.js.JsValue;
import io.karatelabs.js.ObjectLike;
import io.karatelabs.js.Terms;
import net.minidev.json.JSONStyle;
import net.minidev.json.JSONValue;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StringUtils {

    private StringUtils() {
        // only static methods
    }

    public static final String EMPTY = "";

    public static boolean looksLikeJson(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        if (s.charAt(0) == ' ') {
            s = s.trim();
            if (s.isEmpty()) {
                return false;
            }
        }
        return s.charAt(0) == '{' || s.charAt(0) == '[';
    }

    public static boolean isXml(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        if (s.charAt(0) == ' ') {
            s = s.trim();
            if (s.isEmpty()) {
                return false;
            }
        }
        return s.charAt(0) == '<';
    }

    public static String truncate(String s, int length, boolean addDots) {
        if (s == null) {
            return EMPTY;
        }
        if (s.length() > length) {
            return addDots ? s.substring(0, length) + " ..." : s.substring(0, length);
        }
        return s;
    }

    public static String trimToEmpty(String s) {
        if (s == null) {
            return EMPTY;
        } else {
            return s.trim();
        }
    }

    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String temp = trimToEmpty(s);
        return EMPTY.equals(temp) ? null : temp;
    }

    public static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    public static String join(Object[] a, char delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            sb.append(a[i]);
            if (i != a.length - 1) {
                sb.append(delimiter);
            }
        }
        return sb.toString();
    }

    public static String join(Collection<String> c, String delimiter) {
        if (c == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        Iterator<String> iterator = c.iterator();
        while (iterator.hasNext()) {
            sb.append(iterator.next());
            if (iterator.hasNext()) {
                sb.append(delimiter);
            }
        }
        return sb.toString();
    }

    public static List<String> split(String s, char delimiter, boolean skipBackSlash) {
        int pos = s.indexOf(delimiter);
        if (pos == -1) {
            return Collections.singletonList(s);
        }
        List<String> list = new ArrayList<>();
        int startPos = 0;
        int searchPos = 0;
        while (pos != -1) {
            if (skipBackSlash && pos > 0 && s.charAt(pos - 1) == '\\') {
                s = s.substring(0, pos - 1) + s.substring(pos);
                searchPos = pos;
            } else {
                String temp = s.substring(startPos, pos);
                if (!EMPTY.equals(temp)) {
                    list.add(temp);
                }
                startPos = pos + 1;
                searchPos = startPos;
            }
            pos = s.indexOf(delimiter, searchPos);
        }
        if (startPos != s.length()) {
            String temp = s.substring(startPos);
            list.add(temp);
        }
        return list;
    }

    public static boolean isBlank(String s) {
        return trimToNull(s) == null;
    }

    public static String toIdString(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[\\s_\\\\/:<>\"\\|\\?\\*]", "-").toLowerCase();
    }

    public static Pair<String> splitByFirstLineFeed(String text) {
        String left = "";
        String right = "";
        if (text != null) {
            int pos = text.indexOf('\n');
            if (pos != -1) {
                left = text.substring(0, pos).trim();
                right = text.substring(pos).trim();
            } else {
                left = text.trim();
            }
        }
        return Pair.of(left, right);
    }

    public static List<String> toStringLines(String text) {
        return new BufferedReader(new StringReader(text)).lines().collect(Collectors.toList());
    }

    public static int countLineFeeds(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c == '\n') {
                count++;
            }
        }
        return count;
    }

    public static int wrappedLinesEstimate(String text, int colWidth) {
        List<String> lines = toStringLines(text);
        int estimate = 0;
        for (String s : lines) {
            int wrapEstimate = (int) Math.ceil((double) s.length() / colWidth);
            if (wrapEstimate == 0) {
                estimate++;
            } else {
                estimate += wrapEstimate;
            }
        }
        return estimate;
    }

    public static boolean containsIgnoreCase(List<String> list, String str) {
        for (String i : list) {
            if (i.equalsIgnoreCase(str)) {
                return true;
            }
        }
        return false;
    }

    public static <T> T getIgnoreKeyCase(Map<String, T> map, String name) {
        if (map == null || name == null) {
            return null;
        }
        for (Map.Entry<String, T> entry : map.entrySet()) {
            String key = entry.getKey();
            if (name.equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static void removeIgnoreKeyCase(Map<String, ?> map, String name) {
        if (map == null || name == null) {
            return;
        }
        for (String key : map.keySet()) {
            if (name.equalsIgnoreCase(key)) {
                map.remove(key);
                return;
            }
        }
    }

    public static Map<String, Object> simplify(Map<String, List<String>> map, boolean always) {
        if (map == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            if (values.size() > 1) {
                if (always) {
                    result.put(key, StringUtils.join(values, ","));
                } else {
                    result.put(key, values);
                }
            } else {
                Object value = values.getFirst();
                if (value != null) {
                    result.put(key, value + "");
                }
            }
        }
        return result;
    }

    public static String throwableToString(Throwable t) {
        try (final StringWriter sw = new StringWriter();
             final PrintWriter pw = new PrintWriter(sw, true)) {
            t.printStackTrace(pw);
            return sw.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static final Pattern CLI_ARG = Pattern.compile("'([^']*)'[^\\S]|\"([^\"]*)\"[^\\S]|(\\S+)");

    /**
     * Tokenizes a CLI command string into individual arguments.
     * Handles both single-quoted and double-quoted strings.
     *
     * @param command the command string to tokenize
     * @return array of arguments
     */
    public static String[] tokenizeCliCommand(String command) {
        List<String> args = new ArrayList<>();
        Matcher m = CLI_ARG.matcher(command + " ");
        while (m.find()) {
            if (m.group(1) != null) {
                args.add(m.group(1));
            } else if (m.group(2) != null) {
                args.add(m.group(2));
            } else {
                args.add(m.group(3));
            }
        }
        return args.toArray(new String[0]);
    }

    /**
     * Escapes a string for safe use as a shell argument.
     * Uses OS-appropriate escaping (single quotes for Unix, double quotes for Windows).
     *
     * @param value the string to escape
     * @return escaped string suitable for shell command
     */
    public static String shellEscape(String value) {
        if (OsUtils.isWindows()) {
            return shellEscapeWindows(value);
        } else {
            return shellEscapeUnix(value);
        }
    }

    /**
     * Escapes a string for Unix/Mac/Linux shell (bash, sh, zsh).
     * Uses single quotes and escapes embedded single quotes as '\''
     *
     * @param value the string to escape
     * @return escaped string wrapped in single quotes
     */
    public static String shellEscapeUnix(String value) {
        if (value == null) {
            return "''";
        }
        // For Unix shells, wrap in single quotes and escape any single quotes
        // Single quote escaping: end quote, literal escaped quote, start quote
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * Escapes a string for Windows cmd.exe.
     * Uses double quotes and escapes embedded double quotes and special chars.
     *
     * @param value the string to escape
     * @return escaped string wrapped in double quotes
     */
    public static String shellEscapeWindows(String value) {
        if (value == null) {
            return "\"\"";
        }
        // For Windows cmd.exe:
        // 1. Wrap in double quotes
        // 2. Escape double quotes as ""
        // 3. Escape special characters: ^, &, |, <, >, %, !
        String escaped = value
            .replace("\"", "\"\"")  // Escape double quotes
            .replace("^", "^^")      // Escape caret
            .replace("&", "^&")      // Escape ampersand
            .replace("|", "^|")      // Escape pipe
            .replace("<", "^<")      // Escape less-than
            .replace(">", "^>")      // Escape greater-than
            .replace("%", "%%");     // Escape percent
        return "\"" + escaped + "\"";
    }

    /**
     * Escapes a string for PowerShell.
     * Uses single quotes and escapes embedded single quotes by doubling them.
     *
     * @param value the string to escape
     * @return escaped string wrapped in single quotes
     */
    public static String shellEscapePowerShell(String value) {
        if (value == null) {
            return "''";
        }
        // For PowerShell:
        // Use single quotes (which are literal strings in PS)
        // Escape single quotes by doubling them: ' becomes ''
        return "'" + value.replace("'", "''") + "'";
    }

    /**
     * Escapes a string for the specified shell platform.
     *
     * @param value the string to escape
     * @param platform "sh" for Unix/Linux/macOS, "cmd" for Windows CMD, "ps" for PowerShell
     * @return escaped string suitable for the target platform
     */
    public static String shellEscapeForPlatform(String value, String platform) {
        if (platform == null) {
            platform = "sh";
        }
        return switch (platform) {
            case "cmd" -> shellEscapeWindows(value);
            case "ps" -> shellEscapePowerShell(value);
            default -> shellEscapeUnix(value); // "sh" or default
        };
    }

    /**
     * Returns the line continuation character for the specified shell platform.
     *
     * @param platform "sh" for Unix/Linux/macOS, "cmd" for Windows CMD, "ps" for PowerShell
     * @return line continuation string
     */
    public static String getLineContinuation(String platform) {
        if (platform == null) {
            platform = "sh";
        }
        return switch (platform) {
            case "cmd" -> " ^\n";
            case "ps" -> " `\n";
            default -> " \\\n"; // "sh" or default
        };
    }

    /**
     * Builds a complete shell command from command name and arguments.
     * Properly escapes each argument for the current OS.
     *
     * @param command the command name
     * @param args the arguments
     * @return complete shell command string
     */
    public static String buildShellCommand(String command, String... args) {
        StringBuilder sb = new StringBuilder();
        sb.append(command);
        for (String arg : args) {
            sb.append(' ');
            sb.append(shellEscape(arg));
        }
        return sb.toString();
    }

    /**
     * Builds a complete shell command from command name and arguments.
     * Properly escapes each argument for the current OS.
     *
     * @param command the command name
     * @param args the arguments
     * @return complete shell command string
     */
    public static String buildShellCommand(String command, List<String> args) {
        StringBuilder sb = new StringBuilder();
        sb.append(command);
        for (String arg : args) {
            sb.append(' ');
            sb.append(shellEscape(arg));
        }
        return sb.toString();
    }

    static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final int CHAR_LENGTH = CHARS.length();
    static SecureRandom RANDOM = new SecureRandom();

    public static String randomAlphaNumeric(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++)
            sb.append(CHARS.charAt(RANDOM.nextInt(CHAR_LENGTH)));
        return sb.toString();
    }

    public static String formatFileSize(int bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
    }

    public static String formatJson(Object o) {
        return formatJson(o, true, false, false);
    }

    public static String formatJson(Object o, boolean pretty, boolean lenient, boolean sort) {
        return formatJson(o, pretty, lenient, sort, "  ");
    }

    public static String formatJson(Object o, boolean pretty, boolean lenient, boolean sort, String indent) {
        if (o instanceof String ostring) {
            if (StringUtils.looksLikeJson(ostring)) {
                if (sort) { // dont care about order in first phase
                    o = JSONValue.parse(ostring);
                } else {
                    o = JSONValue.parseKeepingOrder(ostring);
                }
            } else {
                return ostring;
            }
        }
        if (o instanceof List || o instanceof Map) {
            StringBuilder sb = new StringBuilder();
            // Use IdentityHashMap for circular reference detection (identity, not equals)
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            formatRecurse(o, pretty, lenient, sort, indent, sb, 0, visited);
            return sb.toString();
        } else {
            return o + "";
        }
    }

    @SuppressWarnings("unchecked")
    private static void formatRecurse(Object o, boolean pretty, boolean lenient, boolean sort,
                                       String indent, StringBuilder sb, int depth, Set<Object> visited) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof JsFunction) {
            // JS functions cannot be serialized to JSON
            sb.append("null");
        } else if (o instanceof JsValue jv) {
            // Handles JsUndefined (returns null), JsDate, JsNumber, JsString, JsBoolean, JsUint8Array
            // Unwrap JS wrapper types (JsDate, JsNumber, JsString, JsBoolean) - check BEFORE Map/List
            // because these types extend JsObject which implements Map
            formatRecurse(jv.getJavaValue(), pretty, lenient, sort, indent, sb, depth, visited);
        } else if (o instanceof List<?> list) {
            // Check for circular reference
            if (visited.contains(o)) {
                sb.append("\"[Circular]\"");
                return;
            }
            visited.add(o);
            try {
                sb.append('[');
                if (pretty) {
                    sb.append('\n');
                }
                Iterator<?> iterator = list.iterator();
                while (iterator.hasNext()) {
                    Object child = iterator.next();
                    if (pretty) {
                        pad(sb, depth + 1, indent);
                    }
                    formatRecurse(child, pretty, lenient, sort, indent, sb, depth + 1, visited);
                    if (iterator.hasNext()) {
                        sb.append(',');
                    }
                    if (pretty) {
                        sb.append('\n');
                    }
                }
                if (pretty) {
                    pad(sb, depth, indent);
                }
                sb.append(']');
            } finally {
                visited.remove(o);
            }
        } else if (o instanceof ObjectLike ol) {
            // Check for circular reference
            if (visited.contains(o)) {
                sb.append("\"[Circular]\"");
                return;
            }
            visited.add(o);
            try {
                // Use toMap() to get raw entries (without auto-unwrap) for ObjectLike types
                // This preserves Terms.UNDEFINED so we can filter it out
                Map<Object, Object> map = (Map<Object, Object>) (Map<?, ?>) ol.toMap();
                if (sort) {
                    map = new TreeMap<>(map);
                }
                // Filter out undefined values and functions (JSON has no undefined/functions)
                List<Map.Entry<Object, Object>> entries = new ArrayList<>();
                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                    Object value = entry.getValue();
                    if (!(value == Terms.UNDEFINED) && !(value instanceof JsFunction)) {
                        entries.add(entry);
                    }
                }
                formatMap(entries, pretty, lenient, sort, indent, sb, depth, visited);
            } finally {
                visited.remove(o);
            }
        } else if (o instanceof Map) {
            // Check for circular reference
            if (visited.contains(o)) {
                sb.append("\"[Circular]\"");
                return;
            }
            visited.add(o);
            try {
                // found a rare case where key was a boolean (not string)
                Map<Object, Object> map = (Map<Object, Object>) o;
                if (sort) {
                    map = new TreeMap<>(map);
                }
                // Filter out undefined values and functions (JSON has no undefined/functions)
                List<Map.Entry<Object, Object>> entries = new ArrayList<>();
                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                    Object value = entry.getValue();
                    if (!(value == Terms.UNDEFINED) && !(value instanceof JsFunction)) {
                        entries.add(entry);
                    }
                }
                formatMap(entries, pretty, lenient, sort, indent, sb, depth, visited);
            } finally {
                visited.remove(o);
            }
        } else if (o instanceof java.util.Date date) {
            // Format Date as ISO 8601 string with milliseconds (matches JavaScript's Date.toISOString())
            java.time.Instant instant = date.toInstant();
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(java.time.ZoneOffset.UTC);
            String isoDate = formatter.format(instant);
            sb.append('"').append(isoDate).append('"');
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else {
            String value = o.toString();
            if (lenient) {
                sb.append('\'').append(escapeJsonValue(value)).append('\'');
            } else {
                sb.append('"').append(escapeJsonValue(value)).append('"');
            }
        }
    }

    private static void formatMap(List<Map.Entry<Object, Object>> entries, boolean pretty, boolean lenient,
                                   boolean sort, String indent, StringBuilder sb, int depth, Set<Object> visited) {
        sb.append('{');
        if (pretty) {
            sb.append('\n');
        }
        Iterator<Map.Entry<Object, Object>> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Object> entry = iterator.next();
            Object key = entry.getKey();
            if (pretty) {
                pad(sb, depth + 1, indent);
            }
            if (lenient) {
                String escaped = escapeJsonValue(key == null ? null : key.toString());
                if (escaped != null && isValidJsonKey(escaped)) {
                    sb.append(key);
                } else {
                    sb.append('\'').append(escaped).append('\'');
                }
            } else {
                sb.append('"').append(escapeJsonValue(key == null ? null : key.toString())).append('"');
            }
            sb.append(':');
            if (pretty) {
                sb.append(' ');
            }
            formatRecurse(entry.getValue(), pretty, lenient, sort, indent, sb, depth + 1, visited);
            if (iterator.hasNext()) {
                sb.append(',');
            }
            if (pretty) {
                sb.append('\n');
            }
        }
        if (pretty) {
            pad(sb, depth, indent);
        }
        sb.append('}');
    }

    private static void pad(StringBuilder sb, int depth, String indent) {
        sb.append(indent.repeat(depth));
    }

    private static final Pattern JS_IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*$");

    private static boolean isValidJsonKey(String key) {
        return JS_IDENTIFIER_PATTERN.matcher(key).matches();
    }

    private static String escapeJsonValue(String raw) {
        return JSONValue.escape(raw, JSONStyle.LT_COMPRESS);
    }

}
