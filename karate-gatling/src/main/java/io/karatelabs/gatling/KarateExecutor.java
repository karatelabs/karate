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

    private final String featurePath;
    private final List<String> tags;
    private final KarateProtocol protocol;
    private final boolean silent;
    private final LogReplayer replayer;

    public KarateExecutor(String featurePath, List<String> tags, KarateProtocol protocol, boolean silent) {
        this.featurePath = featurePath;
        this.tags = tags;
        this.protocol = protocol;
        this.silent = silent;
        this.replayer = LogReplayer.forProtocol(protocol);
    }

    /**
     * Result of executing Karate features.
     */
    public static class ExecutionResult {
        public final boolean success;
        public final Map<String, Object> karateVars;
        /** Karate output retained for this virtual user, to carry on the session. Null when disabled. */
        public final LogReplayer.Buffer logBuffer;

        public ExecutionResult(boolean success, Map<String, Object> karateVars) {
            this(success, karateVars, null);
        }

        public ExecutionResult(boolean success, Map<String, Object> karateVars, LogReplayer.Buffer logBuffer) {
            this.success = success;
            this.karateVars = karateVars;
            this.logBuffer = logBuffer;
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
        return execute(gatlingVars, karateVars, null, statsReporter, scenario, groups);
    }

    /**
     * Execute the Karate features, carrying the log-replay buffer of this virtual user.
     *
     * @param logBuffer the Karate output retained from the features already run in this Gatling
     *                  session, may be null. See {@link KarateLogReplay}.
     */
    public ExecutionResult execute(
            Map<String, Object> gatlingVars,
            Map<String, Object> karateVars,
            LogReplayer.Buffer logBuffer,
            GatlingStatsReporter statsReporter,
            String scenario,
            scala.collection.immutable.List<String> groups) {

        // Build arg map
        Map<String, Object> arg = new HashMap<>();
        arg.put(KarateProtocol.GATLING_KEY, gatlingVars);
        arg.put(KarateProtocol.KARATE_KEY, karateVars);

        // Create PerfHook for this execution
        PerfHook perfHook = createPerfHook(statsReporter, scenario, groups);

        // Execute feature — apply protocol.runner template so users can configure
        // karateEnv, configDir, systemProperty etc. via protocol.runner.*
        Runner.Builder template = protocol != null ? protocol.getRunner() : null;
        boolean success = true;
        FeatureResult result = Runner.runFeature(featurePath, arg, perfHook, tags, template);

        if (result.isFailed()) {
            success = false;
            // Surface the actual failure detail, not just the path. The single-feature Gatling run
            // has HTML report and console summary disabled, so this is the only place the failure
            // reaches the logs.
            log.error("Feature failed: {}", describeFailure(result));
        } else {
            // Update karateVars for chaining into subsequent exec() calls
            Map<String, Object> resultVars = result.getResultVariables();
            if (resultVars != null) {
                karateVars.putAll(resultVars);
            }
        }

        // fold this feature's captured output into the virtual user's buffer, replaying on failure
        LogReplayer.Buffer nextBuffer = replayer.after(logBuffer, featurePath, result);

        return new ExecutionResult(success, karateVars, nextBuffer);
    }

    /**
     * The failure rendered for the log: a summary line naming <em>where</em> and <em>why</em> —
     * {@code path/to.feature:LINE - match failed: EQUALS} — followed by the same detail the console
     * summary shows. The summary line intentionally carries the same location and reason as the
     * Gatling KO message ({@code ScenarioResult.getPerfFailureMessage()}) so the load report and the
     * log can be lined up; the full match diff follows underneath, where it does not have to stay
     * short. The location is repeated in absolute form because that is the IDE-clickable one, while
     * the summary keeps the path exactly as the simulation declared it.
     */
    private String describeFailure(FeatureResult result) {
        StringBuilder sb = new StringBuilder(featurePath);
        int line = result.getFailedStepLine();
        if (line > 0) {
            sb.append(':').append(line);
        }
        String summary = result.getFailureReasonSummary();
        if (summary != null) {
            sb.append(" - ").append(summary);
        }
        String display = result.getFailureMessageForDisplay();
        if (display != null) {
            sb.append("\n  ").append(display);
        }
        String comment = result.getFailedStepComment();
        if (comment != null) {
            sb.append("\n  ").append(comment);
        }
        String reason = result.getFailureReason();
        if (reason != null) {
            sb.append("\n  ").append(reason.strip().replace("\n", "\n  "));
        }
        return sb.toString();
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
