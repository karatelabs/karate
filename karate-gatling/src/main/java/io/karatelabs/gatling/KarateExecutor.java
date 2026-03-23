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

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.PerfEvent;
import io.karatelabs.core.PerfHook;
import io.karatelabs.core.Runner;
import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure Java executor for Karate features within Gatling.
 * This class contains all the business logic - the Scala layer is just a thin bridge.
 */
public class KarateExecutor {

    private static final Logger log = LoggerFactory.getLogger(KarateExecutor.class);

    private final List<String> featurePaths;
    private final List<String> tags;
    private final KarateProtocol protocol;
    private final boolean silent;

    public KarateExecutor(List<String> featurePaths, List<String> tags, KarateProtocol protocol, boolean silent) {
        this.featurePaths = featurePaths;
        this.tags = tags;
        this.protocol = protocol;
        this.silent = silent;
    }

    /**
     * Result of executing Karate features.
     */
    public static class ExecutionResult {
        public final boolean success;
        public final Map<String, Object> karateVars;

        public ExecutionResult(boolean success, Map<String, Object> karateVars) {
            this.success = success;
            this.karateVars = karateVars;
        }
    }

    /**
     * Execute the Karate features.
     *
     * @param gatlingVars variables from Gatling session
     * @param karateVars  variables from previous Karate executions
     * @param statsReporter reporter for Gatling metrics
     * @param scenario    Gatling scenario name
     * @param groups      Gatling groups
     * @return execution result with updated karate variables
     */
    public ExecutionResult execute(
            Map<String, Object> gatlingVars,
            Map<String, Object> karateVars,
            GatlingStatsReporter statsReporter,
            String scenario,
            scala.collection.immutable.List<String> groups) {

        // Build arg map
        Map<String, Object> arg = new HashMap<>();
        arg.put(KarateProtocol.GATLING_KEY, gatlingVars);
        arg.put(KarateProtocol.KARATE_KEY, karateVars);

        // Create PerfHook for this execution
        PerfHook perfHook = createPerfHook(statsReporter, scenario, groups);

        // Execute features
        boolean success = true;
        for (String path : featurePaths) {
            if (!success) break;

            FeatureResult result = Runner.runFeature(path, arg, perfHook);

            if (result.isFailed()) {
                success = false;
                log.error("Feature failed: {}", path);
            } else {
                // Update karateVars for next feature
                Map<String, Object> resultVars = result.getResultVariables();
                if (resultVars != null) {
                    karateVars.putAll(resultVars);
                    arg.put(KarateProtocol.KARATE_KEY, karateVars);
                }
            }
        }

        return new ExecutionResult(success, karateVars);
    }

    private PerfHook createPerfHook(GatlingStatsReporter reporter, String scenario, scala.collection.immutable.List<String> groups) {
        return new PerfHook() {

            @Override
            public String getPerfEventName(HttpRequest request, ScenarioRuntime runtime) {
                // Use protocol's name resolver if configured
                if (protocol != null && protocol.getNameResolver() != null) {
                    Map<String, Object> vars = runtime != null ? runtime.getAllVariables() : new HashMap<>();
                    String customName = protocol.getNameResolver().apply(request, vars);
                    if (customName != null) return customName;
                }

                // Default: use URI path matching
                String method = request != null ? request.getMethod() : "GET";
                String path = extractPath(request);
                if (protocol != null) {
                    String matched = protocol.resolveName(path);
                    if (matched != null) return method + " " + matched;
                }

                // Fallback: method + path
                return method + " " + path;
            }

            @Override
            public void reportPerfEvent(PerfEvent event) {
                if (silent) return;
                reporter.reportPerfEvent(scenario, groups, event);

                // Apply pause if configured
                if (protocol != null) {
                    int pauseMs = protocol.pauseFor(event.getName(), null);
                    if (pauseMs > 0) {
                        pause(pauseMs);
                    }
                }
            }

            @Override
            public void afterFeature(FeatureResult result) {
                // handled in execute()
            }

            @Override
            public void pause(Number millis) {
                try {
                    Thread.sleep(millis.longValue());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public void submit(Runnable runnable) {
                runnable.run();
            }
        };
    }

    private String extractPath(HttpRequest request) {
        if (request == null) return "unknown";
        String url = request.getUrlAndPath();
        if (url == null) return "unknown";

        int protocolEnd = url.indexOf("://");
        if (protocolEnd > 0) {
            int pathStart = url.indexOf('/', protocolEnd + 3);
            if (pathStart > 0) {
                int queryStart = url.indexOf('?', pathStart);
                if (queryStart > 0) return url.substring(pathStart, queryStart);
                return url.substring(pathStart);
            }
        }

        int queryStart = url.indexOf('?');
        if (queryStart > 0) return url.substring(0, queryStart);
        return url;
    }
}
