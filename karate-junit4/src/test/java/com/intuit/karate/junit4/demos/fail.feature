@ignore
Feature: deliberate failure to test log / stack trace

Scenario: test failure
* print 'foo'
* def a = 1
* def b = 2
* match a == b

Scenario: called success, nested call with background
* call read('fail-called2.feature')

Scenario: called failure loop
    and some extra line of description to test reports
* def list = [{a: 1}, {a: 2}, {a: 3}]
* call read('fail-called.feature') list

Scenario: called outline failed
* call read('fail-outline.feature')

Scenario: calling feature file that does not exist
* call read('waldo.feature')

Scenario: match on variable that does not exist
* match blah == true
