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
package com.intuit.karate.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author OwenK2
 */
public class ScenarioOutlineResult {

    final private ScenarioOutline scenarioOutline;
    final private ScenarioRuntime runtime;

    public ScenarioOutlineResult(ScenarioOutline scenarioOutline, ScenarioRuntime runtime) {
        // NOTE: this value can be null, in which case the scenario is not from an outline
        this.scenarioOutline = scenarioOutline;
        this.runtime = runtime;
    }

    public Map<String, Object> toKarateJson() {
        if (scenarioOutline == null) return null;
        Map<String, Object> map = new HashMap();
        map.put("name", scenarioOutline.getName());
        map.put("description", scenarioOutline.getDescription());
        map.put("line", scenarioOutline.getLine());
        map.put("sectionIndex", scenarioOutline.getSection().getIndex());
        map.put("exampleTableCount", scenarioOutline.getNumExampleTables());
        map.put("exampleTables", scenarioOutline.getAllExampleData());
        map.put("numScenariosToExecute", scenarioOutline.getNumScenarios());
        
        // Get results of other examples in this outline
        List<Map<String, Object>> scenarioResults = new ArrayList();
        if (runtime.featureRuntime != null && runtime.featureRuntime.result != null) {
            // Add all past results
            boolean needToAddRecent = runtime.result != null;
            for(ScenarioResult result : runtime.featureRuntime.result.getScenarioResults()) {
                if (result.getScenario().getSection().getIndex() == scenarioOutline.getSection().getIndex()) {
                    scenarioResults.add(result.toInfoJson());
                    if(result.equals(runtime.result)) {
                        needToAddRecent = false;
                    }
                }
            }

            // Add most recent result if we haven't already (and it's not null)
            if (needToAddRecent) {
                scenarioResults.add(runtime.result.toInfoJson());
            }
        }
        map.put("scenarioResults", scenarioResults);
        map.put("numScenariosExecuted", scenarioResults.size());

        return map;
    }

}
