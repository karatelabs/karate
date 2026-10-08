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
 * A string literal followed on the same line by an identifier or another string literal is never
 * valid, and is almost always a doubled or stray quote ({@code ''standard_user'}). The parser can
 * backtrack to the start of the expression or statement before failing, so the reported token may
 * sit before the literal; the scan runs forward from just before the failure to the next {@code ;}.
 * <p>
 * Runs only while an error is being constructed.
 */
public class StrayQuoteHint {

    private StrayQuoteHint() {
    }

    private static final int MAX_LOOKAHEAD = 64;
    private static final int MAX_TEXT = 40;

    static String forFailure(List<Token> tokens, int position) {
        int start = Math.max(0, Math.min(position, tokens.size() - 1) - 1);
        int end = Math.min(tokens.size(), start + MAX_LOOKAHEAD);
        for (int i = start; i < end; i++) {
            Token token = tokens.get(i);
            if (token.type == TokenType.SEMI && i > start) {
                return null;
            }
            if (!isString(token)) {
                continue;
            }
            Token next = token.getNextPrimary();
            if (next == null || !(next.type == TokenType.IDENT || isString(next))
                    || BaseParser.lineTerminatorBetween(token, next)) {
                continue;
            }
            String text = token.getText();
            String prefix = "hint: line " + (token.line + 1) + " col " + (token.col + 1) + ": ";
            if (text.length() == 2) {
                return prefix + text + " is an empty string followed directly by " + clip(next.getText())
                        + " — a doubled or stray quote?";
            }
            return prefix + clip(text) + " is a string followed directly by " + clip(next.getText())
                    + " — a stray quote, or a missing ',' or '+'?";
        }
        return null;
    }

    private static boolean isString(Token token) {
        return token.type == TokenType.S_STRING || token.type == TokenType.D_STRING;
    }

    private static String clip(String text) {
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT) + "...";
    }

}
