Feature: runtime metadata
    such as the feature file name and scenario name

Scenario: first scenario
* def info = karate.info
* print 'info:', info
* match info contains { scenarioName: 'first scenario', featureFileName: 'info.feature' }
