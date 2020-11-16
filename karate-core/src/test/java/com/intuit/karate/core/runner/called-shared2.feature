@ignore
Feature:

Scenario:
* match foo == __loop
* match __arg == { foo: '#(__loop)' }
