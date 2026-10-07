Feature: a helper that matches against the request data a mock hands it

@js
Scenario:
* def res = karate.match('string', poc)

@keyword
Scenario:
* match 'string' == poc
