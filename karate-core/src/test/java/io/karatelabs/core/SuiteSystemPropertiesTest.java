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

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code karate.properties} is a live view rather than a copy of the JVM's property table, so
 * that {@code karate.sysprop('x')} and {@code karate.properties['x']} cost one lookup instead of
 * a map of every property in the JVM. These pin the semantics that had to survive that: the
 * merge order with {@code Runner.Builder.systemProperty}, live reads, and iteration.
 */
class SuiteSystemPropertiesTest {

    private static final String KEY = "__karate_suite_props_test__";

    private Suite suite(String... overrides) {
        Runner.Builder builder = Runner.builder();
        for (int i = 0; i < overrides.length; i += 2) {
            builder.systemProperty(overrides[i], overrides[i + 1]);
        }
        return builder.buildSuite();
    }

    @Test
    void builderPropertyWinsOverTheJvmOne() {
        System.setProperty(KEY, "from-jvm");
        try {
            Suite suite = suite(KEY, "from-builder");
            assertEquals("from-builder", suite.getSystemProperty(KEY));
            assertEquals("from-builder", suite.getSystemProperties().get(KEY));
            // and through iteration, which materializes by a different route
            assertEquals("from-builder", suite.getSystemProperties().entrySet().stream()
                    .filter(e -> e.getKey().equals(KEY)).findFirst().orElseThrow().getValue());
        } finally {
            System.clearProperty(KEY);
        }
    }

    @Test
    void jvmPropertyIsReadWhenTheBuilderHasNoOpinion() {
        System.setProperty(KEY, "from-jvm");
        try {
            Suite suite = suite();
            assertEquals("from-jvm", suite.getSystemProperty(KEY));
            assertEquals("from-jvm", suite.getSystemProperties().get(KEY));
            assertTrue(suite.getSystemProperties().containsKey(KEY));
        } finally {
            System.clearProperty(KEY);
        }
    }

    // A view, not a snapshot: the map is handed to karate.properties once, but a property set
    // after that — by a test, by an ext, by the app under test — still reads through.
    @Test
    void readsStayLive() {
        Suite suite = suite();
        Map<String, String> props = suite.getSystemProperties();
        assertNull(props.get(KEY));
        assertFalse(props.containsKey(KEY));
        System.setProperty(KEY, "set-later");
        try {
            assertEquals("set-later", props.get(KEY));
            assertTrue(props.containsKey(KEY));
        } finally {
            System.clearProperty(KEY);
        }
    }

    @Test
    void iterationSeesEverything() {
        Suite suite = suite(KEY, "from-builder");
        Map<String, String> props = suite.getSystemProperties();
        assertFalse(props.isEmpty());
        assertTrue(props.size() > 1, "should carry the JVM's properties, not just the override");
        assertTrue(props.containsKey("java.version"), "a property every JVM has");
        assertEquals("from-builder", props.get(KEY));
        assertTrue(props.keySet().contains(KEY));
    }

    // A write here has never set a system property — it used to land in a throwaway copy. It
    // must not start throwing either, or a script that pointlessly assigned one would fail.
    @Test
    void writesAreIgnoredRatherThanFatal() {
        Map<String, String> props = suite().getSystemProperties();
        assertDoesNotThrow(() -> props.put(KEY, "ignored"));
        assertNull(props.get(KEY));
        assertNull(System.getProperty(KEY), "must not have reached the JVM");
    }

    // Merging the two maps used to let an explicitly-null builder property shadow the JVM's
    @Test
    void aNullBuilderPropertyShadowsTheJvmOne() {
        System.setProperty(KEY, "from-jvm");
        try {
            Suite suite = suite(KEY, null);
            assertNull(suite.getSystemProperties().get(KEY));
        } finally {
            System.clearProperty(KEY);
        }
    }

    @Test
    void missingPropertyIsNullNotAnError() {
        Suite suite = suite();
        assertNull(suite.getSystemProperty("__karate_never_set_12345__"));
        assertNull(suite.getSystemProperties().get("__karate_never_set_12345__"));
        assertFalse(suite.getSystemProperties().containsKey("__karate_never_set_12345__"));
    }
}
