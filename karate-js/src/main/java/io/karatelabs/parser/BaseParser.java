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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static io.karatelabs.parser.TokenType.*;

public abstract class BaseParser {

    static final Logger logger = LoggerFactory.getLogger(BaseParser.class);

    private static final int MAX_DEPTH = 128;

    protected final Resource resource;
    protected final List<Token> tokens;
    private final int size;

    private int position = 0;

    // Stack-based marker state (replaces linked Marker objects)
    private int stackPointer = 0;
    private final int[] positionStack = new int[MAX_DEPTH];
    private final Node[] nodeStack = new Node[MAX_DEPTH];

    // Error recovery infrastructure
    protected final boolean errorRecoveryEnabled;
    private final List<SyntaxError> errors = new ArrayList<>();
    private int lastRecoveryPosition = -1;

    protected Node markerNode() {
        return nodeStack[stackPointer - 1];
    }

    protected boolean isCallerType(NodeType type) {
        return stackPointer >= 2 && nodeStack[stackPointer - 2].type == type;
    }

    protected enum Shift {
        NONE, LEFT, RIGHT
    }

    protected BaseParser(Resource resource, List<Token> tokens, boolean errorRecovery) {
        this.resource = resource;
        this.errorRecoveryEnabled = errorRecovery;
        this.tokens = tokens;
        size = tokens.size();
        // Initialize root node on stack
        positionStack[0] = position;
        nodeStack[0] = new Node(NodeType.ROOT);
        stackPointer = 1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, position - 7);
        int end = Math.min(position + 7, size);
        for (int i = start; i < end; i++) {
            if (i == 0) {
                sb.append("| ");
            }
            if (i == position) {
                sb.append(">>");
            }
            sb.append(tokens.get(i));
            sb.append(' ');
        }
        if (position == size) {
            sb.append(">>");
        }
        sb.append("|");
        sb.append("\ncurrent node: ");
        // Format stack trace for debugging
        if (stackPointer >= 3) {
            sb.append(nodeStack[stackPointer - 3].type).append(" >> ");
        }
        if (stackPointer >= 2) {
            sb.append(nodeStack[stackPointer - 2].type).append(" >> ");
        }
        sb.append('[').append(nodeStack[stackPointer - 1].type).append(']');
        return sb.toString();
    }

    protected void error(String message) {
        Token token = peekToken();
        if (errorRecoveryEnabled) {
            errors.add(new SyntaxError(token, message));
            return;
        }
        if (token.getResource().isFile()) {
            System.err.println("file://" + token.getResource().getUri().getPath() + ":" + token.getPositionDisplay() + " " + message);
        }
        throw new ParserException(message + "\n"
                + token.getPositionDisplay()
                + " " + token + "\nparser state: " + this);
    }

    protected void error(NodeType... expected) {
        if (errorRecoveryEnabled) {
            errors.add(new SyntaxError(peekToken(), "expected: " + Arrays.asList(expected), expected[0]));
            return;
        }
        error("expected: " + Arrays.asList(expected));
    }

    protected void error(TokenType... expected) {
        if (errorRecoveryEnabled) {
            errors.add(new SyntaxError(peekToken(), "expected: " + Arrays.asList(expected)));
            return;
        }
        error("expected: " + Arrays.asList(expected));
    }

    // ========== Error Recovery Methods ==========

    /**
     * @return list of syntax errors collected during parsing
     */
    public List<SyntaxError> getErrors() {
        return errors;
    }

    /**
     * @return true if any syntax errors were recorded
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Skip tokens until we find a recovery point.
     * Includes infinite loop detection - if called from the same position twice,
     * forces skip of at least one token to guarantee progress.
     * @param recoveryTokens tokens that indicate a safe recovery point
     * @return true if a recovery token was found, false if EOF reached
     */
    protected boolean recoverTo(TokenType... recoveryTokens) {
        // Infinite loop safeguard: if recovering from same position, force progress
        if (position == lastRecoveryPosition) {
            if (peek() != EOF) {
                consumeNext();
            }
        }
        lastRecoveryPosition = position;
        Set<TokenType> recoverySet = Set.of(recoveryTokens);
        while (true) {
            TokenType current = peek();
            if (current == EOF || recoverySet.contains(current)) {
                return current != EOF;
            }
            consumeNext();
        }
    }

    /**
     * Consume token if present, or record error if in recovery mode.
     * @param token the expected token type
     * @return true if consumed successfully or in recovery mode
     */
    protected boolean consumeSoft(TokenType token) {
        if (consumeIf(token)) {
            return true;
        }
        error(token);
        return errorRecoveryEnabled; // Continue if recovering, never reached otherwise
    }

    /**
     * Exit that tolerates incomplete nodes.
     * Even if the node is incomplete, it is added to the parent.
     */
    protected boolean exitSoft() {
        return exit(true, false, Shift.NONE);
    }

    // ========== End Error Recovery Methods ==========

    protected void enter(NodeType type) {
        if (stackPointer >= MAX_DEPTH) {
            throw new ParserException("too much recursion");
        }
        positionStack[stackPointer] = position;
        nodeStack[stackPointer] = new Node(type);
        stackPointer++;
    }

    // Single-token overload - avoids array allocation
    protected boolean enter(NodeType type, TokenType token) {
        if (peek() != token) {
            return false;
        }
        if (stackPointer >= MAX_DEPTH) {
            throw new ParserException("too much recursion");
        }
        positionStack[stackPointer] = position;
        nodeStack[stackPointer] = new Node(type);
        stackPointer++;
        consumeNext();
        return true;
    }

    // Array overload - use with pre-allocated static arrays
    protected boolean enter(NodeType type, TokenType[] tokens) {
        return enterIf(type, tokens);
    }

    protected boolean enterIf(NodeType type, TokenType[] tokens) {
        if (tokens != null) {
            if (!peekAnyOf(tokens)) {
                return false;
            }
        }
        if (stackPointer >= MAX_DEPTH) {
            throw new ParserException("too much recursion");
        }
        positionStack[stackPointer] = position;
        nodeStack[stackPointer] = new Node(type);
        stackPointer++;
        if (tokens != null) {
            consumeNext();
        }
        return true;
    }

    // EnumSet overload - O(1) lookup via bitmask
    protected boolean enter(NodeType type, java.util.EnumSet<TokenType> tokens) {
        return enterIf(type, tokens);
    }

    protected boolean enterIf(NodeType type, java.util.EnumSet<TokenType> tokens) {
        if (!peekAnyOf(tokens)) {
            return false;
        }
        if (stackPointer >= MAX_DEPTH) {
            throw new ParserException("too much recursion");
        }
        positionStack[stackPointer] = position;
        nodeStack[stackPointer] = new Node(type);
        stackPointer++;
        consumeNext();
        return true;
    }

    protected boolean exit() {
        return exit(true, false, Shift.NONE);
    }

    protected boolean exit(boolean result, boolean mandatory) {
        return exit(result, mandatory, Shift.NONE);
    }

    protected void exit(Shift shift) {
        exit(true, false, shift);
    }

    private boolean exit(boolean result, boolean mandatory, Shift shift) {
        Node node = nodeStack[stackPointer - 1];
        if (mandatory && !result) {
            error(node.type);
        }
        if (result) {
            Node parent = nodeStack[stackPointer - 2];
            switch (shift) {
                case LEFT:
                    Node prev = parent.removeFirst(); // remove previous sibling
                    node.addFirst(prev); // and make it the first child
                    parent.add(node);
                    break;
                case NONE:
                    parent.add(node);
                    break;
                case RIGHT:
                    Node prevSibling = parent.removeFirst(); // remove previous sibling
                    if (prevSibling.type == node.type) {
                        Node newNode = new Node(node.type);
                        parent.add(newNode);
                        newNode.add(prevSibling.get(0)); // prev lhs
                        newNode.add(prevSibling.get(1)); // operator
                        Node newRhs = new Node(node.type);
                        newNode.add(newRhs);
                        newRhs.add(prevSibling.get(2)); // prev rhs becomes current lhs
                        newRhs.add(node.get(0)); // operator
                        newRhs.add(node.get(1)); // current rhs
                    } else {
                        node.addFirst(prevSibling); // move previous sibling to first child
                        parent.add(node);
                    }
            }
        } else {
            position = positionStack[stackPointer - 1];
        }
        // Pop the stack (clear reference to help GC)
        stackPointer--;
        nodeStack[stackPointer] = null;
        return result;
    }

    private Token cachedPeek = Token.EMPTY;
    private int cachedPeekPos = -1;

    protected TokenType peek() {
        return peekToken().type;
    }

    protected Token peekToken() {
        if (cachedPeekPos != position) {
            cachedPeekPos = position;
            cachedPeek = (position == size) ? Token.EMPTY : tokens.get(position);
        }
        return cachedPeek;
    }

    protected void consume(TokenType token) {
        if (!consumeIf(token)) {
            error(token);
        }
    }

    // Array overload - use with pre-allocated static arrays to avoid allocation
    protected boolean anyOf(TokenType[] tokens) {
        for (TokenType token : tokens) {
            if (consumeIf(token)) {
                return true;
            }
        }
        return false;
    }

    // EnumSet overload - O(1) check then consume
    protected boolean anyOf(java.util.EnumSet<TokenType> tokens) {
        if (tokens.contains(peek())) {
            consumeNext();
            return true;
        }
        return false;
    }

    protected boolean consumeIf(TokenType token) {
        if (peekIf(token)) {
            consumeNext();
            return true;
        }
        return false;
    }

    protected boolean peekIf(TokenType token) {
        return peek() == token;
    }

    // Array overload - use with pre-allocated static arrays to avoid allocation
    protected boolean peekAnyOf(TokenType[] tokens) {
        TokenType current = peek();
        for (TokenType token : tokens) {
            if (current == token) {
                return true;
            }
        }
        return false;
    }

    // EnumSet overload - O(1) lookup via bitmask
    protected boolean peekAnyOf(java.util.EnumSet<TokenType> tokens) {
        return tokens.contains(peek());
    }

    protected TokenType lastConsumed() {
        return nodeStack[stackPointer - 1].getLast().token.type;
    }

    protected int getPosition() {
        return position;
    }

    protected void consumeNext() {
        nodeStack[stackPointer - 1].add(new Node(next()));
    }

    protected Token next() {
        return position == size ? Token.EMPTY : tokens.get(position++);
    }

}
