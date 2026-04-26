/*
 * The MIT License
 *
 * Copyright 2024 Karate Labs Inc.
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Singleton prototype for String instances. Methods wrapped via
 * {@link Prototype#method(String, int, JsCallable)} for spec
 * {@code length}+{@code name}; the base class caches wrapped instances
 * per-Engine.
 */
class JsStringPrototype extends Prototype {

    static final JsStringPrototype INSTANCE = new JsStringPrototype();

    private JsStringPrototype() {
        super(JsObjectPrototype.INSTANCE);
        install("indexOf", 1, this::indexOf);
        install("startsWith", 1, this::startsWith);
        install("getBytes", 0, this::getBytes);
        install("split", 2, this::split);
        install("charAt", 1, this::charAt);
        install("charCodeAt", 1, this::charCodeAt);
        install("codePointAt", 1, this::codePointAt);
        install("concat", 1, this::concat);
        install("endsWith", 1, this::endsWith);
        install("includes", 1, this::includes);
        install("lastIndexOf", 1, this::lastIndexOf);
        install("padEnd", 1, this::padEnd);
        install("padStart", 1, this::padStart);
        install("repeat", 1, this::repeat);
        install("slice", 2, this::slice);
        install("substring", 2, this::substring);
        install("toLowerCase", 0, this::toLowerCase);
        install("toUpperCase", 0, this::toUpperCase);
        install("trim", 0, this::trim);
        // trimLeft / trimRight are spec aliases of trimStart / trimEnd, but
        // each must report its own name (see name.js tests).
        install("trimStart", 0, this::trimStart);
        install("trimLeft", 0, this::trimStart);
        install("trimEnd", 0, this::trimEnd);
        install("trimRight", 0, this::trimEnd);
        install("replace", 2, this::replace);
        install("replaceAll", 2, this::replaceAll);
        install("match", 1, this::match);
        install("matchAll", 1, this::matchAll);
        install("search", 1, this::search);
        install("valueOf", 0, this::valueOf);
        install("toLocaleLowerCase", 0, this::toLowerCase);
        install("toLocaleUpperCase", 0, this::toUpperCase);
        install("at", 1, this::at);
        install("toString", 0, this::valueOf);
        install("normalize", 0, this::normalize);
        install("localeCompare", 1, this::localeCompare);
    }

    // Helper method to get string from this context
    private static String asString(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsString js) {
            return js.text;
        }
        if (thisObj instanceof String s) {
            return s;
        }
        return thisObj != null ? thisObj.toString() : "";
    }

    // Instance methods

    private Object indexOf(Context context, Object[] args) {
        String s = asString(context);
        if (args.length > 1) {
            return s.indexOf((String) args[0], ((Number) args[1]).intValue());
        }
        return s.indexOf((String) args[0]);
    }

    private Object startsWith(Context context, Object[] args) {
        String s = asString(context);
        if (args.length > 1) {
            return s.startsWith((String) args[0], ((Number) args[1]).intValue());
        }
        return s.startsWith((String) args[0]);
    }

    private Object getBytes(Context context, Object[] args) {
        String s = asString(context);
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private Object split(Context context, Object[] args) {
        String s = asString(context);
        // JS semantics:
        //   str.split() / str.split(undefined) → [str]
        //   str.split('') → each char as element
        //   str.split(sep) → literal match on sep (NOT a regex)
        //   str.split(/re/) → regex match
        //   limit (arg[1]) if provided truncates the result
        // Previously we passed args[0] straight to Java's String.split, which
        // interprets it as a regex — causing '|a|b|'.split('|') to blow up (| is
        // alternation) and land as "Index out of bounds" further down the pipeline.
        if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) {
            // Mutable so the caller can pop/push/splice — Arrays.asList is fixed-size
            List<String> single = new ArrayList<>(1);
            single.add(s);
            return single;
        }
        int limit = -1; // JS default: keep all, including trailing empty strings
        if (args.length > 1 && args[1] instanceof Number n) {
            limit = n.intValue();
            if (limit <= 0) return new ArrayList<String>();
        }
        String[] parts;
        if (args[0] instanceof JsRegex regex) {
            parts = regex.javaPattern.split(s, -1);
        } else {
            String sep = args[0].toString();
            if (sep.isEmpty()) {
                int capped = limit < 0 ? s.length() : Math.min(limit, s.length());
                List<String> result = new ArrayList<>(capped);
                for (int i = 0; i < capped; i++) {
                    result.add(String.valueOf(s.charAt(i)));
                }
                return result;
            }
            // -1 to keep trailing empty segments (JS-compatible, Java default drops them)
            parts = s.split(Pattern.quote(sep), -1);
        }
        // Wrap in a mutable ArrayList — downstream .pop/.push/.splice mutate the list.
        int take = (limit >= 0 && parts.length > limit) ? limit : parts.length;
        List<String> result = new ArrayList<>(take);
        for (int i = 0; i < take; i++) result.add(parts[i]);
        return result;
    }

    private Object charAt(Context context, Object[] args) {
        String s = asString(context);
        int index = ((Number) args[0]).intValue();
        if (index < 0 || index >= s.length()) {
            return "";
        }
        return String.valueOf(s.charAt(index));
    }

    private Object charCodeAt(Context context, Object[] args) {
        String s = asString(context);
        int index = ((Number) args[0]).intValue();
        if (index < 0 || index >= s.length()) {
            return Double.NaN;
        }
        return (int) s.charAt(index);
    }

    private Object codePointAt(Context context, Object[] args) {
        String s = asString(context);
        int index = ((Number) args[0]).intValue();
        if (index < 0 || index >= s.length()) {
            return Terms.UNDEFINED;
        }
        return s.codePointAt(index);
    }

    private Object concat(Context context, Object[] args) {
        String s = asString(context);
        StringBuilder sb = new StringBuilder(s);
        for (Object arg : args) {
            sb.append(arg);
        }
        return sb.toString();
    }

    private Object endsWith(Context context, Object[] args) {
        String s = asString(context);
        if (args.length > 1) {
            int endPosition = ((Number) args[1]).intValue();
            return s.substring(0, Math.min(endPosition, s.length())).endsWith((String) args[0]);
        }
        return s.endsWith((String) args[0]);
    }

    private Object includes(Context context, Object[] args) {
        String s = asString(context);
        String searchString = (String) args[0];
        if (args.length > 1) {
            int position = ((Number) args[1]).intValue();
            return s.indexOf(searchString, position) >= 0;
        }
        return s.contains(searchString);
    }

    private Object lastIndexOf(Context context, Object[] args) {
        String s = asString(context);
        if (args.length > 1) {
            return s.lastIndexOf((String) args[0], ((Number) args[1]).intValue());
        }
        return s.lastIndexOf((String) args[0]);
    }

    private Object padEnd(Context context, Object[] args) {
        String s = asString(context);
        int targetLength = ((Number) args[0]).intValue();
        String padString = args.length > 1 ? (String) args[1] : " ";
        if (padString.isEmpty()) {
            padString = " ";
        }
        if (s.length() >= targetLength) {
            return s;
        }
        int padLength = targetLength - s.length();
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < padLength; i++) {
            sb.append(padString.charAt(i % padString.length()));
        }
        return sb.toString();
    }

    private Object padStart(Context context, Object[] args) {
        String s = asString(context);
        int targetLength = ((Number) args[0]).intValue();
        String padString = args.length > 1 ? (String) args[1] : " ";
        if (padString.isEmpty()) {
            padString = " ";
        }
        if (s.length() >= targetLength) {
            return s;
        }
        int padLength = targetLength - s.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < padLength; i++) {
            sb.append(padString.charAt(i % padString.length()));
        }
        sb.append(s);
        return sb.toString();
    }

    private Object repeat(Context context, Object[] args) {
        String s = asString(context);
        int count = ((Number) args[0]).intValue();
        if (count < 0) {
            throw JsErrorException.rangeError("Invalid count value");
        }
        return s.repeat(count);
    }

    private Object slice(Context context, Object[] args) {
        String s = asString(context);
        int beginIndex = ((Number) args[0]).intValue();
        int endIndex = args.length > 1 ? ((Number) args[1]).intValue() : s.length();
        // handle negative indices
        if (beginIndex < 0) beginIndex = Math.max(s.length() + beginIndex, 0);
        if (endIndex < 0) endIndex = Math.max(s.length() + endIndex, 0);
        // ensure proper range
        beginIndex = Math.min(beginIndex, s.length());
        endIndex = Math.min(endIndex, s.length());
        if (beginIndex >= endIndex) return "";
        return s.substring(beginIndex, endIndex);
    }

    private Object substring(Context context, Object[] args) {
        String s = asString(context);
        int beginIndex = ((Number) args[0]).intValue();
        int endIndex = args.length > 1 ? ((Number) args[1]).intValue() : s.length();
        // ensure indices within bounds
        beginIndex = Math.min(Math.max(beginIndex, 0), s.length());
        endIndex = Math.min(Math.max(endIndex, 0), s.length());
        // swap if beginIndex > endIndex (per JS spec)
        if (beginIndex > endIndex) {
            int temp = beginIndex;
            beginIndex = endIndex;
            endIndex = temp;
        }
        return s.substring(beginIndex, endIndex);
    }

    private Object toLowerCase(Context context, Object[] args) {
        return asString(context).toLowerCase();
    }

    private Object toUpperCase(Context context, Object[] args) {
        return asString(context).toUpperCase();
    }

    private Object trim(Context context, Object[] args) {
        return asString(context).trim();
    }

    private Object trimStart(Context context, Object[] args) {
        return asString(context).replaceAll("^\\s+", "");
    }

    private Object trimEnd(Context context, Object[] args) {
        return asString(context).replaceAll("\\s+$", "");
    }

    private Object replace(Context context, Object[] args) {
        String s = asString(context);
        if (args[0] instanceof JsRegex regex) {
            return regex.replace(s, (String) args[1]);
        }
        return s.replace((String) args[0], (String) args[1]);
    }

    private Object replaceAll(Context context, Object[] args) {
        String s = asString(context);
        if (args[0] instanceof JsRegex regex) {
            if (!regex.global) {
                throw JsErrorException.typeError("String.prototype.replaceAll called with a non-global RegExp argument");
            }
            return regex.replace(s, (String) args[1]);
        }
        return s.replaceAll((String) args[0], (String) args[1]);
    }

    private Object match(Context context, Object[] args) {
        String s = asString(context);
        if (args.length == 0) {
            return List.of("");
        }
        JsRegex regex;
        if (args[0] instanceof JsRegex) {
            regex = (JsRegex) args[0];
        } else {
            regex = new JsRegex(args[0].toString());
        }
        return regex.match(s);
    }

    private Object matchAll(Context context, Object[] args) {
        String s = asString(context);
        // Spec: if regexp is a RegExp object, it must have the global flag.
        // Otherwise we coerce to a global RegExp (string-source patterns are auto-g).
        final JsRegex regex;
        if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) {
            regex = new JsRegex("", "g");
        } else if (args[0] instanceof JsRegex r) {
            if (!r.global) {
                throw JsErrorException.typeError("String.prototype.matchAll called with a non-global RegExp argument");
            }
            regex = r;
        } else {
            regex = new JsRegex(args[0].toString(), "g");
        }
        java.util.regex.Matcher matcher = regex.javaPattern.matcher(s);
        JsIterator iter = new JsIterator() {
            boolean fetched;
            boolean done;
            JsArray pending;

            private void fetch() {
                if (fetched || done) return;
                if (!matcher.find()) {
                    done = true;
                    return;
                }
                List<Object> groups = new ArrayList<>();
                groups.add(matcher.group(0));
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    String g = matcher.group(i);
                    groups.add(g != null ? g : Terms.UNDEFINED);
                }
                JsArray match = new JsArray(groups);
                match.putMember("index", matcher.start());
                match.putMember("input", s);
                pending = match;
                fetched = true;
            }

            @Override
            public boolean hasNext() {
                fetch();
                return !done;
            }

            @Override
            public Object next() {
                fetch();
                if (done) {
                    throw new java.util.NoSuchElementException();
                }
                fetched = false;
                JsArray v = pending;
                pending = null;
                return v;
            }
        };
        return IterUtils.toIteratorObject(iter);
    }

    private Object search(Context context, Object[] args) {
        String s = asString(context);
        if (args.length == 0) {
            return 0;
        }
        JsRegex regex;
        if (args[0] instanceof JsRegex) {
            regex = (JsRegex) args[0];
        } else {
            regex = new JsRegex(args[0].toString());
        }
        return regex.search(s);
    }

    private Object valueOf(Context context, Object[] args) {
        return asString(context);
    }

    private Object at(Context context, Object[] args) {
        String s = asString(context);
        if (args.length == 0 || !(args[0] instanceof Number)) {
            return Terms.UNDEFINED;
        }
        int index = ((Number) args[0]).intValue();
        if (index < 0) {
            index = s.length() + index;
        }
        if (index < 0 || index >= s.length()) {
            return Terms.UNDEFINED;
        }
        return String.valueOf(s.charAt(index));
    }

    private Object normalize(Context context, Object[] args) {
        String s = asString(context);
        String form = args.length > 0 && args[0] instanceof String ? (String) args[0] : "NFC";
        return switch (form) {
            case "NFD" -> java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
            case "NFKC" -> java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
            case "NFKD" -> java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKD);
            default -> java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC);
        };
    }

    private Object localeCompare(Context context, Object[] args) {
        String s = asString(context);
        if (args.length == 0) {
            return 0;
        }
        String other = args[0] != null ? args[0].toString() : "";
        return s.compareToIgnoreCase(other) < 0 ? -1 : s.compareToIgnoreCase(other) > 0 ? 1 : 0;
    }

}
