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

import java.util.Arrays;
import java.util.List;

/**
 * Flyweight container for tokens. Holds shared Resource and parallel arrays
 * for rarely-used token data (comments, prev/next navigation).
 */
public class TokenBuffer {

    private static final int MIN_CAPACITY = 64;
    // Heuristic: roughly 1 token per 4 characters of source
    private static final int CHARS_PER_TOKEN = 4;

    public final Resource resource;

    // Token array for prev/next navigation
    private Token[] tokens;
    private int count;
    private int capacity;

    // Parallel array for comments - lazily allocated, rarely used
    private List<Token>[] comments;

    public TokenBuffer(Resource resource) {
        this.resource = resource;
        // Estimate capacity based on source length to minimize resizing
        int sourceLength = resource.getText().length();
        this.capacity = Math.max(MIN_CAPACITY, sourceLength / CHARS_PER_TOKEN);
        this.tokens = new Token[capacity];
        this.count = 0;
    }

    /**
     * Registers a token and returns its index in the buffer.
     */
    public int addToken(Token token) {
        if (count >= capacity) {
            grow();
        }
        int index = count++;
        tokens[index] = token;
        return index;
    }

    private void grow() {
        int newCapacity = capacity * 2;
        tokens = Arrays.copyOf(tokens, newCapacity);
        if (comments != null) {
            comments = Arrays.copyOf(comments, newCapacity);
        }
        capacity = newCapacity;
    }

    public Token getToken(int index) {
        return (index >= 0 && index < count) ? tokens[index] : null;
    }

    public Token getPrev(int index) {
        return (index > 0) ? tokens[index - 1] : null;
    }

    public Token getNext(int index) {
        return (index >= 0 && index < count - 1) ? tokens[index + 1] : null;
    }

    // ========== Comments (lazily allocated) ==========

    @SuppressWarnings("unchecked")
    public void setComments(int index, List<Token> tokenComments) {
        if (comments == null) {
            comments = new List[capacity];
        }
        comments[index] = tokenComments;
    }

    public List<Token> getComments(int index) {
        return (comments != null && index >= 0 && index < count) ? comments[index] : null;
    }

    public int size() {
        return count;
    }

}
