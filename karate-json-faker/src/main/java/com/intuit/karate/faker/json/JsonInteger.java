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
package com.intuit.karate.faker.json;

import java.util.concurrent.ThreadLocalRandom;

/**
 *
 * @author sixdouglas
 */
public class JsonInteger extends JsonNumber {

    private int multipleOf;
    private boolean multipleOfSet = false;

    public JsonInteger() {
    }

    public JsonInteger(String name) {
        super(name);
    }

    @Override
    public String generateValue() {
        int lowBound = Integer.MIN_VALUE;
        int maxBound = Integer.MAX_VALUE;
        if (this.isMinimumSet()) {
            lowBound = Math.round(this.getMinimum());
            if (this.isMinExcluSet() && this.isMinExclu()) {
                lowBound = Math.round(this.getMinimum()) + 1;
            }
        }
        if (this.isMaximumSet()) {
            maxBound = Math.round(this.getMaximum());
            if (this.isMaxExcluSet() && !this.isMaxExclu()) {
                maxBound = Math.round(this.getMaximum()) + 1;
            }
        }
        int anInt = ThreadLocalRandom.current().nextInt(lowBound, maxBound);
        if (this.isMultipleOfSet()) {
            while (anInt % this.getMultipleOf() != 0) {
                anInt = ThreadLocalRandom.current().nextInt(lowBound, maxBound);
            }
        }

        if (this.isMainObject()) {
            return String.format(JSON_MAIN_PATTERN, anInt);
        } else {
            return String.format(JSON_PROPERTY_PATTERN, getName(), anInt);
        }
    }

    public int getMultipleOf() {
        return multipleOf;
    }

    public void setMultipleOf(int multipleOf) {
        this.multipleOf = multipleOf;
        this.multipleOfSet = true;
    }

    public boolean isMultipleOfSet() {
        return multipleOfSet;
    }
}
