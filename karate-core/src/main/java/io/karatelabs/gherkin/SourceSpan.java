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
package io.karatelabs.gherkin;

import io.karatelabs.parser.Token;

/**
 * A region of feature source. {@code start} (inclusive) and {@code end} (exclusive) are char
 * indexes into {@link io.karatelabs.common.Resource#getText()} — the file decoded as UTF-8 with
 * nothing stripped or normalized: a BOM is char 0 and CRLF stays two chars. The span starts at the
 * first char of its first token and ends after its last token, so it never includes the line
 * terminator. {@code line} and {@code endLine} are 1-based.
 */
public record SourceSpan(int start, int end, int line, int endLine) {

    static SourceSpan of(Token first, Token last) {
        return new SourceSpan(first.pos, last.pos + last.length, first.line + 1, last.line + 1);
    }

    public String text(String source) {
        return source.substring(start, end);
    }

}
