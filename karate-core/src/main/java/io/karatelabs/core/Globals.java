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

import java.io.InputStream;
import java.util.Properties;

/**
 * Global constants and utilities for Karate runtime.
 * <p>
 * Provides access to version info, telemetry settings, and user identification.
 */
public final class Globals {

    /**
     * Karate version loaded from karate-meta.properties.
     * Eager loaded since it's cheap and used early.
     */
    public static final String KARATE_VERSION;

    static {
        KARATE_VERSION = loadVersion();
    }

    private static String loadVersion() {
        try (InputStream is = Globals.class.getResourceAsStream("/karate-meta.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                return props.getProperty("karate.version", "(unknown)");
            }
        } catch (Exception e) {
            // ignore
        }
        return "(unknown)";
    }

    /**
     * Check if telemetry is enabled.
     * Controlled by KARATE_TELEMETRY environment variable.
     * Default is true (enabled).
     */
    public static boolean isTelemetryEnabled() {
        String env = System.getenv("KARATE_TELEMETRY");
        return env == null || !"false".equalsIgnoreCase(env.trim());
    }

    // TODO: lazy-loaded UUID from ~/.karate/uuid.txt
    // public static String uuid() { ... }

    private Globals() {
        // utility class
    }

}
