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
package com.intuit.karate;

/**
 *
 * @author pthomas3
 */
public class MatchStep {

    public final String name;
    public final String path;
    public final Match.Type type;
    public final String expected;

    public MatchStep(String raw) {
        boolean each = false;
        raw = raw.trim();
        if (raw.startsWith("each")) {
            each = true;
            raw = raw.substring(4).trim();
        }
        boolean contains = false;
        boolean not = false;
        boolean only = false;
        boolean any = false;
        boolean deep = false;
        int spacePos = raw.indexOf(' ');
        int leftParenPos = raw.indexOf('(');
        int rightParenPos = raw.indexOf(')');
        int lhsEndPos = raw.indexOf(" contains");
        if (lhsEndPos == -1) {
            lhsEndPos = raw.indexOf(" !contains");
        }
        int searchPos = 0;
        int eqPos = raw.indexOf(" == ");
        if (eqPos == -1) {
            eqPos = raw.indexOf(" != ");
        }
        if (lhsEndPos != -1 && (eqPos == -1 || eqPos > lhsEndPos)) {
            contains = true;
            not = raw.charAt(lhsEndPos + 1) == '!';
            searchPos = lhsEndPos + (not ? 10 : 9);
            String anyOrOnlyOrDeep = raw.substring(searchPos).trim();
            if (anyOrOnlyOrDeep.startsWith("only")) {
                int onlyPos = raw.indexOf(" only", searchPos);
                only = true;
                searchPos = onlyPos + 5;
            } else if (anyOrOnlyOrDeep.startsWith("any")) {
                int anyPos = raw.indexOf(" any", searchPos);
                any = true;
                searchPos = anyPos + 4;
            } else if (anyOrOnlyOrDeep.startsWith("deep")) {
                int deepPos = raw.indexOf(" deep", searchPos);
                deep = true;
                searchPos = deepPos + 5;
            }
        } else {
            int equalPos = raw.indexOf(" ==", searchPos);
            int notEqualPos = raw.indexOf(" !=", searchPos);
            if (equalPos == -1 && notEqualPos == -1) {
                throw new RuntimeException("syntax error, expected '==' for match");
            }
            lhsEndPos = min(equalPos, notEqualPos);
            if (lhsEndPos > spacePos && rightParenPos != -1
                    && rightParenPos > lhsEndPos && rightParenPos < leftParenPos) {
                equalPos = raw.indexOf(" ==", rightParenPos);
                notEqualPos = raw.indexOf(" !=", rightParenPos);
                if (equalPos == -1 && notEqualPos == -1) {
                    throw new RuntimeException("syntax error, expected '==' for match");
                }
                lhsEndPos = min(equalPos, notEqualPos);
            }
            not = lhsEndPos == notEqualPos;
            searchPos = lhsEndPos + 3;
        }
        String lhs = raw.substring(0, lhsEndPos).trim();
        if (leftParenPos == -1) {
            leftParenPos = lhs.indexOf('[');
            if (leftParenPos == 0) { // json array
                spacePos = -1; // just use lhs
                lhs = "(" + lhs + ")";
            }
        }
        if (spacePos != -1 && (leftParenPos > spacePos || leftParenPos == -1)) {
            name = lhs.substring(0, spacePos);
            path = StringUtils.trimToNull(lhs.substring(spacePos));
        } else {
            name = lhs;
            path = null;
        }
        expected = StringUtils.trimToNull(raw.substring(searchPos));
        type = getType(each, not, contains, only, any, deep);
    }

    private static int min(int a, int b) {
        if (a == -1) {
            return b;
        }
        if (b == -1) {
            return a;
        }
        return Math.min(a, b);
    }

    private static Match.Type getType(boolean each, boolean not, boolean contains, boolean only, boolean any, boolean deep) {
        if (each) {
            if (contains) {
                if (only) {
                    return Match.Type.EACH_CONTAINS_ONLY;
                }
                if (any) {
                    return Match.Type.EACH_CONTAINS_ANY;
                }
                return not ? Match.Type.EACH_NOT_CONTAINS : Match.Type.EACH_CONTAINS;
            }
            return not ? Match.Type.EACH_NOT_EQUALS : Match.Type.EACH_EQUALS;
        }
        if (contains) {
            if (only) {
                return Match.Type.CONTAINS_ONLY;
            }
            if (any) {
                return Match.Type.CONTAINS_ANY;
            }
            if (deep) {
                if (not) {
                    throw new RuntimeException("'!contains deep' is not yet supported, use 'contains deep' instead");
                }
                return Match.Type.CONTAINS_DEEP;
            }
            return not ? Match.Type.NOT_CONTAINS : Match.Type.CONTAINS;
        }
        return not ? Match.Type.NOT_EQUALS : Match.Type.EQUALS;
    }

}
