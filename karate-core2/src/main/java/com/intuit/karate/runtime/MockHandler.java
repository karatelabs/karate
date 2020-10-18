/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.runtime;

import com.intuit.karate.StringUtils;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureSection;
import com.intuit.karate.core.Result;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.Step;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.server.Request;
import com.intuit.karate.server.Response;
import com.intuit.karate.server.ServerHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class MockHandler implements ServerHandler {

    private final Feature feature;
    private final String featureName;
    private final ScenarioRuntime runtime; // holds global config and vars

    private static final String PATH_MATCHES = "pathMatches";
    private static final String METHOD_IS = "methodIs";
    private static final String TYPE_CONTAINS = "typeContains";
    private static final String ACCEPT_CONTAINS = "acceptContains";
    private static final String HEADER_CONTAINS = "headerContains";
    private static final String PARAM_VALUE = "paramValue";
    private static final String PARAM_EXISTS = "paramExists";
    private static final String PATH_PARAMS = "pathParams";
    private static final String BODY_PATH = "bodyPath";

    protected static final ThreadLocal<Request> REQUEST = new ThreadLocal<Request>();

    public MockHandler(Feature feature) {
        this.feature = feature;
        featureName = feature.getPath().toFile().getName();
        FeatureRuntime featureRuntime = new FeatureRuntime(new SuiteRuntime(), feature, true);
        FeatureSection section = new FeatureSection();
        section.setIndex(-1); // TODO util for creating dummy scenario
        Scenario dummy = new Scenario(feature, section, -1);
        section.setScenario(dummy);
        runtime = new ScenarioRuntime(featureRuntime, dummy);
        runtime.engine.setVariable(PATH_MATCHES, (Function<String, Boolean>) this::pathMatches);
        if (feature.isBackgroundPresent()) {
            for (Step step : feature.getBackground().getSteps()) {
                Result result = StepRuntime.execute(step, runtime.actions);
                if (result.isFailed()) {
                    String message = "mock-server background failed - " + featureName + ":" + step.getLine();
                    runtime.logger.error(message);
                    throw new KarateException(message, result.getError());
                }
            }
        }
        runtime.logger.info("mock server initialized: {}", featureName);
    }

    @Override
    public Response handle(Request req) {
        REQUEST.set(req);
        ScenarioEngine engine = new ScenarioEngine(runtime);
        ScenarioEngine.LOCAL.set(engine);
        engine.init();
        for (FeatureSection fs : feature.getSections()) {
            if (fs.isOutline()) {
                runtime.logger.warn("skipping scenario outline - {}:{}", featureName, fs.getScenarioOutline().getLine());
                break;
            }
            Scenario scenario = fs.getScenario();
            if (isMatchingScenario(scenario, engine)) {
                Response res = new Response(200);
                Variable response;
                ScenarioActions actions = new ScenarioActions(engine);
                synchronized (runtime) {
                    for (Step step : scenario.getSteps()) {
                        Result result = StepRuntime.execute(step, actions);
                        if (result.isAborted()) {
                            runtime.logger.debug("abort at {}:{}", featureName, step.getLine());
                            break;
                        }
                        if (result.isFailed()) {
                            String message = "server-side scenario failed - " + featureName + ":" + step.getLine();
                            runtime.logger.error(message);
                            throw new KarateException(message, result.getError());
                        }
                    }
                    Map<String, Variable> vars = runtime.engine.vars;
                    response = vars.remove(VariableNames.RESPONSE);
                }
                if (response != null) {
                    res.setBody(response.getAsByteArray());
                }
                return res;
            }
        }
        runtime.logger.warn("no scenarios matched, returning 404: {}", req);
        return new Response(404);
    }

    private boolean isMatchingScenario(Scenario scenario, ScenarioEngine engine) {
        String expression = StringUtils.trimToNull(scenario.getName() + scenario.getDescription());
        if (expression == null) {
            runtime.logger.debug("default scenario matched at line: {}", scenario.getLine());
        }
        try {
            Variable v = engine.evalJs(expression);
            if (v.isTrue()) {
                engine.logger.debug("scenario matched at line {}: {}", scenario.getLine(), expression);
                return true;
            } else {
                engine.logger.debug("scenario skipped at line {}: {}", scenario.getLine(), expression);
                return false;
            }
        } catch (Exception e) {
            engine.logger.warn("scenario match evaluation failed at line {}: {} - {}", scenario.getLine(), expression, e + "");
            return false;
        }
    }

    public boolean pathMatches(String pattern) {
        String uri = REQUEST.get().getPath();
        Map<String, String> pathParams = HttpUtils.parseUriPattern(pattern, uri);
        if (pathParams == null) {
            return false;
        } else {
            ScenarioEngine.LOCAL.get().setVariable(PATH_PARAMS, pathParams);
            return true;
        }
    }

}
