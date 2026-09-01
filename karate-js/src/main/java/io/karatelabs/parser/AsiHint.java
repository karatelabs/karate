/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.parser;

import java.util.List;

/**
 * A line that starts with {@code [} or {@code (} continues the previous line unless that line ended
 * with a {@code ;} — the statement becomes an index or a call. That is spec ASI behavior and not a
 * bug, but the resulting failure names a token nowhere near the omitted {@code ;}, so the message
 * says so explicitly.
 * <p>
 * Everything here runs only while an error is being constructed; nothing is on the success path.
 * Recall is deliberately traded for precision — a hint pointing at the wrong line is worse than none.
 */
public class AsiHint {

    private AsiHint() {
    }

    private static final int MAX_LOOKBACK = 256;

    /**
     * Walk back from the failing token for the opener that swallowed a statement. A {@code ;}
     * in between means the statements were already separated, so the theory is dead.
     */
    static String forFailure(List<Token> tokens, int position) {
        int i = Math.min(position, tokens.size() - 1);
        int limit = Math.max(0, i - MAX_LOOKBACK);
        for (; i >= limit; i--) {
            Token token = tokens.get(i);
            if (token.type == TokenType.SEMI) {
                return null;
            }
            String hint = forOpener(token);
            if (hint != null) {
                return hint;
            }
        }
        return null;
    }

    /**
     * Non-null only when {@code token} is a {@code [} or {@code (} that is the first token on its
     * line and follows a token that ends an operand — the exact shape where an omitted {@code ;}
     * changes the parse.
     */
    public static String forOpener(Token token) {
        if (token == null) {
            return null;
        }
        boolean bracket = token.type == TokenType.L_BRACKET;
        if (!bracket && token.type != TokenType.L_PAREN) {
            return null;
        }
        Token prev = prevPrimary(token);
        // a regex is disallowed after exactly those tokens that can end an operand
        if (prev == null || !Boolean.FALSE.equals(prev.type.regexAllowed)) {
            return null;
        }
        if (!BaseParser.lineTerminatorBetween(prev, token)) {
            return null; // something else precedes it on the same line
        }
        if (prev.type == TokenType.R_PAREN && closesControlHead(prev)) {
            return null; // `if (x)` and friends never wanted a ';' there
        }
        int prevLine = prev.line + 1;
        return "hint: line " + (token.line + 1) + " starts with '" + (bracket ? '[' : '(')
                + "' — without a ';' ending line " + prevLine + " it continues that statement (as "
                + (bracket ? "an index" : "a call") + "); add ';' to the end of line " + prevLine
                + " if a new statement was intended";
    }

    /** True if this {@code )} closes the parenthesized head of a control statement. */
    private static boolean closesControlHead(Token rParen) {
        int depth = 0;
        Token token = rParen;
        for (int i = 0; i < MAX_LOOKBACK && token != null; i++) {
            if (token.type == TokenType.R_PAREN) {
                depth++;
            } else if (token.type == TokenType.L_PAREN && --depth == 0) {
                Token before = prevPrimary(token);
                return before != null && before.type.oneOf(TokenType.IF, TokenType.FOR,
                        TokenType.WHILE, TokenType.SWITCH, TokenType.CATCH);
            }
            token = token.getPrev();
        }
        return false;
    }

    private static Token prevPrimary(Token token) {
        Token temp = token.getPrev();
        while (temp != null && !temp.type.primary) {
            temp = temp.getPrev();
        }
        return temp;
    }

}
