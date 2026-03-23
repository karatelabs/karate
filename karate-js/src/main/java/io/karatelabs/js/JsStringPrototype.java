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
import java.util.Arrays;
import java.util.List;

/**
 * Singleton prototype for String instances.
 * Contains instance methods like indexOf, split, charAt, etc.
 * Inherits from JsObjectPrototype.
 */
class JsStringPrototype extends Prototype {

    static final JsStringPrototype INSTANCE = new JsStringPrototype();

    private JsStringPrototype() {
        super(JsObjectPrototype.INSTANCE);
    }

    @Override
    protected Object getBuiltinProperty(String name) {
        return switch (name) {
            case "indexOf" -> (JsCallable) this::indexOf;
            case "startsWith" -> (JsCallable) this::startsWith;
            case "getBytes" -> (JsCallable) this::getBytes;
            case "split" -> (JsCallable) this::split;
            case "charAt" -> (JsCallable) this::charAt;
            case "charCodeAt" -> (JsCallable) this::charCodeAt;
            case "codePointAt" -> (JsCallable) this::codePointAt;
            case "concat" -> (JsCallable) this::concat;
            case "endsWith" -> (JsCallable) this::endsWith;
            case "includes" -> (JsCallable) this::includes;
            case "lastIndexOf" -> (JsCallable) this::lastIndexOf;
            case "padEnd" -> (JsCallable) this::padEnd;
            case "padStart" -> (JsCallable) this::padStart;
            case "repeat" -> (JsCallable) this::repeat;
            case "slice" -> (JsCallable) this::slice;
            case "substring" -> (JsCallable) this::substring;
            case "toLowerCase" -> (JsCallable) this::toLowerCase;
            case "toUpperCase" -> (JsCallable) this::toUpperCase;
            case "trim" -> (JsCallable) this::trim;
            case "trimStart", "trimLeft" -> (JsCallable) this::trimStart;
            case "trimEnd", "trimRight" -> (JsCallable) this::trimEnd;
            case "replace" -> (JsCallable) this::replace;
            case "replaceAll" -> (JsCallable) this::replaceAll;
            case "match" -> (JsCallable) this::match;
            case "search" -> (JsCallable) this::search;
            case "valueOf" -> (JsCallable) this::valueOf;
            default -> null;
        };
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
        return Arrays.asList(s.split((String) args[0]));
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
            throw new RuntimeException("invalid count value");
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
                throw new RuntimeException("replaceAll requires regex with global flag");
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

}
