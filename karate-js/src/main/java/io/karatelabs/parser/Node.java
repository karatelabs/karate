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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A node in a parsed tree.
 *
 * <p><b>Parsed trees are shared across threads, so treat a Node as read-only once parsing has
 * returned.</b> A {@code Suite} parses {@code karate-config.js} once and every scenario — running
 * in parallel — evaluates that one AST, and the interpreter only ever reads: no {@code node.x = }
 * assignment exists on any evaluation path. The mutators below ({@link #add}, {@link #addFirst},
 * {@link #removeFirst}) belong to tree construction, and the safety of the sharing rests on nobody
 * calling them afterwards. That is a convention, not an invariant this class enforces.</p>
 *
 * <p>One field already bends it: {@link #getText()} caches into a non-volatile {@code cachedText}
 * on first ask, which under sharing is a data race. It is benign — every racing thread computes
 * the same value, and a {@code String}'s final fields make it safe to publish that way — but it is
 * the reason "a Node is read-only once parsed" is a description of intent rather than a fact.
 * <b>A lazily assigned field of any other type would not be benign.</b> An array or a mutable
 * object published by a plain write can be seen non-null with its contents not yet visible; if you
 * add one here, make it {@code volatile} and read it once into a local.</p>
 */
public class Node {

    static final Logger logger = LoggerFactory.getLogger(Node.class);

    private static final int INITIAL_CAPACITY = 4;
    private static final Node[] EMPTY_ARRAY = new Node[0];

    public final NodeType type;
    public final Token token;
    /**
     * The {@code async} modifier, for the node types that can carry one — {@code FN_EXPR}
     * (a declaration / expression / object-literal method / class method) and
     * {@code FN_ARROW_EXPR}. It is a field rather than a child token because {@code async}
     * is a contextual keyword that the lexer emits as a plain {@code IDENT}: an extra
     * leading child would shift every positional lookup the interpreter does on those two
     * node types, and a synthesized method has no token of its own to carry the marker.
     * Written by the parser during tree construction only — same read-only-once-parsed
     * convention as the children, and a {@code boolean} written before publication is safe
     * to share (see the class javadoc).
     */
    public boolean async;
    // Raw array storage - null for TOKEN nodes, allocated on first child
    private Node[] children;
    private short childCount;
    // Cached text for getText() - avoids repeated StringBuilder operations
    private String cachedText;

    // Slot-frame annotations, assigned lazily by the JS engine's per-function
    // symbol analysis (io.karatelabs.js.SlotTable) — the same benign-race
    // contract as cachedText: every racing thread computes the same value.
    // `slot` is a short so it packs into the alignment gap next to childCount
    // — parse allocation is hot enough that an 8-byte-per-node growth showed
    // up on the top-level benchmark rows. A stale -1 read only means the
    // slower name-keyed path is taken. `meta` holds a mutable object, so per
    // the class-doc rule it is volatile and must be read once into a local.
    public short slot = -1;
    public volatile Object meta;

    private Node parent;

    // Ensure capacity for adding children
    private void ensureCapacity(int minCapacity) {
        if (children == null) {
            children = new Node[Math.max(INITIAL_CAPACITY, minCapacity)];
        } else if (minCapacity > children.length) {
            int newCapacity = Math.max(children.length * 2, minCapacity);
            children = Arrays.copyOf(children, newCapacity);
        }
    }

    public Node(NodeType type) {
        this.type = type;
        this.token = Token.EMPTY;
        if (type.expectedChildren > 0) {
            this.children = new Node[type.expectedChildren];
        }
    }

    public Node(Token token) {
        this.token = token;
        type = NodeType.TOKEN;
    }

    public Node getParent() {
        return parent;
    }

    public boolean isToken() {
        return type == NodeType.TOKEN;
    }

    public boolean isEof() {
        return type == NodeType.TOKEN && token.type == TokenType.EOF;
    }

    public Token getFirstToken() {
        if (isToken()) {
            return token;
        }
        if (childCount == 0) {
            return Token.EMPTY;
        }
        return children[0].getFirstToken();
    }

    public Token getLastToken() {
        if (isToken()) {
            return token;
        }
        if (childCount == 0) {
            return Token.EMPTY;
        }
        return children[childCount - 1].getLastToken();
    }

    public String toStringError(String message) {
        Token first = getFirstToken();
        if (first.getResource().isFile()) {
            return message + "\n    at " + first.getResource().getRelativePath() + ":" + first.getPositionDisplay();
        } else if (first.line == 0) {
            return message;
        } else {
            return message + "\n    at " + first.getPositionDisplay();
        }
    }

    @Override
    public String toString() {
        if (isToken()) {
            return token.getText();
        }
        return "[" + type + "] " + getTextIncludingWhitespace();
    }

    public Node findFirstChild(NodeType type) {
        for (int i = 0; i < childCount; i++) {
            Node child = children[i];
            if (child.type == type) {
                return child;
            }
            Node temp = child.findFirstChild(type);
            if (temp != null) {
                return temp;
            }
        }
        return null;
    }

    public List<Node> findAll(NodeType type) {
        List<Node> results = new ArrayList<>();
        findAll(type, results);
        return results;
    }

    private void findAll(NodeType type, List<Node> results) {
        for (int i = 0; i < childCount; i++) {
            Node child = children[i];
            if (child.type == type) {
                results.add(child);
            }
            child.findAll(type, results);
        }
    }

    public Node findFirstChild(TokenType token) {
        for (int i = 0; i < childCount; i++) {
            Node child = children[i];
            if (child.token.type == token) {
                return child;
            }
            Node temp = child.findFirstChild(token);
            if (temp != null) {
                return temp;
            }
        }
        return null;
    }

    public Node findParent(NodeType type) {
        Node temp = this.parent;
        while (temp != null && temp.type != type) {
            temp = temp.parent;
        }
        return temp;
    }

    public List<Node> findImmediateChildren(NodeType type) {
        List<Node> results = new ArrayList<>();
        for (int i = 0; i < childCount; i++) {
            Node child = children[i];
            if (child.type == type) {
                results.add(child);
            }
        }
        return results;
    }

    public List<Node> findChildren(TokenType token) {
        List<Node> results = new ArrayList<>();
        findChildren(token, results);
        return results;
    }

    private void findChildren(TokenType token, List<Node> results) {
        for (int i = 0; i < childCount; i++) {
            Node child = children[i];
            if (!child.isToken()) {
                child.findChildren(token, results);
            } else if (child.token.type == token) {
                results.add(child);
            }
        }
    }

    public String getText() {
        if (cachedText != null) {
            return cachedText;
        }
        if (isToken()) {
            cachedText = token.getText();
            return cachedText;
        }
        if (childCount == 1) {
            cachedText = children[0].getText();
            return cachedText;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < childCount; i++) {
            sb.append(children[i].getText());
        }
        cachedText = sb.toString();
        return cachedText;
    }

    public String getTextIncludingWhitespace() {
        if (isToken()) {
            return token.getText();
        }
        int start = getFirstToken().pos;
        Token last = getLastToken();
        int end = last.pos + last.length;
        return last.getResource().getText().substring(start, end);
    }

    public Node removeFirst() {
        if (childCount == 0) {
            throw new NoSuchElementException();
        }
        Node first = children[0];
        System.arraycopy(children, 1, children, 0, childCount - 1);
        children[--childCount] = null; // help GC
        return first;
    }

    public void addFirst(Node child) {
        child.parent = this;
        ensureCapacity(childCount + 1);
        System.arraycopy(children, 0, children, 1, childCount);
        children[0] = child;
        childCount++;
    }

    public void add(Node child) {
        child.parent = this;
        ensureCapacity(childCount + 1);
        children[childCount++] = child;
    }

    public Node getFirst() {
        if (childCount == 0) {
            throw new NoSuchElementException();
        }
        return children[0];
    }

    public Node getLast() {
        if (childCount == 0) {
            throw new NoSuchElementException();
        }
        return children[childCount - 1];
    }

    public Node get(int index) {
        if (index < 0 || index >= childCount) {
            throw new IndexOutOfBoundsException(index);
        }
        return children[index];
    }

    public int size() {
        return childCount;
    }

    public boolean isEmpty() {
        return childCount == 0;
    }

}
