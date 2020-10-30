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
public class JsonNumber extends AbstractJsonObject {
    private float minimum;
    private boolean minimumSet = false;
    private boolean minExclu;
    private boolean minExcluSet = false;
    private float maximum;
    private boolean maximumSet = false;
    private boolean maxExclu;
    private boolean maxExcluSet = false;

    public JsonNumber() {
    }

    public JsonNumber(String name) {
        super(name);
    }

    @Override
    public String generateValue() {
        float lowBound = Float.MIN_VALUE;
        float maxBound = Float.MAX_VALUE;
        if (this.isMinimumSet()) {
            lowBound = this.getMinimum();
            if (this.isMinExcluSet() && this.isMinExclu()) {
                lowBound = this.getMinimum() + 1;
            }
        }
        if (this.isMaximumSet()) {
            maxBound = this.getMaximum();
            if (this.isMaxExcluSet() && !this.isMaxExclu()) {
                maxBound = this.getMaximum() + 1;
            }
        }
        float aFloat = ThreadLocalRandom.current().nextInt(Math.round(lowBound), Math.round(maxBound)) + ThreadLocalRandom.current().nextFloat();

        if (this.isMainObject()) {
            return String.format(JSON_MAIN_PATTERN, aFloat);
        } else {
            return String.format(JSON_PROPERTY_PATTERN, getName(), aFloat);
        }
    }

    public float getMinimum() {
        return minimum;
    }

    public void setMinimum(float minimum) {
        this.minimum = minimum;
        this.minimumSet = true;
    }

    public boolean isMinimumSet() {
        return minimumSet;
    }

    public boolean isMinExclu() {
        return minExclu;
    }

    public void setMinExclu(boolean minExclu) {
        this.minExclu = minExclu;
        this.minExcluSet = true;
    }

    protected boolean isMinExcluSet() {
        return minExcluSet;
    }

    public float getMaximum() {
        return maximum;
    }

    public void setMaximum(float maximum) {
        this.maximum = maximum;
        this.maximumSet = true;
    }

    public boolean isMaximumSet() {
        return maximumSet;
    }

    public boolean isMaxExclu() {
        return maxExclu;
    }

    public void setMaxExclu(boolean maxExclu) {
        this.maxExclu = maxExclu;
        this.maxExcluSet = true;
    }

    protected boolean isMaxExcluSet() {
        return maxExcluSet;
    }
}
