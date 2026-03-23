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
import java.util.List;

/**
 * Builder for creating Karate feature execution actions.
 * Provides a fluent API for configuring feature paths, tags, and silent mode.
 *
 * <p>Example usage:
 * <pre>
 * // Basic usage
 * karateFeature("classpath:features/cats.feature")
 *
 * // With tags
 * karateFeature("classpath:features/cats.feature").tags("@smoke")
 *
 * // Silent mode for warm-up
 * karateFeature("classpath:features/cats.feature").silent()
 * </pre>
 */
public final class KarateFeatureBuilder implements ActionBuilder {

    private final List<String> featurePaths = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();
    private boolean silent = false;
    private KarateProtocol protocol;

    /**
     * Create a builder with the given feature paths.
     */
    public KarateFeatureBuilder(String... paths) {
        if (paths == null || paths.length == 0) {
            throw new IllegalArgumentException("At least one feature path is required");
        }
        for (String path : paths) {
            featurePaths.add(path);
        }
    }

    /**
     * Add tag filter expressions.
     * Multiple tags are ANDed together.
     *
     * @param tagExpressions tag expressions like "@smoke", "@api"
     * @return this builder
     */
    public KarateFeatureBuilder tags(String... tagExpressions) {
        for (String tag : tagExpressions) {
            tags.add(tag);
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
        // Convert Java lists to Scala Seqs
        Seq<String> scalaPaths = CollectionConverters.asScala(featurePaths).toSeq();
        Seq<String> scalaTags = CollectionConverters.asScala(tags).toSeq();

        // Create the Scala ActionBuilder that provides access to StatsEngine
        return new KarateScalaActionBuilder(scalaPaths, scalaTags, protocol, silent);
    }

}
