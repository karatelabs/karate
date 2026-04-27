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
package io.karatelabs.js;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Instance type for RegExp values — produced by {@code /foo/} literals,
 * {@code new RegExp(pattern, flags)}, and the {@code RegExp(...)} call form.
 * The global {@code RegExp} constructor lives on {@link JsRegexConstructor}.
 */
public class JsRegex extends JsObject {

    public final String pattern;
    public final String flags;
    public final Pattern javaPattern;
    public final boolean global;

    private int lastIndex = 0;

    JsRegex() {
        this("(?:)");
    }

    JsRegex(String literalText) {
        super(null, JsRegexPrototype.INSTANCE);
        if (literalText.startsWith("/")) {
            int lastSlashIndex = literalText.lastIndexOf('/');
            if (lastSlashIndex <= 0) {
                throw JsErrorException.syntaxError("Invalid RegExp literal: " + literalText);
            }
            // extract pattern and flags from the literal
            this.pattern = literalText.substring(1, lastSlashIndex);
            this.flags = lastSlashIndex < literalText.length() - 1
                    ? literalText.substring(lastSlashIndex + 1)
                    : "";
        } else {
            // string patterns without delimiters
            this.pattern = literalText;
            this.flags = "";
        }
        this.global = this.flags.contains("g");
        int javaFlags = translateJsFlags(this.flags);
        try {
            // unescape js-specific regex syntax that differs from Java
            String javaPattern = translateJsRegexToJava(this.pattern);
            this.javaPattern = Pattern.compile(javaPattern, javaFlags);
        } catch (PatternSyntaxException e) {
            throw JsErrorException.syntaxError("Invalid regular expression: /" + pattern + "/ - " + e.getMessage());
        }
    }

    JsRegex(String pattern, String flags) {
        super(null, JsRegexPrototype.INSTANCE);
        this.pattern = pattern;
        this.flags = flags != null ? flags : "";
        this.global = this.flags.contains("g");
        int javaFlags = translateJsFlags(this.flags);
        try {
            String javaPattern = translateJsRegexToJava(this.pattern);
            this.javaPattern = Pattern.compile(javaPattern, javaFlags);
        } catch (PatternSyntaxException e) {
            throw JsErrorException.syntaxError("Invalid regular expression: /" + pattern + "/ - " + e.getMessage());
        }
    }

    private static String translateJsRegexToJava(String jsPattern) {
        // handle any JavaScript regex syntax that needs special handling in Java
        // examples might include certain escape sequences or character classes
        return jsPattern;
    }

    private static int translateJsFlags(String flags) {
        int javaFlags = 0;
        if (flags.contains("i")) {
            javaFlags |= Pattern.CASE_INSENSITIVE;
        }
        if (flags.contains("m")) {
            javaFlags |= Pattern.MULTILINE;
        }
        // The "s" flag (dotall) makes "." match newline characters
        if (flags.contains("s")) {
            javaFlags |= Pattern.DOTALL;
        }
        return javaFlags;
    }

    public boolean test(String str) {
        if (global) {
            // for global, start at lastIndex
            Matcher matcher = javaPattern.matcher(str);
            boolean found = matcher.find(lastIndex);
            if (found) {
                lastIndex = matcher.end();
            } else {
                lastIndex = 0;
            }
            return found;
        } else {
            // non-global regex does not update lastIndex
            return javaPattern.matcher(str).find();
        }
    }

    public String replace(String str, String replacement) {
        Matcher matcher = javaPattern.matcher(str);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(str, last, matcher.start());
            // Spec §22.1.3.18 GetSubstitution form — NOT Java's
            // Matcher.appendReplacement form. JS treats $1..$99 / $& / $` /
            // $' / $<name> / $$ specially; an unrecognized `$X` is the
            // literal two chars. Java's appendReplacement throws
            // "Illegal group reference" for the same input. Do the walk
            // explicitly so Java semantics never leak through.
            appendSubstitution(result, replacement, matcher, str);
            last = matcher.end();
            lastIndex = last;
            if (!global) {
                break;
            }
        }
        result.append(str, last, str.length());
        return result.toString();
    }

    // Spec §22.1.3.18 GetSubstitution. {@code matched}/{@code position}/
    // {@code string} are derived from the matcher; {@code captures} are
    // groups 1..N. Walks the replacement template and emits literal text
    // mixed with substitution slices.
    private static void appendSubstitution(StringBuilder out, String tmpl,
                                            Matcher matcher, String input) {
        int n = tmpl.length();
        for (int i = 0; i < n; i++) {
            char c = tmpl.charAt(i);
            if (c != '$' || i + 1 >= n) {
                out.append(c);
                continue;
            }
            char next = tmpl.charAt(i + 1);
            switch (next) {
                case '$' -> { out.append('$'); i++; }
                case '&' -> { out.append(matcher.group()); i++; }
                case '`' -> { out.append(input, 0, matcher.start()); i++; }
                case '\'' -> { out.append(input, matcher.end(), input.length()); i++; }
                case '<' -> {
                    int close = tmpl.indexOf('>', i + 2);
                    if (close < 0) { out.append(c); continue; }
                    String name = tmpl.substring(i + 2, close);
                    String g;
                    try {
                        g = matcher.group(name);
                    } catch (IllegalArgumentException | IllegalStateException ex) {
                        // No such named group — spec says emit empty string.
                        g = "";
                    }
                    if (g != null) out.append(g);
                    i = close;
                }
                default -> {
                    if (next < '0' || next > '9') {
                        out.append(c);
                        continue;
                    }
                    int total = matcher.groupCount();
                    int idx = next - '0';
                    int consumed = 1;
                    // Try a two-digit reference if (a) the second char is a
                    // digit AND (b) the resulting index is a valid group.
                    // Otherwise fall back to the single-digit form. Spec
                    // §22.1.3.18 step 11.
                    if (i + 2 < n) {
                        char nn = tmpl.charAt(i + 2);
                        if (nn >= '0' && nn <= '9') {
                            int two = idx * 10 + (nn - '0');
                            if (two >= 1 && two <= total) {
                                idx = two;
                                consumed = 2;
                            }
                        }
                    }
                    if (idx == 0 || idx > total) {
                        // $0 or out-of-range — JS leaves as literal.
                        out.append(c);
                        continue;
                    }
                    String g = matcher.group(idx);
                    if (g != null) out.append(g);
                    i += consumed;
                }
            }
        }
    }

    // Spec §22.2.5.7 String.prototype.match dispatch:
    //  - global: array of every match (group(0) per match), length-only;
    //    null when no match.
    //  - non-global: same shape as exec — array of [match, ...captures] with
    //    {@code index} and {@code input} attached as named properties.
    public Object match(String str) {
        Matcher matcher = javaPattern.matcher(str);
        if (global) {
            List<Object> matches = new ArrayList<>();
            while (matcher.find()) {
                matches.add(matcher.group(0));
            }
            if (matches.isEmpty()) return null;
            return new JsArray(matches);
        }
        return exec(str);
    }

    public int search(String str) {
        Matcher matcher = javaPattern.matcher(str);
        if (matcher.find()) {
            return matcher.start();
        }
        return -1;
    }

    JsArray exec(String str) {
        Matcher matcher = javaPattern.matcher(str);
        boolean found;
        if (global) {
            // for global, start at lastIndex
            found = matcher.find(lastIndex);
            if (found) {
                lastIndex = matcher.end();
            } else {
                lastIndex = 0;
                return null;
            }
        } else {
            // non-global regex always starts from beginning
            found = matcher.find();
        }
        if (!found) {
            return null;
        }
        // create result array with match and capture groups
        List<Object> matches = new ArrayList<>();
        matches.add(matcher.group(0)); // Full match
        // Spec §22.2.5.2.2: a capture group that didn't participate in the
        // match resolves to {@code undefined}, not the empty string.
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String group = matcher.group(i);
            matches.add(group != null ? group : Terms.UNDEFINED);
        }
        // Create array with index and input properties
        JsArray result = new JsArray(matches);
        result.putMember("index", matcher.start());
        result.putMember("input", str);
        return result;
    }

    @Override
    public String toString() {
        return "/" + pattern + "/" + flags;
    }

    // Spec §22.2.6: source / flags / global / ignoreCase / multiline /
    // dotAll / sticky / unicode are accessor getters on RegExp.prototype,
    // NOT own properties of instances. Only {@code lastIndex} is an own
    // data property of the instance per spec §22.2.7.1
    // ({@code [[Writable]]: true, [[Enumerable]]: false,
    // [[Configurable]]: false}).
    @Override
    protected Object resolveOwnIntrinsic(String name) {
        return "lastIndex".equals(name) ? lastIndex : null;
    }

    // Spec §22.2.7.1: {@code lastIndex} is a writable own data property.
    // Without this override, {@code re.lastIndex = 12} would land in
    // {@link JsObject#props} (a fresh DataSlot) while {@link #exec}
    // continues to read the {@code lastIndex} field — so global-flag exec
    // ignores user-set positions. Route through {@link Terms#objectToNumber}
    // to coerce non-Number assignments per spec ToInteger semantics.
    @Override
    public void putMember(String name, Object value) {
        if ("lastIndex".equals(name)) {
            double d = Terms.objectToNumber(value).doubleValue();
            if (Double.isNaN(d)) {
                this.lastIndex = 0;
            } else if (d > Integer.MAX_VALUE) {
                this.lastIndex = Integer.MAX_VALUE;
            } else if (d < Integer.MIN_VALUE) {
                this.lastIndex = Integer.MIN_VALUE;
            } else {
                this.lastIndex = (int) d;
            }
            return;
        }
        super.putMember(name, value);
    }

    private static final List<String> INTRINSIC_NAMES = List.of("lastIndex");

    @Override
    protected Iterable<String> ownIntrinsicNames() {
        return INTRINSIC_NAMES;
    }

}
