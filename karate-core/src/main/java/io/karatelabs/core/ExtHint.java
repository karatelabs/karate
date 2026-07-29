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
package io.karatelabs.core;

import io.karatelabs.js.EngineException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a bare {@code ReferenceError: X is not defined} inside a step into an actionable failure —
 * when {@code X} is a global that an <b>ext</b> would have bound, had the project declared it.
 *
 * <p>An ext registers its globals when {@code karate-boot.js} calls {@code boot.ext('<name>')}
 * ({@code rules} binds {@code Rule}, {@code Schema}, …). Leave that line out and every step touching
 * the global fails with the bare JS error, which names the global but never the declaration that
 * binds it — so the reader (or an LLM driving the run) hunts for a typo in the feature instead of
 * adding one line to the boot file.
 *
 * <p>There is no catalogue of ext names here. Resolution walks {@link BootBinding}'s own
 * {@code io.karatelabs.ext.<name>.<Name>Ext} convention outward from the missing global, and speaks
 * only when that class is genuinely on the classpath and the Suite did not already bind the name —
 * so a hint is never invented for an ext this runtime cannot boot, and an ordinary typo stays an
 * ordinary typo.
 */
public final class ExtHint {

    // The unframed JS-side message for a ReferenceError is `<name> is not defined` (per spec); we
    // read getJsMessage() rather than getMessage(), which carries host-side framing.
    private static final Pattern NOT_DEFINED = Pattern.compile("([A-Za-z_$][\\w$]*) is not defined");

    private ExtHint() {
    }

    /**
     * The step failure with the boot hint appended, or {@code error} itself when no hint applies —
     * identity is the contract, the caller stores whatever comes back. The wrapper keeps the
     * original as its cause and reuses its stack, so nothing about the origin is lost.
     */
    public static Throwable decorate(Throwable error, Suite suite) {
        String hint = forError(error, suite);
        if (hint == null) {
            return error;
        }
        String message = error.getMessage() == null ? error.toString() : error.getMessage();
        RuntimeException decorated = new RuntimeException(message + "\n" + hint, error);
        decorated.setStackTrace(error.getStackTrace());
        return decorated;
    }

    /** The hint line for this failure, or null when it is not a resolvable missing-ext global. */
    public static String forError(Throwable error, Suite suite) {
        String missing = missingGlobal(error);
        if (missing == null) {
            return null;
        }
        // already bound by a booted ext → the failure is something else (scoping, a nulled var);
        // a "declare the ext" hint would send the reader down the wrong path.
        if (suite != null && suite.getGlobals() != null && suite.getGlobals().containsKey(missing)) {
            return null;
        }
        for (String candidate : extCandidates(missing)) {
            if (BootBinding.extAvailable(candidate)) {
                return "hint: an ext named '" + candidate + "' is on the classpath but this run never"
                        + " booted it — an ext binds its globals only when the project declares it."
                        + " Add boot.ext('" + candidate + "') to " + BootLoader.BOOT_FILE_NAME
                        + " at the project root.";
            }
        }
        return null;
    }

    /** The global name of a ReferenceError anywhere in the cause chain, else null. */
    private static String missingGlobal(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof EngineException ee && "ReferenceError".equals(ee.getJsErrorName())) {
                String jsMessage = ee.getJsMessage();
                if (jsMessage != null) {
                    Matcher m = NOT_DEFINED.matcher(jsMessage);
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * Ext names to probe for a missing global, in order — the ext is conventionally the lowercased
     * global ({@code Http} → {@code http}, {@code Kafka} → {@code kafka}), but a namespace is
     * singular where its ext is plural ({@code Rule} → {@code rules}), so try both inflections.
     */
    private static Set<String> extCandidates(String global) {
        String lower = global.toLowerCase();
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(lower);
        if (lower.endsWith("s")) {
            candidates.add(lower.substring(0, lower.length() - 1));
        } else {
            candidates.add(lower + "s");
        }
        return candidates;
    }
}
