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

