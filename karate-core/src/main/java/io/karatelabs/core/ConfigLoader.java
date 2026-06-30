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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
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
        if (workingDir == null) {
            return new LinkedHashMap<>();
        }
        Runner.Builder builder = Runner.builder()
                .configDir(workingDir.toString())
                .workingDir(workingDir);
        if (env != null && !env.isBlank()) {
            builder.karateEnv(env);
        }
        Suite suite = builder.buildSuite();
        // a synthetic, stepless, TOP-LEVEL scenario: constructing its ScenarioRuntime runs the config
        // phase (ScenarioRuntime.initEngine -> evalConfig) and nothing else — no steps are executed.
        Feature feature = Feature.read(Resource.text(SYNTHETIC, workingDir));
        FeatureRuntime fr = new FeatureRuntime(suite, feature, null, null, false, null, null);
        Scenario scenario = feature.getSections().get(0).getScenario();
        ScenarioRuntime sr = new ScenarioRuntime(fr, scenario);
        Map<String, Object> config = sr.getConfigVars();
        logger.debug("configOnly({}, env={}) -> {} variables", workingDir, env, config.size());
        return config;
    }
}
