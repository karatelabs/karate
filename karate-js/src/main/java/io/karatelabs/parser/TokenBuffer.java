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

    // Parallel array for extracted token text - lazily allocated, memoized on first getText().
    // Without this every getText() call allocates a fresh String (plus its byte[]) out of the
    // source, and the interpreter calls it on each evaluation of a literal, an identifier and an
    // object-literal key - so a token inside a loop body is re-extracted on every iteration.
    //
    // volatile per the rule Node's javadoc states for exactly this shape: an array published by a
    // plain write can be seen non-null with its contents not yet visible. Final-field semantics
    // cover the String elements, not the array reference holding them. Read once into a local.
    private volatile String[] texts;

    // Parallel array for parsed literal values - lazily allocated, memoized on first use.
    // Caching the text alone still leaves Double.parseDouble running on every evaluation of a
    // numeric literal; this holds the parsed value itself.
    //
    // Null means "not computed yet". Safe because no branch of Terms.literalValue that does any
    // work can return null - a NUMBER that will not parse yields Double.NaN, BIGINT throws, and a
    // string literal yields at worst "". Only the default branch (the `null` keyword, and token
    // types that are not literals at all) returns null, and it parses nothing, so recomputing it
    // costs nothing.
    //
    // Values MUST stay immutable - Double, Long, Integer, BigInteger, String, Boolean - or the
    // unsynchronized publication below stops being safe. JsRegex is deliberately NOT cached: it
    // carries mutable lastIndex, and Interpreter builds it outside literalValue.
    private volatile Object[] literals;

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
        if (texts != null) {
            texts = Arrays.copyOf(texts, newCapacity);
        }
        if (literals != null) {
            literals = Arrays.copyOf(literals, newCapacity);
        }
        capacity = newCapacity;
    }

    /** Memoized literal value, or null if not computed yet. Same race rationale as getText. */
    Object getLiteral(int index) {
        Object[] cache = literals;
        return (cache != null && index >= 0 && index < cache.length) ? cache[index] : null;
    }

    void putLiteral(int index, Object value) {
        if (index < 0) {
            return;
        }
        Object[] cache = literals;
        if (cache == null) {
            cache = literals = new Object[cacheSize()];
        }
        if (index < cache.length) { // bound against the ARRAY, never against count - see getText
            cache[index] = value;
        }
    }

    /**
     * Caches are sized to the token count, not to {@code capacity}. Lexing runs to completion
     * before anything evaluates, so by first touch {@code count} is final and {@code capacity} is
     * merely whatever the doubling happened to land on - up to 4x the tokens actually present.
     */
    private int cacheSize() {
        return Math.max(count, 1);
    }

    /**
     * Source text of a token, memoized. Unsynchronized on purpose: a cached AST (a
     * {@code karate-config.js} for instance) is evaluated from several threads at once, and the
     * worst a race here can do is compute the same substring twice and discard one copy.
     * {@link String} has final fields, so a reader that sees an element sees a fully initialized
     * object; the array reference itself is {@code volatile} for the same reason spelled out on
     * the field.
     * <p>
     * Every bound here is against the ARRAY, never against {@code count}. Those are equal today
     * because lexing completes before evaluation begins, but if that ever stops holding, a stale
     * bound would index past a shorter array and throw. Checking the array degrades to "no cache"
     * instead, which is the whole point of a cache being optional.
     */
    String getText(int index, int pos, int length) {
        if (index < 0) {
            return resource.getText().substring(pos, pos + length);
        }
        String[] cache = texts;
        if (cache == null) {
            cache = texts = new String[cacheSize()];
        }
        if (index >= cache.length) { // not registered here, or added after the cache was sized
            return resource.getText().substring(pos, pos + length);
        }
        String text = cache[index];
        if (text == null) {
            text = cache[index] = resource.getText().substring(pos, pos + length);
        }
        return text;
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
