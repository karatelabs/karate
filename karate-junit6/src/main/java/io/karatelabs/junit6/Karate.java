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
package io.karatelabs.junit6;

import io.karatelabs.core.Runner;
import io.karatelabs.http.HttpClientFactory;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

/**
 * Fluent API for running Karate tests with JUnit 6.
 * <p>
 * Provides streaming dynamic test generation where tests appear in the JUnit tree
 * as scenarios execute, enabling true late-binding test discovery for features
 * that generate scenarios dynamically.
 * <p>
 * Example usage with {@code @TestFactory}:
 * <pre>
 * class ApiTests {
 *     &#64;TestFactory
 *     Stream&lt;DynamicNode&gt; karateTests() {
 *         return Karate.run("classpath:features/")
 *             .tags("@smoke")
 *             .threads(4)
 *             .stream();
 *     }
 * }
 * </pre>
 * <p>
 * Or using the convenience annotation (note: return type must be {@code Iterable<DynamicNode>}
 * for JUnit 6 static validation):
 * <pre>
 * class ApiTests {
 *     &#64;Karate.Test
 *     Iterable&lt;DynamicNode&gt; testAll() {
 *         return Karate.run("sample").relativeTo(getClass());
 *     }
 * }
 * </pre>
 */
public class Karate implements Iterable<DynamicNode> {

    /**
     * Marks a method as a Karate test factory.
     * <p>
     * Methods annotated with {@code @Karate.Test} should return a {@link Karate} instance.
     * The method will be recognized as a JUnit 6 {@link TestFactory}.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @TestFactory
    public @interface Test {
    }

    private final Runner.Builder delegate;
    private final List<String> paths = new ArrayList<>();
    private String relativeTo;
    private boolean hierarchical = true;
    private int threads = 1;
    private long timeoutMinutes = 30;

    private Karate() {
        this.delegate = Runner.builder();
    }

    /**
     * Creates a new Karate instance configured to run the specified paths.
     *
     * @param paths feature file or directory paths
     * @return a new Karate instance
     */
    public static Karate run(String... paths) {
        Karate karate = new Karate();
        for (String path : paths) {
            karate.paths.add(path);
        }
        return karate;
    }

    /**
     * Set paths relative to the given class.
     * <p>
     * This enables discovering feature files in the same package as the test class.
     * If no paths were specified, all .feature files in the class's package will be discovered.
     *
     * @param clazz the class to resolve paths relative to
     * @return this instance for chaining
     */
    public Karate relativeTo(Class<?> clazz) {
        this.relativeTo = "classpath:" + clazz.getPackage().getName().replace('.', '/');
        return this;
    }

    /**
     * Set tag filter expressions.
     * <p>
     * Examples: "@smoke", "~@slow", "@smoke,@fast"
     *
     * @param tagExpressions tag expressions
     * @return this instance for chaining
     */
    public Karate tags(String... tagExpressions) {
        delegate.tags(tagExpressions);
        return this;
    }

    /**
     * Set the karate environment (karate.env).
     *
     * @param env environment name
     * @return this instance for chaining
     */
    public Karate karateEnv(String env) {
        delegate.karateEnv(env);
        return this;
    }

    /**
     * Set the number of threads for parallel execution.
     *
     * @param count thread count (1 for sequential)
     * @return this instance for chaining
     */
    public Karate threads(int count) {
        this.threads = Math.max(1, count);
        return this;
    }

    /**
     * Enable or disable hierarchical test structure.
     * <p>
     * When enabled (default), features appear as containers with scenarios as children.
     * When disabled, all scenarios appear at the root level.
     *
     * @param enabled true for hierarchical structure
     * @return this instance for chaining
     */
    public Karate hierarchical(boolean enabled) {
        this.hierarchical = enabled;
        return this;
    }

    /**
     * Set the timeout for waiting for test events.
     *
     * @param minutes timeout in minutes
     * @return this instance for chaining
     */
    public Karate timeoutMinutes(long minutes) {
        this.timeoutMinutes = minutes;
        return this;
    }

    /**
     * Set a system property that will be available via karate.properties in scripts.
     *
     * @param key   property key
     * @param value property value
     * @return this instance for chaining
     */
    public Karate systemProperty(String key, String value) {
        delegate.systemProperty(key, value);
        return this;
    }

    /**
     * Set the directory containing karate-config.js.
     *
     * @param dir config directory path
     * @return this instance for chaining
     */
    public Karate configDir(String dir) {
        delegate.configDir(dir);
        return this;
    }

    /**
     * Set the output directory for reports.
     *
     * @param dir output directory path
     * @return this instance for chaining
     */
    public Karate outputDir(String dir) {
        delegate.outputDir(dir);
        return this;
    }

    /**
     * Enable or disable HTML report generation.
     *
     * @param enabled true to generate HTML reports
     * @return this instance for chaining
     */
    public Karate outputHtmlReport(boolean enabled) {
        delegate.outputHtmlReport(enabled);
        return this;
    }

    /**
     * Filter by scenario name (regex supported).
     *
     * @param name scenario name pattern
     * @return this instance for chaining
     */
    public Karate scenarioName(String name) {
        delegate.scenarioName(name);
        return this;
    }

    /**
     * Enable dry run mode (parse without executing).
     *
     * @param enabled true for dry run
     * @return this instance for chaining
     */
    public Karate dryRun(boolean enabled) {
        delegate.dryRun(enabled);
        return this;
    }

    /**
     * Set a custom HTTP client factory.
     *
     * @param factory the HTTP client factory
     * @return this instance for chaining
     */
    public Karate httpClientFactory(HttpClientFactory factory) {
        delegate.httpClientFactory(factory);
        return this;
    }

    /**
     * Returns a stream of dynamic test nodes for use with {@code @TestFactory}.
     * <p>
     * This method starts Karate execution asynchronously and returns a stream that
     * yields tests as scenarios complete. The stream blocks while waiting for
     * the next scenario to finish.
     *
     * @return a stream of dynamic test nodes
     */
    public Stream<DynamicNode> stream() {
        // Resolve paths with relativeTo prefix
        List<String> resolvedPaths = resolvePaths();
        for (String path : resolvedPaths) {
            delegate.path(path);
        }

        // Create the event queue and bridge listener
        LinkedBlockingQueue<TestEvent> queue = new LinkedBlockingQueue<>();
        JUnitBridgeListener bridge = new JUnitBridgeListener(queue, hierarchical);
        delegate.resultListener(bridge);

        // Disable console summary since we're in JUnit mode
        delegate.outputConsoleSummary(false);

        // Start Karate execution in background thread
        CompletableFuture.runAsync(() -> delegate.parallel(threads));

        // Return streaming iterator
        return new StreamingTestIterator(queue, hierarchical, timeoutMinutes).stream();
    }

    /**
     * Implements {@link Iterable} for use with the {@link Karate.Test} annotation.
     * <p>
     * This allows the Karate instance to be returned directly from a method
     * annotated with {@code @Karate.Test}, and JUnit will iterate over the tests.
     */
    @Override
    public Iterator<DynamicNode> iterator() {
        return stream().iterator();
    }

    private List<String> resolvePaths() {
        if (paths.isEmpty() && relativeTo != null) {
            // No paths specified, discover all features in the relative package
            return List.of(relativeTo);
        }
        if (relativeTo == null) {
            return paths;
        }
        // Prefix paths with relativeTo
        List<String> resolved = new ArrayList<>();
        for (String path : paths) {
            if (path.startsWith("classpath:")) {
                resolved.add(path);
            } else {
                String suffix = path.endsWith(".feature") ? path : path + ".feature";
                resolved.add(relativeTo + "/" + suffix);
            }
        }
        return resolved;
    }

    @Override
    public String toString() {
        return "Karate{paths=" + paths + ", relativeTo=" + relativeTo + ", threads=" + threads + "}";
    }

}
