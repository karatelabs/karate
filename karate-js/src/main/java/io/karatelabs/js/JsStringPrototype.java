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
import java.util.List;
import java.util.regex.Pattern;

/**
 * Singleton prototype for String instances. Methods wrapped via
 * {@link Prototype#method(String, int, JsCallable)} for spec
 * {@code length}+{@code name}; the base class caches wrapped instances
 * per-Engine.
 * <p>
 * Every method opens with {@link #thisString} — spec
 * {@code RequireObjectCoercible(this)} + {@code ToString(this)}. Arg coercion
 * goes through {@link #argString} (string args) or {@link #argInt} (integer
 * args) so {@code "x".indexOf(undefined)}, {@code "x".charAt({})}, etc. land
 * on the spec ToString / ToInteger pipeline rather than blowing up with a
 * {@code ClassCastException}.
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
        install(IterUtils.SYMBOL_ITERATOR, 0, IterUtils.SYMBOL_ITERATOR_METHOD);
    }

    // Spec preamble for every String.prototype.* method:
    //   1. RequireObjectCoercible(this) — null / undefined throws TypeError.
    //   2. ToString(this) — primitive fast paths, fall through to the full
    //      ToPrimitive-then-ToString pipeline so a host with a JS toString
    //      returns the user's string, not "[object Object]".
    private static String thisString(Context context, String methodName) {
        Object thisObj = context.getThisObject();
        Terms.requireObjectCoercible(thisObj, "String.prototype." + methodName);
        if (thisObj instanceof JsString js) return js.text;
        if (thisObj instanceof String s) return s;
        return Terms.toStringCoerce(thisObj, context instanceof CoreContext cc ? cc : null);
    }

    // Spec ToString for an argument. No RequireObjectCoercible — args[k] of
    // undefined is legal (coerces to "undefined"); only `this` is gated.
    private static String argString(Object[] args, int idx, Context context) {
        Object arg = idx < args.length ? args[idx] : Terms.UNDEFINED;
        if (arg instanceof String s) return s;
        if (arg instanceof JsString js) return js.text;
        return Terms.toStringCoerce(arg, context instanceof CoreContext cc ? cc : null);
    }

    // Spec ToIntegerOrInfinity-then-clamp helper for integer arguments. Wraps
    // {@link Terms#objectToNumber} so booleans / strings / objects coerce per
    // spec instead of failing the {@code (Number) args[k]} cast.
    private static int argInt(Object[] args, int idx, int defaultValue) {
        if (idx >= args.length) return defaultValue;
        Object arg = args[idx];
        if (arg == null || arg == Terms.UNDEFINED) return defaultValue;
        Number n = Terms.objectToNumber(arg);
        double d = n.doubleValue();
        if (Double.isNaN(d)) return 0;
        if (d > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (d < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) d;
    }

    // Instance methods

    private Object indexOf(Context context, Object[] args) {
        String s = thisString(context, "indexOf");
        String search = argString(args, 0, context);
        int from = argInt(args, 1, 0);
        return s.indexOf(search, from);
    }

    private Object startsWith(Context context, Object[] args) {
        String s = thisString(context, "startsWith");
        String search = argString(args, 0, context);
        int from = argInt(args, 1, 0);
        return s.startsWith(search, from);
    }

    private Object getBytes(Context context, Object[] args) {
        return thisString(context, "getBytes").getBytes(StandardCharsets.UTF_8);
    }

    private Object split(Context context, Object[] args) {
        String s = thisString(context, "split");
        // JS semantics:
        //   str.split() / str.split(undefined) → [str]
        //   str.split('') → each char as element
        //   str.split(sep) → literal match on sep (NOT a regex)
        //   str.split(/re/) → regex match
        //   limit (arg[1]) if provided truncates the result
        // Java's String.split treats sep as a regex, so '|a|b|'.split('|') would
        // explode (| is alternation) — quote literal seps with Pattern.quote.
        if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) {
            JsArray single = new JsArray(new ArrayList<>(1));
            single.list.add(s);
            return single;
        }
        int limit = -1; // JS default: keep all, including trailing empty strings
        if (args.length > 1 && args[1] != null && args[1] != Terms.UNDEFINED) {
            limit = argInt(args, 1, 0);
            if (limit <= 0) return new JsArray(new ArrayList<>());
        }
        String[] parts;
        if (args[0] instanceof JsRegex regex) {
            parts = regex.javaPattern.split(s, -1);
        } else {
            String sep = argString(args, 0, context);
            if (sep.isEmpty()) {
                int capped = limit < 0 ? s.length() : Math.min(limit, s.length());
                List<Object> result = new ArrayList<>(capped);
                for (int i = 0; i < capped; i++) {
                    result.add(String.valueOf(s.charAt(i)));
                }
                return new JsArray(result);
            }
            // -1 to keep trailing empty segments (JS-compatible, Java default drops them)
            parts = s.split(Pattern.quote(sep), -1);
        }
        int take = (limit >= 0 && parts.length > limit) ? limit : parts.length;
        List<Object> result = new ArrayList<>(take);
        for (int i = 0; i < take; i++) result.add(parts[i]);
        return new JsArray(result);
    }

    private Object charAt(Context context, Object[] args) {
        String s = thisString(context, "charAt");
        int index = argInt(args, 0, 0);
        if (index < 0 || index >= s.length()) {
            return "";
        }
        return String.valueOf(s.charAt(index));
    }

    private Object charCodeAt(Context context, Object[] args) {
        String s = thisString(context, "charCodeAt");
        int index = argInt(args, 0, 0);
        if (index < 0 || index >= s.length()) {
            return Double.NaN;
        }
        return (int) s.charAt(index);
    }

    private Object codePointAt(Context context, Object[] args) {
        String s = thisString(context, "codePointAt");
        int index = argInt(args, 0, 0);
        if (index < 0 || index >= s.length()) {
            return Terms.UNDEFINED;
        }
        return s.codePointAt(index);
    }

    private Object concat(Context context, Object[] args) {
        String s = thisString(context, "concat");
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < args.length; i++) {
            sb.append(argString(args, i, context));
        }
        return sb.toString();
    }

    private Object endsWith(Context context, Object[] args) {
        String s = thisString(context, "endsWith");
        String search = argString(args, 0, context);
        if (args.length > 1 && args[1] != null && args[1] != Terms.UNDEFINED) {
            int endPosition = argInt(args, 1, s.length());
            return s.substring(0, Math.min(endPosition, s.length())).endsWith(search);
        }
        return s.endsWith(search);
    }

    private Object includes(Context context, Object[] args) {
        String s = thisString(context, "includes");
        String search = argString(args, 0, context);
        int from = argInt(args, 1, 0);
        return s.indexOf(search, from) >= 0;
    }

    private Object lastIndexOf(Context context, Object[] args) {
        String s = thisString(context, "lastIndexOf");
        String search = argString(args, 0, context);
        if (args.length > 1 && args[1] != null && args[1] != Terms.UNDEFINED) {
            int from = argInt(args, 1, s.length());
            return s.lastIndexOf(search, from);
        }
        return s.lastIndexOf(search);
    }

    private Object padEnd(Context context, Object[] args) {
        String s = thisString(context, "padEnd");
        int targetLength = argInt(args, 0, 0);
        String padString = (args.length > 1 && args[1] != Terms.UNDEFINED) ? argString(args, 1, context) : " ";
        if (padString.isEmpty()) {
            return s;
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
        String s = thisString(context, "padStart");
        int targetLength = argInt(args, 0, 0);
        String padString = (args.length > 1 && args[1] != Terms.UNDEFINED) ? argString(args, 1, context) : " ";
        if (padString.isEmpty()) {
            return s;
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
        String s = thisString(context, "repeat");
        // Spec: ToIntegerOrInfinity, then RangeError if < 0 or +Infinity.
        // We only carry double precision through argInt; check the original
        // numeric for the -0 / +Infinity edge before clamping.
        if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) return "";
        double d = Terms.objectToNumber(args[0]).doubleValue();
        if (Double.isNaN(d)) return "";
        if (d < 0 || Double.isInfinite(d)) {
            throw JsErrorException.rangeError("Invalid count value");
        }
        int count = (int) d;
        return s.repeat(count);
    }

    private Object slice(Context context, Object[] args) {
        String s = thisString(context, "slice");
        int beginIndex = argInt(args, 0, 0);
        int endIndex = (args.length > 1 && args[1] != Terms.UNDEFINED) ? argInt(args, 1, s.length()) : s.length();
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
        String s = thisString(context, "substring");
        int beginIndex = argInt(args, 0, 0);
        int endIndex = (args.length > 1 && args[1] != Terms.UNDEFINED) ? argInt(args, 1, s.length()) : s.length();
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
        return thisString(context, "toLowerCase").toLowerCase();
    }

    private Object toUpperCase(Context context, Object[] args) {
        return thisString(context, "toUpperCase").toUpperCase();
    }

    private Object trim(Context context, Object[] args) {
        return trimSpec(thisString(context, "trim"), true, true);
    }

    private Object trimStart(Context context, Object[] args) {
        return trimSpec(thisString(context, "trimStart"), true, false);
    }

    private Object trimEnd(Context context, Object[] args) {
        return trimSpec(thisString(context, "trimEnd"), false, true);
    }

    // Spec §22.1.3.31 trim / .31.1 trimStart / .31.2 trimEnd: strip
    // {@code WhiteSpace} ∪ {@code LineTerminator} per §11.2/11.3. Java's
    // {@code String.trim} only handles ASCII <= U+0020; {@code \s} misses
    // NBSP / ZWNBSP / U+1680 / U+2000–200A / U+2028 / U+2029 / U+202F /
    // U+205F / U+3000. Walk codepoints and consult {@link #isJsWhitespace}.
    private static String trimSpec(String s, boolean leading, boolean trailing) {
        int len = s.length();
        int start = 0, end = len;
        if (leading) {
            while (start < end) {
                int cp = s.codePointAt(start);
                if (!isJsWhitespace(cp)) break;
                start += Character.charCount(cp);
            }
        }
        if (trailing) {
            while (end > start) {
                int cp = s.codePointBefore(end);
                if (!isJsWhitespace(cp)) break;
                end -= Character.charCount(cp);
            }
        }
        return (start == 0 && end == len) ? s : s.substring(start, end);
    }

    // JS WhiteSpace ∪ LineTerminator. Listed code points are explicit per
    // spec; the Zs fallback catches the rest of the Space_Separator block
    // (U+1680, U+2000–U+200A, U+202F, U+205F, U+3000). U+180E is *not*
    // included — reclassified out of Zs in Unicode 6.3 (test262 u180e.js).
    private static boolean isJsWhitespace(int c) {
        return c == 0x09 || c == 0x0B || c == 0x0C || c == 0x20
                || c == 0xA0 || c == 0xFEFF
                || c == 0x0A || c == 0x0D || c == 0x2028 || c == 0x2029
                || (c >= 0x1680 && Character.getType(c) == Character.SPACE_SEPARATOR);
    }

    private Object replace(Context context, Object[] args) {
        String s = thisString(context, "replace");
        Object search = args.length > 0 ? args[0] : Terms.UNDEFINED;
        Object replacement = args.length > 1 ? args[1] : Terms.UNDEFINED;
        if (search instanceof JsRegex regex) {
            if (replacement instanceof JsCallable fn) {
                return regexReplace(s, regex, fn, context, false);
            }
            return regex.replace(s, argString(args, 1, context));
        }
        String searchStr = argString(args, 0, context);
        if (replacement instanceof JsCallable fn) {
            int idx = s.indexOf(searchStr);
            if (idx < 0) return s;
            Object r = fn.call(context, new Object[]{searchStr, idx, s});
            String coerced = Terms.toStringCoerce(r, context instanceof CoreContext cc ? cc : null);
            return s.substring(0, idx) + coerced + s.substring(idx + searchStr.length());
        }
        return s.replace(searchStr, argString(args, 1, context));
    }

    private Object replaceAll(Context context, Object[] args) {
        String s = thisString(context, "replaceAll");
        Object search = args.length > 0 ? args[0] : Terms.UNDEFINED;
        Object replacement = args.length > 1 ? args[1] : Terms.UNDEFINED;
        if (search instanceof JsRegex regex) {
            if (!regex.global) {
                throw JsErrorException.typeError("String.prototype.replaceAll called with a non-global RegExp argument");
            }
            if (replacement instanceof JsCallable fn) {
                return regexReplace(s, regex, fn, context, true);
            }
            return regex.replace(s, argString(args, 1, context));
        }
        String searchStr = argString(args, 0, context);
        if (replacement instanceof JsCallable fn) {
            // Walk every literal occurrence; coerce each callback result to string.
            StringBuilder sb = new StringBuilder();
            int from = 0;
            while (true) {
                int idx = searchStr.isEmpty() ? from : s.indexOf(searchStr, from);
                if (idx < 0 || (searchStr.isEmpty() && from > s.length())) break;
                sb.append(s, from, idx);
                Object r = fn.call(context, new Object[]{searchStr, idx, s});
                sb.append(Terms.toStringCoerce(r, context instanceof CoreContext cc ? cc : null));
                if (searchStr.isEmpty()) {
                    if (from < s.length()) sb.append(s.charAt(from));
                    from++;
                } else {
                    from = idx + searchStr.length();
                }
            }
            sb.append(s, from, s.length());
            return sb.toString();
        }
        return s.replace(searchStr, argString(args, 1, context));
    }

    // Spec §22.1.3.18 — for each match: call fn(match, p1...pN, offset, string),
    // coerce result to string, splice in. {@code global=false} stops after the
    // first hit. Local to JsStringPrototype because JsRegex shouldn't depend
    // on JsCallable / Context.
    private static String regexReplace(String s, JsRegex regex, JsCallable fn, Context context, boolean global) {
        java.util.regex.Matcher m = regex.javaPattern.matcher(s);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        CoreContext cc = context instanceof CoreContext c ? c : null;
        while (m.find()) {
            sb.append(s, last, m.start());
            int groups = m.groupCount();
            Object[] callArgs = new Object[groups + 3];
            callArgs[0] = m.group(0);
            for (int i = 1; i <= groups; i++) {
                String g = m.group(i);
                callArgs[i] = g != null ? g : Terms.UNDEFINED;
            }
            callArgs[groups + 1] = m.start();
            callArgs[groups + 2] = s;
            Object r = fn.call(context, callArgs);
            sb.append(Terms.toStringCoerce(r, cc));
            last = m.end();
            if (!global) break;
        }
        sb.append(s, last, s.length());
        return sb.toString();
    }

    private Object match(Context context, Object[] args) {
        String s = thisString(context, "match");
        if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) {
            return List.of("");
        }
        JsRegex regex = (args[0] instanceof JsRegex r) ? r : new JsRegex(argString(args, 0, context));
        return regex.match(s);
    }

    private Object matchAll(Context context, Object[] args) {
        String s = thisString(context, "matchAll");
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
            regex = new JsRegex(argString(args, 0, context), "g");
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
        String s = thisString(context, "search");
        if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) {
            return 0;
        }
        JsRegex regex = (args[0] instanceof JsRegex r) ? r : new JsRegex(argString(args, 0, context));
        return regex.search(s);
    }

    private Object valueOf(Context context, Object[] args) {
        // Spec thisStringValue: if the receiver is a String primitive or a
        // wrapped JsString, return its text — else TypeError. The thisString
        // helper coerces objects to "[object Object]"; for valueOf we want
        // the strict identity check.
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsString js) return js.text;
        if (thisObj instanceof String s) return s;
        Terms.requireObjectCoercible(thisObj, "String.prototype.valueOf");
        throw JsErrorException.typeError("String.prototype.valueOf requires that 'this' be a String");
    }

    private Object at(Context context, Object[] args) {
        String s = thisString(context, "at");
        if (args.length == 0) return Terms.UNDEFINED;
        int index = argInt(args, 0, 0);
        if (index < 0) {
            index = s.length() + index;
        }
        if (index < 0 || index >= s.length()) {
            return Terms.UNDEFINED;
        }
        return String.valueOf(s.charAt(index));
    }

    private Object normalize(Context context, Object[] args) {
        String s = thisString(context, "normalize");
        String form = (args.length > 0 && args[0] != Terms.UNDEFINED) ? argString(args, 0, context) : "NFC";
        return switch (form) {
            case "NFC" -> java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC);
            case "NFD" -> java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
            case "NFKC" -> java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
            case "NFKD" -> java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKD);
            default -> throw JsErrorException.rangeError("The normalization form should be one of NFC, NFD, NFKC, NFKD");
        };
    }

    private Object localeCompare(Context context, Object[] args) {
        String s = thisString(context, "localeCompare");
        String other = argString(args, 0, context);
        return Integer.signum(s.compareToIgnoreCase(other));
    }

}
