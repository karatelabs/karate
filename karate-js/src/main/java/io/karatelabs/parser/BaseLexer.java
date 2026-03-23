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
package io.karatelabs.parser;

import io.karatelabs.common.Resource;

import java.util.ArrayList;
import java.util.List;

import static io.karatelabs.parser.TokenType.*;

/**
 * Abstract base class for lexers. Provides common utilities for character
 * handling, position tracking, and tokenization.
 */
public abstract class BaseLexer {

    protected final Resource resource;
    protected final TokenBuffer buffer;
    protected final String source;
    protected final int length;

    protected int pos;
    protected int line;
    protected int col;
    protected int tokenStart;
    protected int tokenLine;
    protected int tokenCol;

    protected BaseLexer(Resource resource) {
        this.resource = resource;
        this.buffer = new TokenBuffer(resource);
        this.source = resource.getText();
        this.length = source.length();
        this.pos = 0;
        this.line = resource.getLineOffset();
        this.col = 0;
    }

    // ========== Public API ==========

    public abstract Token nextToken();

    protected abstract TokenType scanToken();

    // ========== Tokenization Utilities ==========

    /**
     * Tokenizes the source into a list of tokens. Tokens are stored in the
     * lexer's buffer with prev/next navigation. Comments are collected for
     * primary tokens.
     */
    public static List<Token> tokenize(BaseLexer lexer) {
        List<Token> list = new ArrayList<>();
        List<Token> comments = new ArrayList<>();
        Token token;

        do {
            token = lexer.nextToken();
            // prev/next navigation now handled by TokenBuffer via index

            if (token.type.primary) {
                list.add(token);
                if (!comments.isEmpty()) {
                    lexer.buffer.setComments(token.index, comments);
                    comments = new ArrayList<>();
                }
            } else if (token.type == L_COMMENT || token.type == G_COMMENT || token.type == B_COMMENT) {
                comments.add(token);
            }
        } while (token.type != EOF);

        return list;
    }

    // ========== Character Utilities ==========

    protected boolean isAtEnd() {
        return pos >= length;
    }

    protected char peek() {
        return pos >= length ? '\0' : source.charAt(pos);
    }

    protected char peek(int offset) {
        int index = pos + offset;
        return (index < 0 || index >= length) ? '\0' : source.charAt(index);
    }

    protected char advance() {
        char c = source.charAt(pos++);
        if (c == '\n') {
            line++;
            col = 0;
        } else {
            col++;
        }
        return c;
    }

    protected boolean match(char expected) {
        if (pos >= length || source.charAt(pos) != expected) {
            return false;
        }
        advance();
        return true;
    }

    // ========== Character Classification ==========

    protected static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    protected static boolean isHexDigit(char c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    protected static boolean isIdentifierStart(char c) {
        return Character.isJavaIdentifierStart(c);
    }

    protected static boolean isIdentifierPart(char c) {
        return Character.isJavaIdentifierPart(c);
    }

}
