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

import io.karatelabs.common.Resource;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.output.LogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Config-only evaluation: evaluate a project's {@code karate-base.js} + {@code karate-config.js} (+ the
 * env-specific {@code karate-config-<env>.js}) for a working dir and return the merged config
 * {@code Map} <b>without running any features</b> (no {@code SUITE_ENTER}/{@code SUITE_EXIT}, no
 * scenarios). The config sibling of {@link BootLoader#bootOnly} — so a caller that lives OUTSIDE a run
 * (a persistent serve/loop engine that wants a project's config — base URLs, {@code env} selection,
 * secrets — resolved the SAME way {@code karate run} / {@code Runner.run} resolve it) can get the
 * config map on demand and bind it, instead of hand-injecting individual variables.
 *
 * <p>Evaluation runs through the normal {@link ScenarioRuntime} config phase (a minimal top-level
 * runtime over a synthetic empty scenario), so the full {@code karate.*} API — including
 * {@code karate.callSingle} and {@code karate.properties}/{@code karate.env} — is live during config,
 * exactly as in a real run. Fail-loud: a present-but-failing config throws (the same discipline as
 * {@link BootLoader#bootOnly}); the caller decides whether to degrade.
 */
public final class ConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

    private static final String SYNTHETIC = "Feature: __config\n  Scenario: __config\n";

    private ConfigLoader() {
    }

    /**
     * Evaluate config for {@code workingDir} under {@code env} (nullable → the ambient
     * {@code karate.env}) and return the merged config variables. Returns an empty map when
     * {@code workingDir} is null. Throws when a config file is present but its evaluation fails.
     */
    public static Map<String, Object> configOnly(Path workingDir, String env) {
        return configOnly(workingDir, env, null);
    }

    /**
     * As {@link #configOnly(Path, String)}, plus a <b>system-property overlay</b> for this evaluation
     * only — the properties the enclosing run is using, so config resolved on its behalf sees the same
     * {@code karate.sysprop(...)} / {@code karate.properties[...]} values that run sees.
     *
     * <p>Without it, config evaluated <i>inside</i> a run (a nested check that re-resolves the project's
     * config on its own engine) reads the JVM's ambient properties — and therefore the project's
     * DEFAULTS — while the enclosing feature reads the run's overlay. The two then disagree on exactly
     * the values a launch script sets to point a suite elsewhere: a base URL, or the port a
     * just-served SUT is on. Pass the enclosing {@code Suite#getSystemProperties()} to keep them
     * in agreement.</p>
     *
     * @param systemProperties overlay for this evaluation; null → the ambient JVM properties (unchanged behaviour)
     */
    public static Map<String, Object> configOnly(Path workingDir, String env, Map<String, String> systemProperties) {
        return configOnly(workingDir, env, systemProperties, null);
    }

    /**
     * {@link #configOnly(Path, String, Map)} with {@code systemEnv} layered over the process environment
     * for {@code karate.sysenv} ({@code Runner.Builder.systemEnv}); null → the process environment alone.
     */
    public static Map<String, Object> configOnly(Path workingDir, String env, Map<String, String> systemProperties,
                                                 Map<String, String> systemEnv) {
        return evaluate(workingDir, env, systemProperties, systemEnv).vars();
    }

    /**
     * The config variables, and the auth the config configured ({@code configure auth}) as
     * {@code {type, fields, refs}}: its type, the sorted names of the fields it set, and for each string field
     * whose value a {@code systemEnv} name holds, that name (the lowest when several do). Never a value; empty
     * when none is configured.
     */
    public record Evaluated(Map<String, Object> vars, Map<String, Object> auth) {
    }

    /**
     * The auth {@code evaluated}'s config configured, with its values, for Java code that signs requests with it;
     * null when none is configured, and null for an {@code Evaluated} built other than by {@link #evaluate}. Nothing
     * the {@code Evaluated} itself offers yields a value, neither its components, nor its printing, nor a method,
     * so a script handed one cannot reach them; code that can call a Java static method, a script with Java interop
     * included, can call this one.
     */
    public static ResolvedAuth resolvedAuth(Evaluated evaluated) {
        return evaluated.auth() instanceof AuthShape shape ? shape.resolved : null;
    }

    /**
     * The auth map a config configured, values included, as {@code configure auth} holds it. Its {@code toString}
     * names the type alone.
     */
    public static final class ResolvedAuth {

        private final Map<String, Object> map;

        private ResolvedAuth(Map<String, Object> map) {
            this.map = Collections.unmodifiableMap(map);
        }

        public String type() {
            return String.valueOf(map.get("type"));
        }

        /** The auth map, unmodifiable: the shape {@link io.karatelabs.http.AuthHandlers#of} takes. */
        public Map<String, Object> map() {
            return map;
        }

        @Override
        public String toString() {
            return "ResolvedAuth[" + type() + "]";
        }
    }

    // the auth shape, carrying the resolved auth beside its entries so the record's components stay as they are
    private static final class AuthShape extends LinkedHashMap<String, Object> {

        private final transient ResolvedAuth resolved;

        AuthShape(ResolvedAuth resolved) {
            this.resolved = resolved;
        }
    }

    /** {@link #configOnly(Path, String, Map, Map)}, answering the configured auth beside the variables. */
    public static Evaluated evaluate(Path workingDir, String env, Map<String, String> systemProperties,
                                     Map<String, String> systemEnv) {
        return evaluate(workingDir, env, systemProperties, systemEnv, false);
    }

    /**
     * {@link #evaluate(Path, String, Map, Map)}, with {@code quiet} discarding every line the evaluation logs on
     * this thread — its HTTP exchanges, a called feature's {@code print} and {@code karate.log}, a step failure —
     * as {@link LogContext#quiet()} does. A failure is still thrown.
     */
    public static Evaluated evaluate(Path workingDir, String env, Map<String, String> systemProperties,
                                     Map<String, String> systemEnv, boolean quiet) {
        if (!quiet) {
            return evaluateConfig(workingDir, env, systemProperties, systemEnv);
        }
        try (LogContext.Quiet ignored = LogContext.quiet()) {
            return evaluateConfig(workingDir, env, systemProperties, systemEnv);
        }
    }

    private static Evaluated evaluateConfig(Path workingDir, String env, Map<String, String> systemProperties,
                                            Map<String, String> systemEnv) {
        if (workingDir == null) {
            return new Evaluated(new LinkedHashMap<>(), new LinkedHashMap<>());
        }
        Runner.Builder builder = Runner.builder()
                .configDir(workingDir.toString())
                .workingDir(workingDir);
        if (env != null && !env.isBlank()) {
            builder.karateEnv(env);
        }
        if (systemProperties != null) {
            systemProperties.forEach(builder::systemProperty);
        }
        builder.systemEnv(systemEnv);
        Suite suite = builder.buildSuite();
        // a synthetic, stepless, TOP-LEVEL scenario: constructing its ScenarioRuntime runs the config
        // phase (ScenarioRuntime.initEngine -> evalConfig) and nothing else — no steps are executed.
        Feature feature = Feature.read(Resource.text(SYNTHETIC, workingDir));
        FeatureRuntime fr = new FeatureRuntime(suite, feature, null, null, false, null, null);
        Scenario scenario = feature.getSections().get(0).getScenario();
        ScenarioRuntime sr = new ScenarioRuntime(fr, scenario);
        try {
            // The constructor captures a config failure instead of throwing (so a real run can report
            // the called feature's steps). No call() happens here, so rethrow to honour the fail-loud
            // contract.
            Throwable configError = sr.getConfigError();
            if (configError != null) {
                throw configError instanceof RuntimeException re ? re : new RuntimeException(configError);
            }
            Map<String, Object> config = sr.getConfigVars();
            logger.debug("configOnly({}, env={}) -> {} variables", workingDir, env, config.size());
            return new Evaluated(config, authShape(sr.getConfig().getAuth(), systemEnv));
        } finally {
            // Synthetic runtime, no call() — same reason as the dynamic-outline template above.
            sr.releaseHttpClient();
        }
    }

    private static Map<String, Object> authShape(Map<String, Object> auth, Map<String, String> systemEnv) {
        Object type = auth.get("type");
        if (type == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> shape = new AuthShape(new ResolvedAuth(new LinkedHashMap<>(auth)));
        List<String> fields = new ArrayList<>();
        auth.forEach((k, v) -> {
            if (v != null && !"type".equals(k)) {
                fields.add(k);
            }
        });
        Collections.sort(fields);
        Map<String, String> refs = new LinkedHashMap<>();
        for (String field : fields) {
            if (auth.get(field) instanceof String value && !value.isEmpty() && systemEnv != null) {
                systemEnv.entrySet().stream()
                        .filter(e -> value.equals(e.getValue()))
                        .map(Map.Entry::getKey)
                        .min(String::compareTo)
                        .ifPresent(name -> refs.put(field, name));
            }
        }
        shape.put("type", String.valueOf(type));
        shape.put("fields", fields);
        shape.put("refs", refs);
        return shape;
    }
}
