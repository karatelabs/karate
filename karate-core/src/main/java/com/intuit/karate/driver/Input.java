/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.driver;

/**
 *
 * @author pthomas3
 */
public class Input {

    protected boolean control;
    protected boolean alt;
    protected boolean shift;
    protected boolean meta;

    private int pos = 0;

    public final char[] chars;

    public Input(String chars) {
        this.chars = chars.toCharArray();
    }

    public boolean hasNext() {
        return pos < chars.length;
    }

    private void updateModifiers(char c) {
        switch (c) {
            case Keys.CONTROL:
                control = !control;
                break;
            case Keys.ALT:
                alt = !alt;
                break;
            case Keys.SHIFT:
                shift = !shift;
                break;
            case Keys.META:
                meta = !meta;
                break;
            default:
                break;
        }
    }

    public char next() {
        char c = chars[pos++];
        updateModifiers(c);
        return c;
    }

    public int getModifierFlags() {
        int modifier = 0;
        if (control) {
            modifier += 2;
        }
        if (alt) {
            modifier += 1;
        }
        if (shift) {
            modifier += 8;
        }
        if (meta) {
            modifier += 4;
        }
        return modifier;
    }

}
