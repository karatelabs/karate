Feature: set multiple variables in one shot

Scenario: using json
* def json = { foo: 'bar', a: 1, b: true }
* karate.set(json)
* match foo == 'bar'
* match a == 1
* match b == true
