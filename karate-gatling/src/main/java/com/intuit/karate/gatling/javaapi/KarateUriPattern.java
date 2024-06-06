/*
 * The MIT License
 *
 * Copyright 2023 Karate Labs Inc.
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
package com.intuit.karate.gatling.javaapi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.intuit.karate.gatling.MethodPause;

/** CLass to be used as a parameter of KarateDsl.karateProtocol.
 * 
 * Instances are obtained from KarateDsl.uri(<uri>) chained with nil() or pauseFor() and won't typically be created directly.
 */
public class KarateUriPattern {
    final String uri;
    final List<MethodPause> pauses;
    
    KarateUriPattern(String uri, List<MethodPause> pauses) {
        this.uri = uri;
        this.pauses = pauses;
    }

    String getUri() {
        return uri;
    }

    List<MethodPause> getPauses() {
        return pauses;
    }
    
    public static class KarateUriPatternBuilder {
        private final String uri;

        KarateUriPatternBuilder(String uri) {
           this.uri = uri;     
        }

        /**
         * Creates a uriPattern with no pauses
         * @return
         */
        public KarateUriPattern nil() {
           return new KarateUriPattern(uri, Collections.emptyList());     
        }

        public KarateUriPattern pauseFor(String method, int durationInMillis) {
            return pauseFor(KarateDsl.method(method, durationInMillis));
        }

        public KarateUriPattern pauseFor(String method1, int durationInMillis1, String method2, int durationInMillis2) {
            return pauseFor(KarateDsl.method(method1, durationInMillis1), KarateDsl.method(method2, durationInMillis2));
        }

        public KarateUriPattern pauseFor(MethodPause... pauses) {
            return new KarateUriPattern(uri, Arrays.asList(pauses));
        }
    }
}

