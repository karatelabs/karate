@ignore
Feature: abort2pass1
Background:
Given def a = 1

Scenario: skip-pass
* eval karate.abort()
* assert a == 1

Scenario: skip-fail
* eval karate.abort()
* assert a == 2

Scenario: noskip
Then assert a != 3
And assert a != 4
