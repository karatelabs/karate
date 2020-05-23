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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 *
 * @author pthomas3
 */
public class PathSearch {
    
    public static boolean isWildcard(String locator) {
        char firstChar = locator.charAt(0);    
        return firstChar == '^' || firstChar == '~';
    }    

    protected static class Chunk {

        final String raw;
        final boolean anyDepth;
        final String controlType;
        final int index;
        final Predicate<String> nameCondition;
        final String name;

        Chunk(boolean anyDepth, String raw) {
            this.raw = raw;
            this.anyDepth = anyDepth;
            int pos = raw.indexOf('[');
            int controlTypeEndPos = -1;
            if (pos != -1) {
                controlTypeEndPos = pos;
                int endPos = raw.indexOf(']', pos);
                String temp = raw.substring(pos + 1, endPos);
                index = Integer.valueOf(temp) - 1;
            } else {
                index = -1;
            }
            pos = raw.indexOf('{');
            if (pos != -1) {
                if (controlTypeEndPos == -1) {
                    controlTypeEndPos = pos;
                }
                int endPos = raw.indexOf('}', pos);
                String temp = raw.substring(pos + 1, endPos);
                switch (temp.charAt(0)) {
                    case '^':
                        name = temp.substring(1);
                        nameCondition = s -> s.contains(name);
                        break;
                    case '~':
                        name = temp.substring(1);
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(name);
                        nameCondition = s -> pattern.matcher(s).find();
                        break;
                    default:
                        name = temp;
                        nameCondition = s -> s.equals(name);
                }
            } else {
                name = null;
                nameCondition = null;
            }
            if (controlTypeEndPos == -1) {
                controlType = raw;
            } else {
                controlType = raw.substring(0, controlTypeEndPos);
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
