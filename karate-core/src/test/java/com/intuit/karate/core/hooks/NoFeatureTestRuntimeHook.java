package com.intuit.karate.core.hooks;

import com.intuit.karate.core.FeatureRuntime;

public class NoFeatureTestRuntimeHook extends TestRuntimeHook {

    @Override
    public boolean beforeFeature(FeatureRuntime fr) {
        return false;
    }

    @Override
    public void afterFeature(FeatureRuntime fr) {
        // don't count if if not executing
    }
}
