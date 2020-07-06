/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * @author pthomas3
 */
public class StringUtils {

    private StringUtils() {
        // only static methods
    }

    private static final String EMPTY = "";

    public static class Pair {

        public final String left;
        public final String right;

        public Pair(String left, String right) {
            this.left = left;
            this.right = right;
        }

        @Override // only needed for unit tests, so no validation and null checks
        public boolean equals(Object obj) {
            Pair o = (Pair) obj;
            return left.equals(o.left) && right.equals(o.right);
        }

    }

    public static Pair pair(String left, String right) {
        return new Pair(left, right);
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

    public static String join(Collection<String> c, char delimiter) {
        StringBuilder sb = new StringBuilder();
        Iterator iterator = c.iterator();
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
        List<String> list = new ArrayList();
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
            if (!EMPTY.equals(temp)) {
                list.add(temp);
            }
        }
        return list;
    }

    public static boolean isBlank(String s) {
        return trimToNull(s) == null;
    }

    public static String toIdString(String name) {
        return name.replaceAll("[\\s_]", "-").toLowerCase();
    }

    public static StringUtils.Pair splitByFirstLineFeed(String text) {
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
        return StringUtils.pair(left, right);
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
            int wrapEstimate = (int) Math.ceil(s.length() / colWidth);
            if (wrapEstimate == 0) {
                estimate++;
            } else {
                estimate += wrapEstimate;
            }
        }
        return estimate;
    }

    private static final Pattern FUNCTION_PATTERN = Pattern.compile("^function[^(]*\\(");

    public static boolean isJavaScriptFunction(String text) {
        return FUNCTION_PATTERN.matcher(text).find();
    }

    public static String fixJavaScriptFunction(String text) {
        Matcher matcher = FUNCTION_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.replaceFirst("function(");
        } else {
            return text;
        }
    }

}
