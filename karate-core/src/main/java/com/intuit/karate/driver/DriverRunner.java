package com.intuit.karate.driver;

import com.intuit.karate.core.ScenarioRuntime;

import java.util.Map;

@FunctionalInterface
public interface DriverRunner<D extends Driver> {
    D start(Map<String, Object> options, ScenarioRuntime sr);
}