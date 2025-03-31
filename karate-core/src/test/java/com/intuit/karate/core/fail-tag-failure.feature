Feature: fail tag failure

@fail
Scenario:
* def a = 1 + 2
* match a == 3
