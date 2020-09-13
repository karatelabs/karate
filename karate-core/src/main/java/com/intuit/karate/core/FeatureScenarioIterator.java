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

import com.intuit.karate.Script;
import com.intuit.karate.ScriptValue;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class FeatureScenarioIterator implements Iterator<ScenarioExecutionUnit> {

    private final ExecutionContext exec;
    private final Iterator<FeatureSection> sections;

    // state
    private Iterator<Scenario> scenarios;
    private Scenario currentScenario;
    private ScenarioExecutionUnit next;

    // dynamic
    private ScriptValue expressionValue;
    private int index;
    ScenarioExecutionUnit bgUnit;

    public FeatureScenarioIterator(ExecutionContext exec, Iterator<FeatureSection> sections) {
        this.exec = exec;
        this.sections = sections;
    }

    @Override
    public boolean hasNext() {
        if (next != null) {
            return true;
        }
        if (currentScenario == null) {
            if (scenarios == null) {
                if (sections.hasNext()) {
                    FeatureSection section = sections.next();
                    if (section.isOutline()) {
                        scenarios = section.getScenarioOutline().getScenarios().iterator();
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
                bgUnit = null;
            } else {
                scenarios = null;
                return hasNext();
            }
        }
        if (currentScenario.isDynamic()) {
            if (bgUnit == null) {
                bgUnit = new ScenarioExecutionUnit(currentScenario, null, exec);
                bgUnit.run();
                if (bgUnit.getContext() == null || bgUnit.isStopped()) { // karate-config.js || background failed
                    currentScenario = null;
                    next = bgUnit;
                    return true; // exit early
                }
            }
            if (expressionValue == null) {
                String expression = currentScenario.getDynamicExpression();
                try {
                    expressionValue = Script.evalKarateExpression(expression, bgUnit.getContext());
                    if (expressionValue.isListLike() || expressionValue.isFunction()) {
                        // all good
                    } else {
                        throw new RuntimeException("result is neither list nor function: " + expressionValue);
                    }
                } catch (Exception e) {
                    String message = "dynamic expression evaluation failed: " + expression;
                    bgUnit.result.addError(message, e);
                    currentScenario = null;
                    next = bgUnit;
                    return true; // exit early
                }
            }
            final int rowIndex = index++;
            ScriptValue rowValue;
            if (expressionValue.isFunction()) {
                try {
                    rowValue = expressionValue.invokeFunction(bgUnit.getContext(), rowIndex);
                } catch (Exception e) {
                    String message = "dynamic function expression evaluation failed at index " + rowIndex + ": " + e.getMessage();
                    bgUnit.result.addError(message, e);
                    currentScenario = null;
                    next = bgUnit;
                    return true; // exit early                    
                }
            } else { // is list
                List list = expressionValue.getAsList();
                if (rowIndex >= list.size()) {
                    currentScenario = null;
                    return hasNext();
                }
                rowValue = new ScriptValue(list.get(rowIndex));
            }
            if (rowValue.isMapLike()) {
                Scenario dynamic = currentScenario.copy(rowIndex); // this will set exampleIndex
                dynamic.setBackgroundDone(true);
                Map<String, Object> map = rowValue.getAsMap();
                dynamic.setExampleData(map); // and here we set exampleData
                map.forEach((k, v) -> {
                    ScriptValue sv = new ScriptValue(v);
                    dynamic.replace("<" + k + ">", sv.getAsString());
                });
                next = new ScenarioExecutionUnit(dynamic, bgUnit.result.getStepResults(), exec, bgUnit.getContext());
                return true;
            } else { // assume that this is signal to stop the dynamic scenario outline
                bgUnit.getContext().logger.debug("dynamic expression complete at index: {}, not map-like: {}", rowIndex, rowValue);
                currentScenario = null;
                return hasNext();
            }
        } else {
            next = new ScenarioExecutionUnit(currentScenario, null, exec);
            currentScenario = null;
            return true;
        }
    }

    @Override
    public ScenarioExecutionUnit next() {
        ScenarioExecutionUnit temp = next;
        next = null;
        return temp;
    }

}
