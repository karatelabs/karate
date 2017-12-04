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
* def list = [{a: 1}, {a: 2}, {a: 3}]
* call read('fail-called.feature') list
