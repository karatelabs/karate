@ignore
Feature: response delay

Background:
* configure responseDelay = 300
* def delay = function(){ karate.set('responseDelay', 200.0) }

Scenario: pathMatches('/feature-delay')
* def response = 'feature-delay'

Scenario: pathMatches('/after-scenario-delay')
* def response = 'specific-delay'
* def afterScenario = delay

Scenario: pathMatches('/scenario-delay')
* def response = 'specific-delay'
* def responseDelay = 100
