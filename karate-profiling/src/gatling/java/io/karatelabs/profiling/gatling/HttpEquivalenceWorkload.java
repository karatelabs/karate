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
package io.karatelabs.profiling.gatling;

import io.gatling.javaapi.core.Simulation;

/**
 * The two controls that say whether the parity number is comparing like with like.
 *
 * <p><b>Why they exist.</b> Every parity figure this harness has published assumes the two arms
 * do equivalent work, and they do not. Karate's feature ends with
 * {@code match response == { id: '#(catId)', name: 'Billie', age: 5 }} — three fields plus the
 * id round-trip. The plain reference checks one JSONPath and never looks at the id. So Karate is
 * doing strictly more, which makes the published deficit an <b>upper bound</b> on what it costs
 * to do the same work. Nobody has measured how much of it is the assertion.
 *
 * <p><b>Why two and not one.</b> Each control moves one arm to the other's level:
 *
 * <ul>
 *   <li>{@code gatling-http-plain-fat} — raise Gatling to Karate's checks, and pair it against
 *       the ordinary {@code gatling-http-karate}.</li>
 *   <li>{@code gatling-http-karate-lean} — lower Karate to Gatling's checks, and pair it against
 *       the ordinary {@code gatling-http-plain}.</li>
 * </ul>
 *
 * A single control only says which way the gap moves. Running both brackets the like-for-like
 * cost from above and below, and the two should meet: if they disagree, something other than the
 * assertion differs between the arms and the parity number needs re-examining rather than
 * re-quoting.
 *
 * <p><b>Reading the result.</b> If both controls pull the deficit toward zero, the published
 * figure is the pessimistic end and the true like-for-like cost lies between them. If neither
 * moves it, the assertion asymmetry was never the cost and the figure stands unqualified — which
 * is an equally useful answer, and the reason this is worth running rather than arguing about.
 *
 * <p>Run them with {@code etc/ec2/matrix.sh --control fat} and {@code --control lean}, which is
 * what wires each variant against the right partner. {@code compare} classifies a run by whether
 * its directory name contains {@code -karate-} or {@code -plain-}, so both names slot in without
 * it needing to know these exist.
 */
public final class HttpEquivalenceWorkload {

    private HttpEquivalenceWorkload() {
    }

    /** Gatling raised to Karate's checks. Pairs against {@code gatling-http-karate}. */
    public static final class PlainFat extends GatlingWorkload {

        @Override
        public String name() {
            return "gatling-http-plain-fat";
        }

        @Override
        public String describe() {
            return "Equivalence control: the plain arm checking all three fields and the id "
                    + "round-trip, as the Karate feature does. Pair it with gatling-http-karate.";
        }

        @Override
        public boolean needsMock() {
            return true;
        }

        @Override
        protected Class<? extends Simulation> simulation() {
            return HttpPlainFatSimulation.class;
        }
    }

    /** Karate lowered to Gatling's checks. Pairs against {@code gatling-http-plain}. */
    public static final class KarateLean extends GatlingWorkload {

        @Override
        public String name() {
            return "gatling-http-karate-lean";
        }

        @Override
        public String describe() {
            return "Equivalence control: the Karate arm checking one field instead of matching "
                    + "the whole response. Pair it with gatling-http-plain.";
        }

        @Override
        public boolean needsMock() {
            return true;
        }

        @Override
        protected Class<? extends Simulation> simulation() {
            return HttpKarateLeanSimulation.class;
        }
    }

}
