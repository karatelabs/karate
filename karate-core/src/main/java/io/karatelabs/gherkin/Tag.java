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

import io.karatelabs.common.StringUtils;

import java.util.Collections;
import java.util.List;

public class Tag {

    public static final String IGNORE = "ignore";
    public static final String ENV = "env";
    public static final String ENVNOT = "envnot";
    public static final String SETUP = "setup";
    public static final String FAIL = "fail";
    public static final String LOCK = "lock";

    private final int line;
    private final String text;
    private final String name;
    private final List<String> values;

    public Tag(int line, String text) {
        this.line = line;
        this.text = text.substring(1);
        int pos = text.indexOf('=');
        if (pos != -1) {
            name = text.substring(1, pos);
            String temp = text.substring(pos + 1);
            if (temp.isEmpty()) { // edge case '@id='
                values = Collections.emptyList();
            } else {
                values = StringUtils.split(temp, ',', false);
            }
        } else {
            name = this.text;
            values = Collections.emptyList();
        }
    }

    public int getLine() {
        return line;
    }

    public String getText() {
        return text;
    }

    public String getName() {
        return name;
    }

    public List<String> getValues() {
        return values;
    }

    @Override
    public String toString() {
        return '@' + text;
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Tag other = (Tag) obj;
        return text.equals(other.text);
    }

}
