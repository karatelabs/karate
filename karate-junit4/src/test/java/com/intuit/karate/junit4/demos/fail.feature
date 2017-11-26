@ignore
Feature: deliberate failure to test log / stack trace

Scenario: test failure
* print 'foo'
* def a = 1
* def b = 2
* match a == b

Scenario: called failure
* call read('fail-called.feature') { a: 2 }

Scenario: called failure loop
* def list = [{a: 1}, {a: 2}, {a: 3}]
* call read('fail-called.feature') list
