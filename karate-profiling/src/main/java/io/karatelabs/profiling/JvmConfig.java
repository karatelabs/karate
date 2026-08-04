/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.profiling;

import java.util.List;

/**
 * Heap size and collector for the forked child. Part of the experiment, not the
 * environment — see {@link Workload#jvm()}.
 *
 * @param xmx  value for {@code -Xmx}, e.g. "768m"
 * @param gc   which collector to select
 */
public record JvmConfig(String xmx, Gc gc) {

    public static JvmConfig defaults() {
        return new JvmConfig("768m", Gc.G1);
    }

    public JvmConfig withXmx(String value) {
        return value == null ? this : new JvmConfig(value, gc);
    }

    public JvmConfig withGc(Gc value) {
        return value == null ? this : new JvmConfig(xmx, value);
    }

    /**
     * G1 is the default because it is what most users actually run. ZGC exists to
     * reproduce environments that reported a problem under it.
     */
    public enum Gc {
        G1,
        ZGC;

        public static Gc parse(String s) {
            return switch (s.toLowerCase()) {
                case "g1" -> G1;
                case "zgc", "z" -> ZGC;
                default -> throw new IllegalArgumentException("unknown gc: " + s + " (expected g1 or zgc)");
            };
        }
    }

    /**
     * Collector flags for a given JDK feature version.
     *
     * <p>{@code -XX:+UseZGC} does not mean the same thing everywhere, and that matters
     * whenever a report names generational ZGC:
     * <ul>
     *   <li>JDK 21-22 — bare {@code UseZGC} is <em>non</em>-generational; generational
     *       needs {@code -XX:+ZGenerational}</li>
     *   <li>JDK 23 — generational is the default, {@code ZGenerational} is deprecated</li>
     *   <li>JDK 24+ — non-generational removed, the flag is gone</li>
     * </ul>
     * The expansion is echoed into {@code run-meta.txt} so a digest can be compared
     * against another honestly.
     */
    public List<String> flags(int jdkFeature) {
        if (gc == Gc.G1) {
            return List.of("-XX:+UseG1GC");
        }
        if (jdkFeature <= 22) {
            return List.of("-XX:+UseZGC", "-XX:+ZGenerational");
        }
        return List.of("-XX:+UseZGC");
    }

}
