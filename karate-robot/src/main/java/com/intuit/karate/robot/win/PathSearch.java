/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.robot.win;

import com.intuit.karate.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;

/**
 *
 * @author pthomas3
 */
public class PathSearch {

    public static boolean isWildcard(String locator) {
        char firstChar = locator.charAt(0);
        return firstChar == '^' || firstChar == '~';
    }

    private static final java.util.regex.Pattern PATH_CHUNK = java.util.regex.Pattern.compile("([^.{]+)?(\\.[^{]+)?(\\{.+\\})?");

    protected static class Chunk {

        final String raw;
        final boolean anyDepth;
        final String controlType;
        final String className;
        final int index;
        final Predicate<String> nameCondition;
        final String name;

        Chunk(boolean anyDepth, String raw) {
            this.anyDepth = anyDepth;
            this.raw = raw;
            Matcher matcher = PATH_CHUNK.matcher(raw);
            if (!matcher.find()) {
                throw new RuntimeException("invalid path pattern: " + raw);
            }
            String typeAndIndex = matcher.group(1);
            if (typeAndIndex == null) {
                index = -1;
                controlType = null;
            } else {
                int pos = typeAndIndex.indexOf('[');
                if (pos != -1) {
                    int endPos = raw.indexOf(']', pos);
                    String temp = raw.substring(pos + 1, endPos);
                    index = Integer.valueOf(temp) - 1;
                    controlType = typeAndIndex.substring(0, pos);
                } else {
                    index = -1;
                    controlType = typeAndIndex;
                }
            }
            String dotAndClassName = matcher.group(2);
            className = dotAndClassName == null ? null : dotAndClassName.substring(1);
            String prefixAndName = matcher.group(3);
            if (prefixAndName == null) {
                name = null;
                nameCondition = null;
            } else {
                prefixAndName = prefixAndName.substring(1, prefixAndName.length() - 1);
                switch (prefixAndName.charAt(0)) {
                    case '^':
                        name = prefixAndName.substring(1);
                        nameCondition = s -> s.contains(name);
                        break;
                    case '~':
                        name = prefixAndName.substring(1);
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(name);
                        nameCondition = s -> pattern.matcher(s).find();
                        break;
                    default:
                        name = StringUtils.trimToNull(prefixAndName);
                        nameCondition = name == null ? null : s -> s.equals(name);
                }
            }
        }

        @Override
        public String toString() {
            return (anyDepth ? "//" : "/") + raw;
        }

    }

    private static final char SLASH = '/';

    public final boolean findAll;
    private final String path;
    public final List<Chunk> chunks;

    public PathSearch(String path, boolean findAll) {
        this.path = path;
        this.findAll = findAll;
        chunks = split(path);
    }

    public static List<Chunk> split(String s) {
        int pos = s.indexOf(SLASH);
        if (pos == -1) {
            throw new RuntimeException("path did not start with or contain '/'");
        }
        List<Chunk> list = new ArrayList();
        int startPos = 0;
        int searchPos = 0;
        boolean anyDepth = false;
        while (pos != -1) {
            if (pos == 0) {
                startPos = 1;
                searchPos = 1;
            } else if (s.charAt(pos - 1) == '\\') {
                s = s.substring(0, pos - 1) + s.substring(pos);
                searchPos = pos;
            } else {
                String temp = s.substring(startPos, pos);
                if (temp.isEmpty()) {
                    anyDepth = true;
                } else {
                    list.add(new Chunk(anyDepth, temp));
                    anyDepth = false; // reset                   
                }
                startPos = pos + 1;
                searchPos = startPos;
            }
            pos = s.indexOf(SLASH, searchPos);
        }
        if (startPos != s.length()) {
            String temp = s.substring(startPos);
            if (!temp.isEmpty()) {
                list.add(new Chunk(anyDepth, temp));
            }
        }
        return list;
    }

    @Override
    public String toString() {
        return path;
    }

}
