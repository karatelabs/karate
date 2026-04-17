Feature: Skipped Scenarios Demo

@demo
Scenario: Aborted before any step
* karate.abort()
* def value = 42
* match value == 42

@demo
Scenario: Normal passing scenario
* def value = 1
* match value == 1

@demo
Scenario: Aborted mid-scenario
* def value = 1
* match value == 1
* karate.abort()
* def ignored = 999
