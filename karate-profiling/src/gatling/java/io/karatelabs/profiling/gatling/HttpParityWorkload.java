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
 * The same two HTTP calls, driven two ways against the same sibling mock: once through a
 * Karate feature, once through Gatling's own HTTP DSL.
 *
 * <p>Both variants POST a small JSON body, capture the id from the response, GET it back and
 * assert on the result — so the comparison is of two clients doing one user's realistic work,
 * not of a Karate feature against an empty loop.
 *
 * <p><b>The two are not doing identical work, and that is the point.</b> Karate parses each
 * response into a document and runs a structural {@code match}; the Gatling variant extracts
 * one JSONPath and compares a string. Karate's richer assertion <em>is</em> the overhead being
 * measured — reading the ratio as "Karate is slower at HTTP" would be the wrong conclusion.
 * What the number answers is: what does a Karate-driven virtual user cost against a
 * Gatling-native one, for work a user would actually write.
 *
 * <p>Sized deliberately small (a ~100-byte body). A large payload would make both variants a
 * measurement of the JSON parser, which is a different question with its own workloads.
 */
public final class HttpParityWorkload {

    private HttpParityWorkload() {
    }

    public static final class Karate extends GatlingWorkload {

        @Override
        public String name() {
            return "gatling-http-karate";
        }

        @Override
        public String describe() {
            return "Gatling driving a Karate feature: POST a cat, GET it back, match the result. "
                    + "Paired with gatling-http-plain.";
        }

        @Override
        public boolean needsMock() {
            return true;
        }

        @Override
        protected Class<? extends Simulation> simulation() {
            return HttpKarateSimulation.class;
        }
    }

    public static final class Plain extends GatlingWorkload {

        @Override
        public String name() {
            return "gatling-http-plain";
        }

        @Override
        public String describe() {
            return "The same POST + GET through Gatling's own HTTP DSL. The reference the Karate "
                    + "variant is measured against.";
        }

        @Override
        public boolean needsMock() {
            return true;
        }

        @Override
        protected Class<? extends Simulation> simulation() {
            return HttpPlainSimulation.class;
        }
    }

}
