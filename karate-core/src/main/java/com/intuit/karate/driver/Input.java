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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class Input {

    protected boolean control;
    protected boolean alt;
    protected boolean shift;
    protected boolean meta;

    protected boolean release;

    private int pos = 0;

    public final char[] chars;

    public Input(String chars) {
        this.chars = chars.toCharArray();
    }

    public boolean hasNext() {
        return pos < chars.length;
    }

    public List<Integer> getKeyCodesToRelease() {
        if (control || alt || shift || meta) {
            List<Integer> list = new ArrayList();
            if (shift) {
                list.add(Keys.CODE_SHIFT);
            }
            if (control) {
                list.add(Keys.CODE_CONTROL);
            }
            if (alt) {
                list.add(Keys.CODE_ALT);
            }
            if (meta) {
                list.add(Keys.CODE_META);
            }
            return list;
        } else {
            return Collections.emptyList();
        }
    }

    private void updateModifiers(char c) {
        switch (c) {
            case Keys.CONTROL:
                release = control;
                control = !control;
                break;
            case Keys.ALT:
                release = alt;
                alt = !alt;
                break;
            case Keys.SHIFT:
                release = shift;
                shift = !shift;
                break;
            case Keys.META:
                release = meta;
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
