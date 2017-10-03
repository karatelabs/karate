package com.intuit.karate.filter;

import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.CucumberTagStatement;

/**
 * Interface that should be implemented to plug in
 * any filtering based on cucumber tags
 *
 * @author ssishtla
 */

@FunctionalInterface
public interface TagFilter {

    /**
     * Method to be over-ridden to evaluate if a feature/scenario
     * should be skipped for execution
     *
     * @param feature
     * @param cucumberTagStatement
     * @return true - indicates feature should be skipped from
     *         execution, otherwise false
     * @throws TagFilterException - when thrown, stops
     *         execution of all features
     */
    boolean filter(CucumberFeature feature, CucumberTagStatement cucumberTagStatement) throws TagFilterException;
}
