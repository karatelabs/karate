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
package io.karatelabs.gatling;

import io.gatling.javaapi.core.ActionBuilder;
import scala.collection.immutable.Seq;
import scala.jdk.javaapi.CollectionConverters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builder for creating Karate feature execution actions.
 * Provides a fluent API for configuring a feature path, tags, and silent mode.
 *
 * <p>Example usage:
 * <pre>
 * // Basic usage
 * karateFeature("classpath:features/cats.feature")
 *
 * // With tag selector (positional, matches v1 API)
 * karateFeature("classpath:features/cats.feature", "@smoke")
 *
 * // With multiple tag selectors (AND-ed)
 * karateFeature("classpath:features/cats.feature", "@smoke", "~@slow")
 *
 * // Silent mode for warm-up
 * karateFeature("classpath:features/cats.feature").silent()
 * </pre>
 *
 * <p>To run multiple features in sequence, chain {@code .exec(...)} calls
 * on the Gatling scenario rather than passing multiple paths here.
 */
public final class KarateFeatureBuilder implements ActionBuilder {

    private final String featurePath;
    private final List<String> tags = new ArrayList<>();
    private boolean silent = false;
    private KarateProtocol protocol;

    /**
     * Create a builder for the given feature path with optional tag selectors.
     *
     * @param path feature file path (e.g., "classpath:features/cats.feature")
     * @param tags tag selector expressions (e.g., "@smoke", "~@slow") — varargs;
     *             multiple values are AND-ed together, commas within a value are OR
     */
    public KarateFeatureBuilder(String path, String... tags) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("feature path is required");
        }
        this.featurePath = path;
        if (tags != null) {
            Collections.addAll(this.tags, tags);
        }
    }

    /**
     * Add tag filter expressions. Equivalent to passing tags to the constructor;
     * additional calls append to the existing tag list (all AND-ed together).
     *
     * @param tagExpressions tag expressions like "@smoke", "~@slow"
     * @return this builder
     */
    public KarateFeatureBuilder tags(String... tagExpressions) {
        if (tagExpressions != null) {
            Collections.addAll(tags, tagExpressions);
        }
        return this;
    }

    /**
     * Enable silent mode.
     * In silent mode, results are not reported to Gatling's StatsEngine.
     * Use this for warm-up scenarios.
     *
     * @return this builder
     */
    public KarateFeatureBuilder silent() {
        this.silent = true;
        return this;
    }

    /**
     * Set the protocol for URI pattern matching and name resolution.
     * This is typically called internally by the DSL.
     *
     * @param protocol the KarateProtocol
     * @return this builder
     */
    public KarateFeatureBuilder protocol(KarateProtocol protocol) {
        this.protocol = protocol;
        return this;
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        Seq<String> scalaTags = CollectionConverters.asScala(tags).toSeq();
        return new KarateScalaActionBuilder(featurePath, scalaTags, protocol, silent);
    }

}
