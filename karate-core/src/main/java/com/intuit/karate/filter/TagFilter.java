package com.intuit.karate.filter;

import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.CucumberTagStatement;

@FunctionalInterface
public interface TagFilter {

    boolean filter(CucumberFeature feature, CucumberTagStatement cucumberTagStatement) throws TagFilterException;

}
