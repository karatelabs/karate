/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.cucumber;

import com.intuit.karate.CallContext;
import com.intuit.karate.Script;
import com.intuit.karate.ScriptBindings;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StringUtils;
import com.intuit.karate.exception.KarateException;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class FeatureProvider {

    private final FeatureWrapper feature;
    private final KarateBackend backend;
    private final boolean ssl;
    private final boolean corsEnabled;

    public FeatureProvider(FeatureWrapper feature) {
        this(feature, null, false);
    }

    public FeatureProvider(FeatureWrapper feature, Map<String, Object> vars) {
        this(feature, vars, false);
    }

    public boolean isSsl() {
        return ssl;
    }

    public boolean isCorsEnabled() {
        return corsEnabled;
    }

    public final ScriptContext getContext() {
        return backend.getStepDefs().context;
    }

    private static void putBinding(String name, ScriptContext context) {
        String function = "function(s){ return " + ScriptBindings.KARATE + "." + name + "(s) }";
        context.getVars().put(name, Script.evalJsExpression(function, context));
    }

    public FeatureProvider(FeatureWrapper feature, Map<String, Object> vars, boolean ssl) {
        this.feature = feature;
        this.ssl = ssl;
        CallContext callContext = new CallContext(null, false);
        backend = CucumberUtils.getBackendWithGlue(feature, callContext);
        ScriptContext context = getContext();
        putBinding(ScriptBindings.PATH_MATCHES, context);
        putBinding(ScriptBindings.METHOD_IS, context);
        putBinding(ScriptBindings.PARAM_VALUE, context);
        putBinding(ScriptBindings.TYPE_CONTAINS, context);
        putBinding(ScriptBindings.ACCEPT_CONTAINS, context);
        putBinding(ScriptBindings.BODY_PATH, context);
        if (vars != null) {
            ScriptValueMap backendVars = backend.getVars();
            vars.forEach((k, v) -> backendVars.put(k, v));
        }
        call(feature, backend, CallType.BACKGROUND_ONLY);
        // this is a special case, we support the auto-handling of cors
        // only if '* configure cors = true' has been done in the Background
        corsEnabled = context.getConfig().isCorsEnabled();
    }

    public ScriptValueMap handle(ScriptValueMap vars) {
        backend.getVars().putAll(vars);
        call(feature, backend, CallType.SCENARIO_ONLY);
        return getContext().getVars();
    }

    private static ScriptValueMap call(FeatureWrapper feature, KarateBackend backend, CallType callType) {
        boolean matched = callType != CallType.SCENARIO_ONLY;
        for (FeatureSection section : feature.getSections()) {
            if (section.isOutline()) {
                ScenarioOutlineWrapper outline = section.getScenarioOutline();
                for (ScenarioWrapper scenario : outline.getScenarios()) {
                    call(scenario, backend, callType);
                }
            } else {
                ScenarioWrapper scenario = section.getScenario();
                if (callType == CallType.SCENARIO_ONLY) {
                    if (isMatchingScenario(scenario, backend)) {
                        call(scenario, backend, callType);
                        matched = true;
                        break; // only execute first matching scenario
                    }
                } else {
                    call(scenario, backend, callType);
                }
            }
        }
        if (!matched) {
            backend.getEnv().logger.warn("no scenarios matched");
        }
        return backend.getStepDefs().context.getVars();
    }

    private static boolean isMatchingScenario(ScenarioWrapper scenario, KarateBackend backend) {
        String expression = StringUtils.trimToNull(scenario.getNameAndDescription());
        ScriptContext context = backend.getStepDefs().context;
        if (expression == null) {
            context.logger.debug("scenario matched: (empty)");
            return true;
        }
        try {
            ScriptValue sv = Script.evalJsExpression(expression, context);
            if (sv.isBooleanTrue()) {
                context.logger.debug("scenario matched: {}", expression);
                return true;
            } else {
                context.logger.debug("scenario skipped: {}", expression);
                return false;
            }
        } catch (Exception e) {
            context.logger.warn("scenario match evaluation failed: {}", e.getMessage());
            return false;
        }
    }

    private static void call(ScenarioWrapper scenario, KarateBackend backend, CallType callType) {
        for (StepWrapper step : scenario.getSteps()) {
            if (callType == CallType.BACKGROUND_ONLY && !step.isBackground()) {
                continue;
            }
            if (callType == CallType.SCENARIO_ONLY && step.isBackground()) {
                continue;
            }
            StepResult result = CucumberUtils.runCalledStep(step, backend);
            if (result.isAbort()) {
                backend.getEnv().logger.debug("abort at {}:{}", scenario.getFeature().getPath(), step.getStep().getLine());
                break;
            }
            if (!result.isPass()) {
                FeatureWrapper feature = scenario.getFeature();
                String scenarioName = StringUtils.trimToNull(scenario.getScenario().getGherkinModel().getName());
                String message = "called: " + feature.getPath();
                if (scenarioName != null) {
                    message = message + ", scenario: " + scenarioName;
                }
                message = message + ", line: " + step.getStep().getLine();
                // verbose for server / mock
                backend.getEnv().logger.error("{}, {}", result.getError(), message);
                throw new KarateException(message, result.getError());
            }
        }
    }

}
