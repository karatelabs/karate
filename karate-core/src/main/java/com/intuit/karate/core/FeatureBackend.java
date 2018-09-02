/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.core;

import com.intuit.karate.CallContext;
import com.intuit.karate.Script;
import com.intuit.karate.ScriptBindings;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StepDefs;
import com.intuit.karate.StringUtils;
import com.intuit.karate.exception.KarateException;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class FeatureBackend {

    private final Feature feature;
    private final StepDefs stepDefs;
    private final boolean ssl;
    private final boolean corsEnabled;
    
    private final ScriptContext context;
    private final String featureName;

    private static void putBinding(String name, ScriptContext context) {
        String function = "function(s){ return " + ScriptBindings.KARATE + "." + name + "(s) }";
        context.getVars().put(name, Script.evalJsExpression(function, context));
    }

    public boolean isCorsEnabled() {
        return corsEnabled;
    }

    public boolean isSsl() {
        return ssl;
    }

    public ScriptContext getContext() {
        return context;
    }

    public FeatureBackend(Feature feature) {
        this(feature, null, false);
    }

    public FeatureBackend(Feature feature, Map<String, Object> vars) {
        this(feature, vars, false);
    }

    public FeatureBackend(Feature feature, Map<String, Object> vars, boolean ssl) {
        this.feature = feature;
        featureName = feature.getFile().getName();
        this.ssl = ssl;
        CallContext callContext = new CallContext(null, false);
        ScriptEnv env = ScriptEnv.forEnvAndFeatureFile(null, feature.getFile());
        stepDefs = new StepDefs(env, callContext);
        context = stepDefs.context;
        putBinding(ScriptBindings.PATH_MATCHES, context);
        putBinding(ScriptBindings.METHOD_IS, context);
        putBinding(ScriptBindings.PARAM_VALUE, context);
        putBinding(ScriptBindings.TYPE_CONTAINS, context);
        putBinding(ScriptBindings.ACCEPT_CONTAINS, context);
        putBinding(ScriptBindings.BODY_PATH, context);
        if (vars != null) {
            ScriptValueMap backendVars = context.getVars();
            vars.forEach((k, v) -> backendVars.put(k, v));
        }
        // the background is evaluated one-time
        if (feature.isBackgroundPresent()) {
            for (Step step : feature.getBackground().getSteps()) {
                Result result = Engine.executeStep(step, stepDefs);
                if (result.isFailed()) {

                    String message = "server-side background init failed - " + featureName + ":" + step.getLine();
                    stepDefs.context.logger.error(message);
                    throw new KarateException(message, result.getError());
                }
            }
        }
        // this is a special case, we support the auto-handling of cors
        // only if '* configure cors = true' has been done in the Background
        corsEnabled = context.getConfig().isCorsEnabled();
    }

    public ScriptValueMap handle(ScriptValueMap args) {
        boolean matched = false;
        ScriptValueMap vars = stepDefs.context.getVars();
        vars.putAll(args);
        for (FeatureSection fs : feature.getSections()) {            
            if (fs.isOutline()) {
                stepDefs.context.logger.warn("skipping scenario outline - {}:{}", featureName, fs.getScenarioOutline().getLine());
                break;
            }
            Scenario scenario = fs.getScenario();
            if (isMatchingScenario(scenario)) {
                matched = true;
                for (Step step : scenario.getSteps()) {
                    Result result = Engine.executeStep(step, stepDefs);
                    if (result.isAborted()) {
                        stepDefs.context.logger.debug("abort at {}:{}", featureName, step.getLine());
                        break;
                    }
                    if (result.isFailed()) {
                        String message = "server-side scenario failed - " + featureName + ":" + step.getLine();
                        stepDefs.context.logger.error(message);
                        throw new KarateException(message, result.getError());
                    }
                }
                break; // process only first matching scenario
            }
        }
        if (!matched) {
            context.logger.warn("no scenarios matched");
        }
        return vars;
    }

    private boolean isMatchingScenario(Scenario scenario) {
        String expression = StringUtils.trimToNull(scenario.getName() + scenario.getDescription());
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

}
