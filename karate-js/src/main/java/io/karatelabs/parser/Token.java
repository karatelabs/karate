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
package io.karatelabs.parser;

import io.karatelabs.common.Resource;

import java.util.Collections;
import java.util.List;

/**
 * Lightweight token using flyweight pattern. Core fields (pos, line, col, type, length)
 * are stored directly for performance. Resource and rarely-used data (comments, prev/next)
 * are accessed via TokenBuffer.
 */
public class Token {

    private static final TokenBuffer EMPTY_BUFFER = new TokenBuffer(Resource.text(""));
    public static final Token EMPTY = new Token(EMPTY_BUFFER, TokenType.EOF, 0, (short) 0, (short) 0, (short) 0);

    // Reference to shared buffer (holds Resource, Token[], and comments)
    final TokenBuffer buffer;
    // Index of this token in the buffer
    final int index;

    // Core fields kept as public final for direct access performance
    public final int pos;
    public final short line;
    public final short col;
    public final TokenType type;
    public final short length;

    public Token(TokenBuffer buffer, TokenType type, int pos, short line, short col, short length) {
        this.buffer = buffer;
        this.type = type;
        this.pos = pos;
        this.line = line;
        this.col = col;
        this.length = length;
        this.index = buffer.addToken(this);
    }

    // Convenience constructor for creating tokens with int line/col (auto-cast to short)
    public Token(TokenBuffer buffer, TokenType type, int pos, int line, int col, int length) {
        this(buffer, type, pos, (short) line, (short) col, (short) length);
    }

    public Resource getResource() {
        return buffer.resource;
    }

    public String getText() {
        return buffer.resource.getText().substring(pos, pos + length);
    }

    public Token getNextPrimary() {
        Token temp = this;
        do {
            temp = buffer.getNext(temp.index);
        } while (temp != null && !temp.type.primary);
        // this will never be null, because the last token is always EOF
        // and EOF is considered "primary" unlike white-space or comments
        return temp;
    }

    public Token getPrev() {
        return buffer.getPrev(index);
    }

    public Token getNext() {
        return buffer.getNext(index);
    }

    public String getLineText() {
        return buffer.resource.getLine(line);
    }

    public String getPositionDisplay() {
        return (line + 1) + ":" + (col + 1);
    }

    public List<Token> getComments() {
        List<Token> c = buffer.getComments(index);
        return c == null ? Collections.emptyList() : c;
    }

    @Override
    public String toString() {
        return switch (type) {
            case WS -> "_";
            case WS_LF -> "_\\n_";
            case EOF -> "_EOF_";
            default -> getText();
        };
    }

}
