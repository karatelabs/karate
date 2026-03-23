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

/**
 * Represents a recoverable syntax error during lenient parsing.
 * Unlike ParserException which is thrown for fatal errors, SyntaxError
 * is collected during error-tolerant parsing to enable IDE features
 * like syntax coloring even when the source code is incomplete.
 */
public class SyntaxError {

    public final Token token;
    public final String message;
    public final NodeType expected;

    public SyntaxError(Token token, String message, NodeType expected) {
        this.token = token;
        this.message = message;
        this.expected = expected;
    }

    public SyntaxError(Token token, String message) {
        this(token, message, null);
    }

    public int getLine() {
        return token.line + 1;
    }

    public int getColumn() {
        return token.col + 1;
    }

    public long getOffset() {
        return token.pos;
    }

    @Override
    public String toString() {
        return "[" + getLine() + ":" + getColumn() + "] " + message;
    }

}
