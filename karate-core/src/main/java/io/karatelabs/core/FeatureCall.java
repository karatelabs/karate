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
package io.karatelabs.core;

import io.karatelabs.gherkin.Feature;

/**
 * Wrapper for a Feature with an optional tag selector.
 * Used for call-by-tag syntax like read('file.feature@tag').
 */
public class FeatureCall {

    private final Feature feature;
    private final String tagSelector;

    public FeatureCall(Feature feature, String tagSelector) {
        this.feature = feature;
        this.tagSelector = tagSelector;
    }

    public Feature getFeature() {
        return feature;
    }

    public String getTagSelector() {
        return tagSelector;
    }

    /**
     * Returns true if this is a same-file tag call (feature is null).
     */
    public boolean isSameFile() {
        return feature == null;
    }

    @Override
    public String toString() {
        if (feature == null) {
            return tagSelector;
        }
        return feature.toString() + (tagSelector != null ? tagSelector : "");
    }

}
