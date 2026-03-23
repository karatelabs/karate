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
package com.intuit.karate.junit5;

import io.karatelabs.http.HttpClientFactory;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * V1 compatibility shim for {@link io.karatelabs.junit6.Karate}.
 * <p>
 * This class provides backward compatibility for code written against Karate v1.
 * Migrate to {@link io.karatelabs.junit6.Karate} for new code.
 *
 * @deprecated Use {@link io.karatelabs.junit6.Karate} instead.
 *             This class will be removed in a future release.
 */
@Deprecated(since = "2.0", forRemoval = true)
public class Karate implements Iterable<DynamicNode> {

    /**
     * Marks a method as a Karate test factory.
     *
     * @deprecated Use {@link io.karatelabs.junit6.Karate.Test} instead.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @TestFactory
    @Deprecated(since = "2.0", forRemoval = true)
    public @interface Test {
    }

    private final io.karatelabs.junit6.Karate delegate;

    private Karate(io.karatelabs.junit6.Karate delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates a new Karate instance configured to run the specified paths.
     *
     * @deprecated Use {@link io.karatelabs.junit6.Karate#run(String...)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static Karate run(String... paths) {
        return new Karate(io.karatelabs.junit6.Karate.run(paths));
    }

    /**
     * Set paths relative to the given class.
     *
     * @deprecated Use {@link io.karatelabs.junit6.Karate#relativeTo(Class)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Karate relativeTo(Class<?> clazz) {
        delegate.relativeTo(clazz);
        return this;
    }

    /**
     * Set tag filter expressions.
     *
     * @deprecated Use {@link io.karatelabs.junit6.Karate#tags(String...)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Karate tags(String... tagExpressions) {
        delegate.tags(tagExpressions);
        return this;
    }

    /**
     * Set the karate environment.
     *
     * @deprecated Use {@link io.karatelabs.junit6.Karate#karateEnv(String)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Karate karateEnv(String env) {
        delegate.karateEnv(env);
        return this;
    }

    /**
     * Set a system property.
     *
     * @deprecated Use {@link io.karatelabs.junit6.Karate#systemProperty(String, String)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Karate systemProperty(String key, String value) {
        delegate.systemProperty(key, value);
        return this;
    }

    /**
     * Set the config directory.
     *
     * @deprecated Use {@link io.karatelabs.junit6.Karate#configDir(String)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Karate configDir(String dir) {
        delegate.configDir(dir);
        return this;
    }

    /**
     * Set the output directory.
     *
     * @deprecated Use {@link io.karatelabs.junit6.Karate#outputDir(String)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Karate outputDir(String dir) {
        delegate.outputDir(dir);
        return this;
    }

    /**
     * Enable or disable HTML report.
     *
     * @deprecated Use {@link io.karatelabs.junit6.Karate#outputHtmlReport(boolean)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Karate outputHtmlReport(boolean enabled) {
        delegate.outputHtmlReport(enabled);
        return this;
    }

    /**
     * Filter by scenario name.
     *
     * @deprecated Use {@link io.karatelabs.junit6.Karate#scenarioName(String)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Karate scenarioName(String name) {
        delegate.scenarioName(name);
        return this;
    }

    /**
     * Enable dry run mode.
     *
     * @deprecated Use {@link io.karatelabs.junit6.Karate#dryRun(boolean)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Karate dryRun(boolean enabled) {
        delegate.dryRun(enabled);
        return this;
    }

    /**
     * Set a custom HTTP client factory.
     *
     * @deprecated Use {@link io.karatelabs.junit6.Karate#httpClientFactory(HttpClientFactory)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Karate clientFactory(HttpClientFactory factory) {
        delegate.httpClientFactory(factory);
        return this;
    }

    /**
     * Returns a stream of dynamic test nodes.
     *
     * @deprecated Use {@link io.karatelabs.junit6.Karate#stream()} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public Stream<DynamicNode> stream() {
        return delegate.stream();
    }

    @Override
    public Iterator<DynamicNode> iterator() {
        return delegate.iterator();
    }

    /**
     * Get the underlying v2 Karate instance for gradual migration.
     */
    public io.karatelabs.junit6.Karate toV2() {
        return delegate;
    }

}
