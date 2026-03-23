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
package io.karatelabs.core;

import io.karatelabs.common.Json;
import io.karatelabs.common.Xml;
import io.karatelabs.js.JavaCallable;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static utility methods for step execution.
 */
public class StepUtils {

    private StepUtils() {
        // Utility class
    }

    /**
     * Parsed result of a feature path with optional tag selector.
     * Used by both StepExecutor (call keyword) and ScenarioRuntime (callSingle).
     */
    public record ParsedFeaturePath(String path, String tagSelector, boolean sameFile) {}

    /**
     * Parse a feature path into path and tag selector components.
     * Supports:
     * - file.feature@tag - call specific scenario by tag
     * - @tag - call scenario in same file by tag
     * - file.feature - normal call without tag
     *
     * @param rawPath the raw path string (may contain @tag suffix)
     * @return ParsedFeaturePath with separated path and tag components
     */
    public static ParsedFeaturePath parseFeaturePath(String rawPath) {
        if (rawPath.startsWith("@")) {
            // Same-file tag call: @tagname
            return new ParsedFeaturePath(null, rawPath, true);
        }
        // Check for tag suffix: file.feature@tag
        int tagPos = rawPath.indexOf(".feature@");
        if (tagPos != -1) {
            String path = rawPath.substring(0, tagPos + 8);  // "file.feature"
            String tag = "@" + rawPath.substring(tagPos + 9);  // "@tag"
            return new ParsedFeaturePath(path, tag, false);
        }
        // No tag - normal call
        return new ParsedFeaturePath(rawPath, null, false);
    }

    /**
     * Check if a string is a valid Karate variable name.
     * Must start with letter or underscore, followed by letters, digits, or underscores.
     */
    public static boolean isValidVariableName(String name) {
        return name != null && !name.isEmpty()
                && name.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }

    /**
     * Find the separator between call target and arguments in a call expression.
     * Handles quoted strings in read() calls, e.g., read('foo bar.js') arg
     *
     * @return index of separator space, or -1 if not found
     */
    public static int findCallArgSeparator(String text) {
        if (text.startsWith("read(")) {
            int parenDepth = 0;
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                } else if (c == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                }
                if (inSingleQuote || inDoubleQuote) continue;
                if (c == '(') parenDepth++;
                else if (c == ')') {
                    parenDepth--;
                    if (parenDepth == 0) {
                        return text.indexOf(' ', i + 1);
                    }
                }
            }
            return -1;
        }
        return text.indexOf(' ');
    }

    /**
     * Check if text is a literal feature file read like read('file.feature').
     */
    public static boolean isLiteralFeatureRead(String text) {
        if (!text.startsWith("read(")) return false;
        int openParen = text.indexOf('(');
        if (openParen == -1) return false;
        int nextChar = openParen + 1;
        if (nextChar >= text.length()) return false;
        char c = text.charAt(nextChar);
        if (c != '\'' && c != '"') return false;
        int end = text.indexOf(c, nextChar + 1);
        if (end == -1) return false;
        String path = text.substring(nextChar + 1, end);
        return path.endsWith(".feature");
    }

    /**
     * Deep copy a value (Map, List, or primitive).
     */
    @SuppressWarnings("unchecked")
    public static Object deepCopy(Object value) {
        if (value == null) {
            return null;
        }
        // JsCallable functions shouldn't be deep-copied - return them unchanged
        if (value instanceof JavaCallable) {
            return value;
        }
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                copy.put(entry.getKey(), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return value;
    }

    /**
     * Navigate to or create a nested path in a map structure.
     * Creates intermediate maps as needed.
     */
    @SuppressWarnings("unchecked")
    public static Object getOrCreatePath(Map<String, Object> target, String path) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (String part : parts) {
            Object next = current.get(part);
            if (next == null) {
                next = new LinkedHashMap<>();
                current.put(part, next);
            }
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                return next;
            }
        }
        return current;
    }

    /**
     * Set a value at a nested path, handling both dot notation and array brackets.
     */
    @SuppressWarnings("unchecked")
    public static void setValueAtPath(Object target, String path, Object value) {
        if (target instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) target;
            if (path.contains("[")) {
                int bracketIdx = path.indexOf('[');
                String key = path.substring(0, bracketIdx);
                int closeIdx = path.indexOf(']');
                int arrayIdx = Integer.parseInt(path.substring(bracketIdx + 1, closeIdx));
                String remainder = closeIdx + 1 < path.length() ? path.substring(closeIdx + 1) : "";

                Object arr = map.get(key);
                if (arr == null) {
                    arr = new ArrayList<>();
                    map.put(key, arr);
                }
                if (arr instanceof List) {
                    List<Object> list = (List<Object>) arr;
                    while (list.size() <= arrayIdx) {
                        list.add(remainder.isEmpty() ? null : new LinkedHashMap<>());
                    }
                    if (remainder.isEmpty()) {
                        list.set(arrayIdx, value);
                    } else {
                        String nextPath = remainder.startsWith(".") ? remainder.substring(1) : remainder;
                        setValueAtPath(list.get(arrayIdx), nextPath, value);
                    }
                }
            } else if (path.contains(".")) {
                int dotIdx = path.indexOf('.');
                String key = path.substring(0, dotIdx);
                String remainder = path.substring(dotIdx + 1);
                Object next = map.get(key);
                if (next == null) {
                    next = new LinkedHashMap<>();
                    map.put(key, next);
                }
                setValueAtPath(next, remainder, value);
            } else {
                map.put(path, value);
            }
        }
    }

    /**
     * Remove a value at a nested path in a map.
     */
    @SuppressWarnings("unchecked")
    public static void removeAtPath(Map<String, Object> map, String path) {
        if (path.startsWith("[")) {
            int closeIdx = path.indexOf(']');
            if (closeIdx > 0) {
                String key = path.substring(1, closeIdx);
                if ((key.startsWith("'") && key.endsWith("'")) ||
                    (key.startsWith("\"") && key.endsWith("\""))) {
                    key = key.substring(1, key.length() - 1);
                }
                String remainder = closeIdx + 1 < path.length() ? path.substring(closeIdx + 1) : "";
                if (remainder.isEmpty()) {
                    map.remove(key);
                } else {
                    Object nested = map.get(key);
                    if (nested instanceof Map) {
                        String nextPath = remainder.startsWith(".") ? remainder.substring(1) : remainder;
                        removeAtPath((Map<String, Object>) nested, nextPath);
                    }
                }
            }
        } else if (path.contains(".")) {
            int dotIdx = path.indexOf('.');
            String key = path.substring(0, dotIdx);
            String remainder = path.substring(dotIdx + 1);
            Object nested = map.get(key);
            if (nested instanceof Map) {
                removeAtPath((Map<String, Object>) nested, remainder);
            }
        } else {
            map.remove(path);
        }
    }

    /**
     * Find the first '=' that's an assignment operator (not ==, !=, <=, >=).
     *
     * @param text the text to search
     * @return index of '=' or -1 if not found
     */
    public static int findAssignmentOperator(String text) {
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '=' && i > 0) {
                char prev = text.charAt(i - 1);
                if (prev != '=' && prev != '!' && prev != '<' && prev != '>') {
                    // Check not followed by '='
                    if (i + 1 >= text.length() || text.charAt(i + 1) != '=') {
                        return i;
                    }
                }
            }
            i++;
        }
        return -1;
    }

    /**
     * Check if string contains JS expression punctuation like . ( [ ] ' "
     *
     * @param s the string to check
     * @return true if contains punctuation
     */
    public static boolean hasPunctuation(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.' || c == '(' || c == ')' || c == '[' || c == ']' || c == '\'' || c == '"') {
                return true;
            }
        }
        return false;
    }

    /**
     * Convert a value to its string representation.
     * - null -> "null"
     * - String -> as-is
     * - Map/List -> JSON
     * - Node -> XML
     * - Other -> toString()
     *
     * @param value the value to stringify
     * @return string representation
     */
    public static String stringify(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return (String) value;
        } else if (value instanceof Map || value instanceof List) {
            return Json.stringifyStrict(value);
        } else if (value instanceof Node) {
            return Xml.toString((Node) value, false);
        } else {
            return value.toString();
        }
    }

}
