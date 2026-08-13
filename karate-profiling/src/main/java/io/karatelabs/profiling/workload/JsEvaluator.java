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
package io.karatelabs.profiling.workload;

/**
 * One fresh evaluation of a JS source string — the engine seam of the js-* family.
 *
 * <p>Every implementation must honour the family's timed-lifecycle contract
 * (PROFILING.md §9, R1): whatever the engine amortizes across the run (a shared standard
 * library, a sealed root scope) is built once per JVM in the constructor; everything a fresh
 * evaluation genuinely costs — context/engine setup, a fresh global scope, parsing the source,
 * executing it — happens inside every {@link #eval} call, with no adapter-level source or
 * compiled-script cache, ever. The workload's unique-source suffix keeps an <em>engine's</em>
 * own cache from firing; the adapter must not add one of its own.
 *
 * <p>Selection is by the {@code karate.profiling.jsEngine} system property (set by the
 * parent's {@code --engine} flag), so the choice lands in run-meta and the digest as part of
 * the arm's identity. The rhino-best adapter is loaded reflectively because it only compiles
 * under {@code -Prhino} — the default build carries no competitor engine.
 */
public interface JsEvaluator {

    String PROPERTY = "karate.profiling.jsEngine";

    Object eval(String source);

    /** Human-readable identity for logs; the digest identity is written by the parent. */
    String describe();

    static JsEvaluator resolve() {
        String engine = System.getProperty(PROPERTY, "karate");
        switch (engine) {
            case "karate":
                return new Karate();
            case "rhino-best":
                try {
                    return (JsEvaluator) Class.forName("io.karatelabs.profiling.rhino.RhinoBest")
                            .getDeclaredConstructor().newInstance();
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("engine 'rhino-best' requested but the "
                            + "adapter is not on the classpath — the build ran without "
                            + "-Prhino. Run through etc/run.sh with --engine rhino-best so "
                            + "the profile activates.", e);
                } catch (ReflectiveOperationException e) {
                    // The adapter's constructor throws on a version-pin mismatch — surface
                    // that message, not the reflection wrapper.
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    throw new IllegalStateException("could not initialise the rhino-best "
                            + "adapter: " + cause.getMessage(), cause);
                }
            default:
                throw new IllegalArgumentException("unknown js engine '" + engine
                        + "' — supported: karate, rhino-best");
        }
    }

    /** The engine under development: a fresh {@code Engine} per evaluation, as always. */
    final class Karate implements JsEvaluator {

        @Override
        public Object eval(String source) {
            return new io.karatelabs.js.Engine().eval(source);
        }

        @Override
        public String describe() {
            return "karate";
        }
    }

}
