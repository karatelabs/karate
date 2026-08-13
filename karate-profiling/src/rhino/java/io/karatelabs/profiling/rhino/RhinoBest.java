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
package io.karatelabs.profiling.rhino;

import io.karatelabs.profiling.workload.JsEvaluator;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * The {@code rhino-best} arm: Rhino embedded the way its own docs recommend, and exactly the
 * configuration the public karate-js-benchmark scoreboard calls Rhino-best — interpreted mode,
 * ES6 language level, a shared <em>sealed</em> standard-objects root built once per JVM, and a
 * fresh per-eval scope that merely prototypes off it. Structurally the same design as
 * karate-js (an immutable shared standard library behind a cheap per-eval global), which is
 * what makes it the fair head-to-head, not merely the fast one.
 *
 * <p><b>The timed lifecycle is the contract</b> (PROFILING.md §9, R1). Once per JVM, in the
 * constructor: build and seal the root. Inside every {@link #eval}: enter a {@code Context}
 * (interpreted, ES6), create the fresh child scope, parse <em>and</em> evaluate the source,
 * exit. Nothing else is amortized — in particular there is no compiled-script or source cache
 * here, and {@code evaluateString} parses its argument on every call. This is the benchmarked
 * embedding <em>recipe</em>; it is not a claim that Rhino's and karate-js's construction APIs
 * cost the same.
 *
 * <p>The version pin: {@link #PINNED_VERSION} must match the {@code -Prhino} profile's
 * dependency in {@code pom.xml}. The constructor checks the runtime's implementation version
 * against it and refuses to run on a mediated substitute — the parent separately records the
 * resolved jar's sha256 into run-meta and the digest, so the arm's identity is verified twice:
 * bytes by the parent, behaviour by this check.
 */
public final class RhinoBest implements JsEvaluator {

    /** Keep in lockstep with the {@code rhino} profile's dependency version in pom.xml. */
    static final String PINNED_VERSION = "1.9.1";

    private final ScriptableObject sealedRoot;
    private final String implementationVersion;

    public RhinoBest() {
        Context cx = enter();
        try {
            implementationVersion = cx.getImplementationVersion();
            if (implementationVersion == null || !implementationVersion.contains(PINNED_VERSION)) {
                throw new IllegalStateException("rhino-best is pinned to Rhino " + PINNED_VERSION
                        + " but the classpath resolved '" + implementationVersion + "' — a "
                        + "mediated or substituted artifact would be measured under the wrong "
                        + "label. Align the -Prhino profile's dependency and this pin.");
            }
            ScriptableObject root = cx.initSafeStandardObjects(null, true);
            root.sealObject();
            sealedRoot = root;
        } finally {
            Context.exit();
        }
    }

    @Override
    public Object eval(String source) {
        Context cx = enter();
        try {
            Scriptable scope = cx.newObject(sealedRoot);
            scope.setPrototype(sealedRoot);
            scope.setParentScope(null);
            return cx.evaluateString(scope, source, "profiling", 1, null);
        } finally {
            Context.exit();
        }
    }

    @Override
    public String describe() {
        return "rhino-best (" + implementationVersion + ", interpreted, sealed shared root)";
    }

    private static Context enter() {
        Context cx = Context.enter();
        cx.setLanguageVersion(Context.VERSION_ES6);
        cx.setInterpretedMode(true);
        return cx;
    }

}
