Feature:

Background:
* def cats = [{name: 'Bob', age: 5}, {name: 'Nyan', age: 7}]

Scenario Outline: name is <name> and age is <age>
* def name = '<name>'
* match name == "#? _ == 'Bob' || _ == 'Nyan'"
* def title = karate.info.scenarioName

Examples:
| cats |
