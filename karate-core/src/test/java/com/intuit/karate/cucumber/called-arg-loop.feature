@ignore
Feature:

Scenario: loop arg
* match __arg == karate.get('foos[' + __loop + ']')


