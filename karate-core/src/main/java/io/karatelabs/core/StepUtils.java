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
     * Parsed result of a feature path with optional tag selector or line filter.
     * Used by both StepExecutor (call keyword) and ScenarioRuntime (callSingle).
     * {@code lineFilters} is non-null only for {@code file.feature:N(:N)*} form.
     */
    public record ParsedFeaturePath(String path, String tagSelector, boolean sameFile,
                                    java.util.Set<Integer> lineFilters) {}

    /**
     * Parse a feature path into path and tag/line components.
     * Supports:
     * - file.feature@tag - call specific scenario by tag
     * - file.feature:10 / file.feature:10:25 - call by line number(s); matches
     *   Runner.path(...) suite-side syntax. Tag and line are mutually exclusive
     *   on the same path; tag suffix wins by parse order.
     * - @tag - call scenario in same file by tag
     * - file.feature - normal call without tag
     *
     * @param rawPath the raw path string (may contain @tag or :N suffix)
     * @return ParsedFeaturePath with separated path and selector components
     */
    public static ParsedFeaturePath parseFeaturePath(String rawPath) {
        if (rawPath.startsWith("@")) {
            // Same-file tag call: @tagname
            return new ParsedFeaturePath(null, rawPath, true, null);
        }
        // Check for tag suffix: file.feature@tag
        int tagPos = rawPath.indexOf(".feature@");
        if (tagPos != -1) {
            String path = rawPath.substring(0, tagPos + 8);  // "file.feature"
            String tag = "@" + rawPath.substring(tagPos + 9);  // "@tag"
            return new ParsedFeaturePath(path, tag, false, null);
        }
        // Check for line-number suffix: file.feature:10 or file.feature:10:25
        // Mirrors Runner.resolveFeatures parsing for consistency.
        int linePos = rawPath.indexOf(".feature:");
        if (linePos != -1) {
            String path = rawPath.substring(0, linePos + 8);  // "file.feature"
            String remainder = rawPath.substring(linePos + 9);
            if (remainder.matches("\\d+(:\\d+)*")) {
                java.util.Set<Integer> lines = new java.util.LinkedHashSet<>();
                for (String part : remainder.split(":")) {
                    lines.add(Integer.parseInt(part));
                }
                return new ParsedFeaturePath(path, null, false, lines);
            }
            // Not numeric — fall through and treat as plain path (preserves
            // edge-case behavior for paths that happen to contain ".feature:").
        }
        // No selector - normal call
        return new ParsedFeaturePath(rawPath, null, false, null);
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
     * Find the index of the `)` that matches the leading `read(` in {@code text},
     * skipping anything inside single- or double-quoted strings and tracking
     * nested parens. Returns -1 if {@code text} does not start with {@code read(}
     * or no matching paren is found. Used to safely split {@code read(path) arg}
     * even when the path contains characters like {@code )} inside quotes.
     */
    public static int findReadCloseParen(String text) {
        if (!text.startsWith("read(")) return -1;
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
                if (parenDepth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * The callable target and (optional) argument expression of a call step, e.g.
     * {@code call read('x.feature') {...}} splits into {@code read('x.feature')} and
     * {@code {...}}. {@code arg} is null when the step carries no argument.
     */
    public record CallParts(String callable, String arg) {}

    /**
     * Split a call expression into its callable target and argument, mirroring v1's
     * {@code parseCallArgs}. For a {@code read(...)} expression the split is at read()'s
     * close paren, so the argument may be either space-separated ({@code read('x') arg})
     * or directly appended ({@code read('x.js')(arg)} — the V1 immediately-invoke form).
     * Otherwise the split is at the first space.
     */
    public static CallParts splitCallExpr(String text) {
        int splitAfter; // index of the last char belonging to the callable target
        if (text.startsWith("read(")) {
            int closeParen = findReadCloseParen(text);
            if (closeParen < 0) {
                return new CallParts(text, null);
            }
            splitAfter = closeParen;
        } else {
            int space = text.indexOf(' ');
            if (space < 0) {
                return new CallParts(text, null);
            }
            // the space itself is a separator, not part of either side
            splitAfter = space - 1;
        }
        String callable = text.substring(0, splitAfter + 1).trim();
        String arg = text.substring(splitAfter + 1).trim();
        return new CallParts(callable, arg.isEmpty() ? null : arg);
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

    /**
     * True when an LHS path uses a JsonPath construct that JS can't evaluate:
     * {@code [*]} (array wildcard), {@code ..} (recursive descent), or {@code [?(...)]} (filter).
     * Used by `set` / `remove` to fall back to Jayway when a step like
     * {@code set body[*].parent = 'test'} would otherwise hit the JS parser.
     */
    public static boolean containsJsonPathWildcard(String s) {
        return s.contains("[*]") || s.contains("[?") || s.contains("..");
    }

    /**
     * True if {@code s} is a bare identifier — letter/_/$ followed by letters/digits/_/$.
     * Used by `set` to distinguish full-replacement (`set foo = ...`) from path/JS forms
     * (`set foo.b = ...`, `set arr[0] = ...`).
     */
    public static boolean isPlainIdentifier(String s) {
        if (s == null) return false;
        s = s.trim();
        int n = s.length();
        if (n == 0) return false;
        char c0 = s.charAt(0);
        if (!(Character.isLetter(c0) || c0 == '_' || c0 == '$')) return false;
        for (int i = 1; i < n; i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '$')) return false;
        }
        return true;
    }

    /**
     * True if {@code lhs} is a "pure" JSON path expression: an identifier followed by
     * zero or more path segments where every segment is one of
     * {@code .ident}, {@code [int]}, {@code [int:int]}, {@code ['str']}, {@code ["str"]},
     * {@code [*]}, {@code ..}, {@code [?(...)]}. JS-style dynamic indices (e.g. {@code [i]},
     * {@code [i+1]}) deliberately fail this check so they continue to route through the
     * JS engine. Used by `set` / `remove` to route LHS paths through Jayway, which mirrors
     * v1 semantics — most importantly, auto-vivifying intermediate objects when the
     * target path doesn't yet exist.
     */
    public static boolean isPureJsonPath(String lhs) {
        if (lhs == null) return false;
        String s = lhs.trim();
        int n = s.length();
        if (n == 0) return false;
        // leading identifier
        char c0 = s.charAt(0);
        if (!(Character.isLetter(c0) || c0 == '_' || c0 == '$')) return false;
        int i = 1;
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') i++;
            else break;
        }
        boolean hasSegment = false;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '.') {
                hasSegment = true;
                i++;
                if (i < n && s.charAt(i) == '.') { // recursive descent: `..`
                    i++;
                    if (i >= n) return false;
                    char next = s.charAt(i);
                    if (next == '[') continue; // `..[*]` etc. — let the next loop iter parse it
                    if (!(Character.isLetter(next) || next == '_' || next == '$')) return false;
                }
                if (i >= n) return false;
                char id = s.charAt(i);
                if (!(Character.isLetter(id) || id == '_' || id == '$')) return false;
                i++;
                // Allow '-' inside a dot segment so a hyphenated JSON key like
                // `obj.hyphen-key` routes through Jayway instead of the JS engine,
                // which would read it as subtraction (`obj.hyphen - key`) and throw
                // "MATH_ADD_EXPR is not a valid assignment target" (issue #2896).
                // The segment must still start with an identifier char (above), so a
                // bare `a - b` with spaces is unaffected — the space breaks the scan.
                while (i < n) {
                    char cc = s.charAt(i);
                    if (Character.isLetterOrDigit(cc) || cc == '_' || cc == '$' || cc == '-') i++;
                    else break;
                }
            } else if (c == '[') {
                hasSegment = true;
                i++;
                if (i >= n) return false;
                char inner = s.charAt(i);
                if (inner == '\'' || inner == '"') {
                    char quote = inner;
                    i++;
                    while (i < n && s.charAt(i) != quote) {
                        if (s.charAt(i) == '\\' && i + 1 < n) i++; // skip escape
                        i++;
                    }
                    if (i >= n) return false;
                    i++; // consume closing quote
                } else if (inner == '*') {
                    i++;
                } else if (inner == '?') {
                    int depth = 1;
                    i++;
                    while (i < n && depth > 0) {
                        char cc = s.charAt(i);
                        if (cc == '[') depth++;
                        else if (cc == ']') {
                            depth--;
                            if (depth == 0) break;
                        }
                        i++;
                    }
                    if (i >= n) return false;
                } else if (inner == '-' || Character.isDigit(inner)) {
                    if (inner == '-') i++;
                    while (i < n && Character.isDigit(s.charAt(i))) i++;
                    if (i < n && s.charAt(i) == ':') {
                        i++;
                        if (i < n && s.charAt(i) == '-') i++;
                        while (i < n && Character.isDigit(s.charAt(i))) i++;
                    }
                } else {
                    return false; // [ident], [expr], ... — JS dynamic, not pure JsonPath
                }
                if (i >= n || s.charAt(i) != ']') return false;
                i++;
            } else {
                return false;
            }
        }
        return hasSegment;
    }

    public static final class VarAndPath {
        public final String var;
        public final String path;

        VarAndPath(String var, String path) {
            this.var = var;
            this.path = path;
        }
    }

    /**
     * Split an LHS like {@code body[*].parent} into variable {@code body} and JsonPath
     * {@code $[*].parent}. Mirrors v1's {@code parseVariableAndPath}: variable is the leading
     * identifier ({@code [A-Za-z_$][A-Za-z0-9_$]*}); the rest becomes the path, prefixed
     * with {@code $} unless it already starts with one.
     */
    public static VarAndPath splitVarAndJsonPath(String lhs) {
        lhs = lhs.trim();
        int i = 0;
        int n = lhs.length();
        if (n == 0) {
            return new VarAndPath("", "$");
        }
        char first = lhs.charAt(0);
        if (!(Character.isLetter(first) || first == '_' || first == '$')) {
            return new VarAndPath("", "$");
        }
        i = 1;
        while (i < n) {
            char c = lhs.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                i++;
            } else {
                break;
            }
        }
        String var = lhs.substring(0, i);
        String rest = lhs.substring(i).trim();
        String path;
        if (rest.isEmpty()) {
            path = "$";
        } else if (rest.charAt(0) == '$') {
            path = rest;
        } else if (rest.charAt(0) == '.' || rest.charAt(0) == '[') {
            path = "$" + rest;
        } else {
            path = "$." + rest;
        }
        return new VarAndPath(var, path);
    }

}
