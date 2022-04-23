package com.intuit.karate.core.runner.hooks;

import com.intuit.karate.RuntimeHook;
import com.intuit.karate.core.ScenarioRuntime;

/**
 *
 * @author peter
 */
public class SkipHook implements RuntimeHook {

    @Override
    public boolean beforeScenario(ScenarioRuntime sr) {
        return sr.scenario.getName().contains("one");
    }        
    
}
