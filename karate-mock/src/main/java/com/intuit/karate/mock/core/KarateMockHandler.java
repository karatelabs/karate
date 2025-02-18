/*
 * The MIT License
 *
 * Copyright 2022 Karate Labs Inc.
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
package com.intuit.karate.mock.core;

import com.intuit.karate.*;
import com.intuit.karate.core.*;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class KarateMockHandler implements KarateMockCallback {

    private static final Logger logger = LoggerFactory.getLogger(KarateMockHandler.class);

    private static final String PREDICATE = "predicate";

    private static final String KARATE_MOCK_REQUEST_MESSAGE = "karateMockRequestMessage";

    private static final String KARATE_MOCK_RESPONSE_MESSAGE = "karateMockResponseMessage";

    private final LinkedHashMap<Feature, ScenarioRuntime> scenarioRuntimes = new LinkedHashMap<>(); // feature + holds global config and vars
    private final Map<String, Variable> globals = new HashMap<>();

    protected static final ThreadLocal<KarateMessage> LOCAL_REQUEST = new ThreadLocal<>();

    public KarateMockHandler(Feature feature) {
        this(feature, null);
    }

    public KarateMockHandler(Feature feature, Map<String, Object> args) {
        this(Collections.singletonList(feature), args);
    }

    public KarateMockHandler(List<Feature> features) {
        this(features, null);
    }

    public KarateMockHandler(List<Feature> features, Map<String, Object> args) {
        features.forEach(feature -> {
            ScenarioRuntime runtime = initRuntime(feature, args);
            Method method;
            try {
                method = ScenarioEngine.class.getDeclaredMethod("shallowCloneVariables");
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            method.setAccessible(true);  // bypass access modifiers
            try {
                globals.putAll((Map<? extends String, ? extends Variable>) method.invoke(runtime.engine));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            runtime.logger.info("karate mock server initialized: {}", feature);
            scenarioRuntimes.put(feature, runtime);            
        });
    }
    
    public Object getVariable(String name) {
        if (globals.containsKey(name)) {
            Variable v = globals.get(name);
            if (v != null) {
                return JsValue.fromJava(v.getValue());
            }
        }
        return null;
    }

    private ScenarioRuntime initRuntime(Feature feature, Map<String, Object> args) {
        FeatureRuntime featureRuntime = FeatureRuntime.of(Suite.forTempUse(HttpClientFactory.DEFAULT), new FeatureCall(feature), args);
        FeatureSection section = new FeatureSection();
        section.setIndex(-1); // TODO util for creating dummy scenario
        Scenario dummy = new Scenario(feature, section, -1);
        section.setScenario(dummy);
        ScenarioRuntime runtime = new ScenarioRuntime(featureRuntime, dummy);
        runtime.logger.setLogOnly(true);
        runtime.engine.setVariable(PREDICATE, (Function<Predicate, Boolean>) this::predicate);
        runtime.engine.init();
        if (feature.isBackgroundPresent()) {
            // if we are within a scenario already e.g. karate.start(), preserve context
            ScenarioEngine prevEngine = ScenarioEngine.get();
            try {
                ScenarioEngine.set(runtime.engine);
                for (Step step : feature.getBackground().getSteps()) {
                    Result result = StepRuntime.execute(step, runtime.actions);
                    if (result.isFailed()) {
                        String message = "mock-server background failed - " + feature + ":" + step.getLine();
                        runtime.logger.error(message);
                        throw new KarateException(message, result.getError());
                    }
                }
            } finally {
                ScenarioEngine.set(prevEngine);
            }
        }        
        return runtime;
    }

    private static final Result PASSED = Result.passed(0, 0);

    @Override
    public KarateMessage receive(KarateMessage req) {
        // snapshot existing thread-local to restore
        ScenarioEngine prevEngine = ScenarioEngine.get();
        for (Map.Entry<Feature, ScenarioRuntime> entry : scenarioRuntimes.entrySet()) {
            Feature feature = entry.getKey();
            ScenarioRuntime runtime = entry.getValue();
            // important for graal to work properly
            Thread.currentThread().setContextClassLoader(runtime.featureRuntime.suite.classLoader);
            LOCAL_REQUEST.set(req);
            ScenarioEngine engine = initEngine(runtime, globals, req);
            for (FeatureSection fs : feature.getSections()) {
                if (fs.isOutline()) {
                    runtime.logger.warn("skipping scenario outline - {}:{}", feature, fs.getScenarioOutline().getLine());
                    break;
                }
                Scenario scenario = fs.getScenario();
                if (isMatchingScenario(scenario, engine)) {
                    Variable karateMockResponseMessage;
                    ScenarioActions actions = new ScenarioActions(engine);
                    Result result = executeScenarioSteps(feature, runtime, scenario, actions);
                    engine.mockAfterScenario();
                    karateMockResponseMessage = engine.vars.remove(KARATE_MOCK_RESPONSE_MESSAGE);
                    Method method;
                    try {
                        method = ScenarioEngine.class.getDeclaredMethod("shallowCloneVariables");
                    } catch (NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                    method.setAccessible(true);  // bypass access modifiers
                    try {
                        globals.putAll((Map<? extends String, ? extends Variable>) method.invoke(engine));
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                    KarateMessage res = new KarateMessage();
                    if (result.isFailed()) {
                        karateMockResponseMessage = new Variable(result.getError().getMessage());
                    }
                    if (karateMockResponseMessage != null && !karateMockResponseMessage.isNull()) {
                        res.setBody(karateMockResponseMessage.getAsByteArray());
                    }
                    if (prevEngine != null) {
                        ScenarioEngine.set(prevEngine);
                    }
                    return res;
                }
            }
        }
        logger.warn("no scenarios matched, returning 404: {}", req); // NOTE: not logging with engine.logger
        if (prevEngine != null) {
            ScenarioEngine.set(prevEngine);
        }
        return new KarateMessage();
    }
    
    private static ScenarioEngine initEngine(ScenarioRuntime runtime, Map<String, Variable> globals, KarateMessage request) {
        ScenarioEngine engine = new ScenarioEngine(runtime.engine.getConfig(), runtime, new HashMap(globals), runtime.logger);        
        engine.init();
        engine.setVariable(KARATE_MOCK_REQUEST_MESSAGE, request);
        runtime.featureRuntime.setMockEngine(engine);
        ScenarioEngine.set(engine);
        return engine;
    }

    private Result executeScenarioSteps(Feature feature, ScenarioRuntime runtime, Scenario scenario, ScenarioActions actions) {
        Result result = PASSED;
        for (Step step : scenario.getSteps()) {
            result = StepRuntime.execute(step, actions);
            if (result.isAborted()) {
                runtime.logger.debug("abort at {}:{}", feature, step.getLine());
                break;
            }
            if (result.isFailed()) {
                String message = "server-side scenario failed, " + feature + ":" + step.getLine()
                        + "\n" + step.toString() + "\n" + result.getError().getMessage();
                runtime.logger.error(message);
                break;
            }
        }
        return result;
    }

    private boolean isMatchingScenario(Scenario scenario, ScenarioEngine engine) {
        String expression = StringUtils.trimToNull(scenario.getName() + scenario.getDescription());
        if (expression == null) {
            engine.logger.debug("default scenario matched at line: {} - {}", scenario.getLine(), engine.getVariable(ScenarioEngine.REQUEST_URI));
            return true;
        }
        try {
            Variable v = engine.evalJs(expression);
            if (v.isTrue()) {
                engine.logger.debug("scenario matched at line {}: {}", scenario.getLine(), expression);
                return true;
            } else {
                engine.logger.trace("scenario skipped at line {}: {}", scenario.getLine(), expression);
                return false;
            }
        } catch (Exception e) {
            engine.logger.warn("scenario match evaluation failed at line {}: {} - {}", scenario.getLine(), expression, e + "");
            return false;
        }
    }

    public boolean predicate(Predicate predicate) {
        return predicate.check(LOCAL_REQUEST.get());
    }
}
