package com.intuit.karate.core.runner.hooks;

import com.intuit.karate.core.Tag;
import com.intuit.karate.RuntimeHook;
import com.intuit.karate.core.ScenarioRuntime;

/**
 *
 * @author pthomas3
 */
public class MandatoryTagHook implements RuntimeHook {

    @Override
    public boolean beforeScenario(ScenarioRuntime sr) {
        if (sr.caller.depth > 0) {
            return true; // only enforce tags for top-level scenarios (not called ones)
        }
        boolean found = false;
        for (Tag tag : sr.tags) {
            if ("testId".equals(tag.getName())) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new RuntimeException("testId tag not present at line: " + sr.scenario.getLine());
        }
        return true;
    }
    
}
