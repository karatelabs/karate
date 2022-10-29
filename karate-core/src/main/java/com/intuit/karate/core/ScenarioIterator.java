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
package com.intuit.karate.core;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;

/**
 *
 * @author pthomas3
 */
public class ScenarioIterator implements Spliterator<ScenarioRuntime> {

    private final FeatureRuntime featureRuntime;
    private final Iterator<FeatureSection> sections;

    // state
    private Iterator<Scenario> scenarios;
    private Scenario currentScenario;

    // dynamic
    private ScenarioRuntime dynamicRuntime;
    private Variable expressionValue;
    private int index;

    public ScenarioIterator(FeatureRuntime featureRuntime) {
        this.featureRuntime = featureRuntime;
        this.sections = featureRuntime.featureCall.feature.getSections().iterator();
    }

    public Stream<ScenarioRuntime> filterSelected() {
        return StreamSupport.stream(this, false).filter(sr -> sr.selectedForExecution);
    }

    public ScenarioRuntime first() {
        return filterSelected().findFirst().get();
    }

    @Override
    public boolean tryAdvance(Consumer<? super ScenarioRuntime> action) {
        if (currentScenario == null) {
            if (scenarios == null) {
                if (sections.hasNext()) {
                    FeatureSection section = sections.next();
                    if (section.isOutline()) {
                        scenarios = section.getScenarioOutline().getScenarios(featureRuntime).iterator();
                    } else {
                        scenarios = Collections.singletonList(section.getScenario()).iterator();
                    }
                } else {
                    return false;
                }
            }
            if (scenarios.hasNext()) {
                currentScenario = scenarios.next();
                index = 0;
                expressionValue = null;
            } else {
                scenarios = null;
                return tryAdvance(action);
            }
        }
        if (currentScenario.isDynamic()) {
            Logger logger = FeatureRuntime.logger;
            if (expressionValue == null) {
                String expression = currentScenario.getDynamicExpression();
                dynamicRuntime = new ScenarioRuntime(featureRuntime, currentScenario);
                ScenarioEngine prevEngine = ScenarioEngine.get();
                try {
                    ScenarioEngine.set(dynamicRuntime.engine);
                    dynamicRuntime.engine.init();
                    expressionValue = dynamicRuntime.engine.evalJs(expression);
                    if (expressionValue.isList() || expressionValue.isJsOrJavaFunction()) {
                        // all good
                    } else {
                        throw new RuntimeException("result is neither list nor function: " + expressionValue);
                    }
                } catch (Exception e) {
                    String message = currentScenario + " dynamic expression evaluation failed: " + expression;
                    logger.error(message);
                    dynamicRuntime.result.addFakeStepResult(message, e);
                    currentScenario = null;
                    action.accept(dynamicRuntime);
                    return true; // exit early
                } finally {
                    ScenarioEngine.set(prevEngine);
                }
            }
            final int rowIndex = index++;
            Variable rowValue;
            if (expressionValue.isJsOrJavaFunction()) {
                ScenarioEngine prevEngine = ScenarioEngine.get();
                try {
                    ScenarioEngine.set(dynamicRuntime.engine);
                    rowValue = dynamicRuntime.engine.executeFunction(expressionValue, rowIndex);
                } catch (Exception e) {
                    String message = currentScenario + " dynamic function expression evaluation failed at index " + rowIndex + ": " + e.getMessage();
                    logger.error(message);
                    dynamicRuntime.result.addFakeStepResult(message, e);
                    currentScenario = null;
                    action.accept(dynamicRuntime);
                    return true; // exit early                    
                } finally {
                    ScenarioEngine.set(prevEngine);
                }
            } else { // is list
                List list = expressionValue.getValue();
                if (rowIndex >= list.size()) {
                    currentScenario = null;
                    return tryAdvance(action);
                }
                rowValue = new Variable(list.get(rowIndex));
            }
            if (rowValue.isMap()) {
                Scenario dynamic = currentScenario.copy(rowIndex); // this will set exampleIndex
                Map<String, Object> map = rowValue.getValue();
                dynamic.setExampleData(map); // and here we set exampleData
                map.forEach((k, v) -> {
                    Variable var = new Variable(v);
                    dynamic.replace("<" + k + ">", var.getAsString());
                });
                action.accept(new ScenarioRuntime(featureRuntime, dynamic));
                return true;
            } else { // assume that this is signal to stop the dynamic scenario outline
                dynamicRuntime.logger.info("dynamic expression complete at index: {}, not map-like: {}", rowIndex, rowValue);
                currentScenario = null;
                return tryAdvance(action);
            }
        } else {
            action.accept(new ScenarioRuntime(featureRuntime, currentScenario));
            currentScenario = null;
            return true;
        }
    }

    @Override
    public Spliterator<ScenarioRuntime> trySplit() {
        return null;
    }

    @Override
    public long estimateSize() {
        return 0;
    }

    @Override
    public int characteristics() {
        return 0;
    }

}
