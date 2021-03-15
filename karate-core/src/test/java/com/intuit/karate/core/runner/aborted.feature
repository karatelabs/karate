@ignore
Feature: abort2pass1
Background:
Given def a = 1

Scenario: skip-pass
* karate.abort()
* assert a == 1

Scenario: skip-fail
* karate.abort()
* assert a == 2

Scenario: noskip
Then assert a != 3
And assert a != 4

Scenario: skip-pass-config
* configure abortedStepsShouldPass = true
* karate.abort()
* assert a == 5
