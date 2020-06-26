/*
 * Copyright (c) 2012, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.jersey.karate.uri.internal;

import java.util.NoSuchElementException;

// Source: https://github.com/eclipse-ee4j/jersey/blob/master/core-common/src/main/java/org/glassfish/jersey/uri/internal/CharacterIterator.java @ 12a0573
/**
 * Iterator which iterates through the input string and returns characters from that string.
 *
 * @author Miroslav Fuksa
 */
final class CharacterIterator {
    private int pos;
    private String s;

    /**
     * Creates a new iterator initialized with the given input string.
     *
     * @param s String trough which the iterator iterates.
     */
    public CharacterIterator(final String s) {
        this.s = s;
        this.pos = -1;
    }

    /**
     * Determines whether there is next character in the iteration chain.
     *
     * @return True if there is a character which can be retrieved by {@link #next()}, false otherwise.
     */
    public boolean hasNext() {
        return pos < s.length() - 1;
    }

    /**
     * Returns next character in the iteration chain and increase the current position.
     *
     * @return Next character.
     * @throws RuntimeException The method might throw exception when there is no more character to be retrieved.
     */
    public char next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return s.charAt(++pos);
    }

    /**
     * Returns the next character without increasing the position. The method does the same as {@link #next()} but
     * the position is not changed by calling this method.
     *
     * @return Next character.
     */
    public char peek() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        return s.charAt(pos + 1);
    }


    /**
     * Returns the current internal position of the iterator.
     *
     * @return current position of the iterator
     */
    public int pos() {
        return pos;
    }

    /**
     * Returns the input String on which this {@link CharacterIterator iterator} operates.
     *
     * @return String which initialized this iterator.
     */
    public String getInput() {
        return s;
    }

    /**
     * Changes the current position to the position.
     *
     * @param newPosition New position for the iterator.
     */
    public void setPosition(int newPosition) {
        if (newPosition > this.s.length() - 1) {
            throw new IndexOutOfBoundsException("Given position " + newPosition + " is outside the input string range.");
        }
        this.pos = newPosition;
    }

    /**
     * Returns character at the current position.
     *
     * @return Character from current position.
     */
    public char current() {
        if (pos == -1) {
            throw new IllegalStateException("Iterator not used yet.");
        }
        return s.charAt(pos);
    }
}
