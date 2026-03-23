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
package io.karatelabs.gatling;

import io.gatling.javaapi.core.Session;
import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.Runner;
import io.karatelabs.core.ScenarioResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Encapsulates the logic for executing Karate features within Gatling.
 * This class is used by KarateFeatureBuilder to create session functions.
 */
public class KarateFeatureAction {

    private static final Logger log = LoggerFactory.getLogger(KarateFeatureAction.class);

    private final String[] featurePaths;
    private final String[] tags;
    private final boolean silent;

    public KarateFeatureAction(String[] featurePaths, String[] tags, boolean silent) {
        this.featurePaths = featurePaths;
        this.tags = tags;
        this.silent = silent;
    }

    /**
     * Create a session function that executes the Karate feature.
     * This is used with Gatling's exec() method.
     */
    public Function<Session, Session> toSessionFunction() {
        return session -> {
            long startTime = System.currentTimeMillis();
            String featurePath = featurePaths.length > 0 ? featurePaths[0] : "unknown";
            log.trace("Executing Karate feature: {}", featurePath);

            try {
                // Build arg map with Gatling session variables and previous Karate vars
                Map<String, Object> arg = new HashMap<>();

                // Add __gatling map with all Gatling session variables
                Map<String, Object> gatlingVars = extractSessionVars(session);
                arg.put(KarateProtocol.GATLING_KEY, gatlingVars);
                log.trace("__gatling vars: {}", gatlingVars);

                // Add __karate map with variables from previous feature executions
                Map<String, Object> karateVars = getKarateVars(session);
                arg.put(KarateProtocol.KARATE_KEY, karateVars);
                log.trace("__karate vars: {}", karateVars);
                log.trace("Full arg map: {}", arg);

                // Execute each feature path
                FeatureResult lastResult = null;
                for (String path : featurePaths) {
                    lastResult = Runner.runFeature(path, arg);

                    if (lastResult.isFailed()) {
                        String error = getFirstError(lastResult);
                        long duration = System.currentTimeMillis() - startTime;
                        log.error("Karate feature failed in {}ms: {} - {}", duration, path, error);
                        return session.markAsFailed();
                    }

                    // Update karateVars with result variables for next feature in chain
                    Map<String, Object> resultVars = lastResult.getResultVariables();
                    if (resultVars != null) {
                        karateVars.putAll(resultVars);
                        arg.put(KarateProtocol.KARATE_KEY, karateVars);
                    }
                }

                long duration = System.currentTimeMillis() - startTime;
                int scenarioCount = lastResult != null ? lastResult.getScenarioResults().size() : 0;
                log.debug("Karate feature completed in {}ms: {} (scenarios: {})",
                        duration, featurePath, scenarioCount);

                // Store result variables in session for next exec() in the Gatling scenario
                return session.set(KarateProtocol.KARATE_KEY, karateVars);

            } catch (Exception e) {
                log.error("Karate feature execution failed: {}", e.getMessage(), e);
                return session.markAsFailed();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getKarateVars(Session session) {
        if (session.contains(KarateProtocol.KARATE_KEY)) {
            Object val = session.get(KarateProtocol.KARATE_KEY);
            if (val instanceof Map) {
                return new HashMap<>((Map<String, Object>) val);
            }
        }
        return new HashMap<>();
    }

    /**
     * Extract session variables as a Java Map.
     * Converts Scala Session attributes to Java Map for use in Karate features.
     */
    private Map<String, Object> extractSessionVars(Session session) {
        Map<String, Object> vars = new HashMap<>();
        // Access Scala session attributes and convert to Java Map
        scala.collection.immutable.Map<String, Object> scalaAttrs = session.asScala().attributes();
        scala.collection.Iterator<scala.Tuple2<String, Object>> iter = scalaAttrs.iterator();
        while (iter.hasNext()) {
            scala.Tuple2<String, Object> entry = iter.next();
            String key = entry._1();
            // Skip internal Gatling keys
            if (!key.startsWith("gatling.")) {
                vars.put(key, entry._2());
            }
        }
        return vars;
    }

    /**
     * Get the first error message from the result.
     */
    private String getFirstError(FeatureResult result) {
        for (ScenarioResult sr : result.getScenarioResults()) {
            if (sr.isFailed()) {
                Throwable error = sr.getError();
                if (error != null) {
                    // Log full stack trace for debugging
                    log.debug("Feature error details:", error);
                    return error.getMessage();
                }
                String failureMessage = sr.getFailureMessage();
                if (failureMessage != null) {
                    return failureMessage;
                }
            }
        }
        return "Unknown error";
    }

}
