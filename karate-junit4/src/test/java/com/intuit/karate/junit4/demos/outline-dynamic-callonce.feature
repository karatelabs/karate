Feature:

@setup
Scenario:
* def data = [{a: 1}, {a: 2}, {a: 3}]

Scenario Outline: row <a>
* print 'a: ', a

Examples:
| karate.setup().data |
