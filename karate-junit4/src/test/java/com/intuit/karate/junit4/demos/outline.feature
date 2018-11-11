Feature:

Scenario Outline: name is <name> and age is <age>
* def name = '<name>'
* match name == "#? _ == 'Bob' || _ == 'Nyan'"
* def title = karate.info.scenarioName
* print title

Examples:
| name | age |
| Bob  | 5   |
| Nyan | 6   |
