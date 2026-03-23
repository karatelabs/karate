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
 * JavaScript RegExp wrapper that provides RegExp prototype methods.
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
                throw new RuntimeException("Invalid RegExp literal: " + literalText);
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
            throw new RuntimeException("invalid regex: " + pattern + " - " + e.getMessage());
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
            throw new RuntimeException("invalid regex: " + pattern + " - " + e.getMessage());
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
        while (matcher.find()) {
            matcher.appendReplacement(result, replacement);
            lastIndex = matcher.end();
            if (!global) {
                break;
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public List<String> match(String str) {
        Matcher matcher = javaPattern.matcher(str);
        List<String> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(matcher.group(0));
        }
        // ideally should return JsArray with extra properties
        return matches;
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
        // add capture groups
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String group = matcher.group(i);
            matches.add(group != null ? group : ""); // js returns "" for undefined groups, not null
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

    @Override
    public Object getMember(String name) {
        // Check own properties first
        Object own = super.getMember(name);
        if (own != null) {
            return own;
        }
        // Regex instance properties
        return switch (name) {
            case "source" -> pattern;
            case "flags" -> flags;
            case "lastIndex" -> lastIndex;
            case "global" -> global;
            case "ignoreCase" -> flags.contains("i");
            case "multiline" -> flags.contains("m");
            case "dotAll" -> flags.contains("s");
            default -> null;
        };
    }

    @Override
    public Object call(Context context, Object[] args) {
        if (args.length == 0) {
            return new JsRegex(); // empty regex in JS
        }
        String patternStr = args[0].toString();
        String flagsStr = args.length > 1 ? args[1].toString() : "";
        return new JsRegex(patternStr, flagsStr);
    }

}
