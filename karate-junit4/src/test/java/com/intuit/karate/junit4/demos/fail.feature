@ignore
Feature: deliberate failure to test log / stack trace

Scenario: test failure
* def a = 1
* def b = 2
* match a == b

Scenario: called failure
* call read('fail-called.feature')
