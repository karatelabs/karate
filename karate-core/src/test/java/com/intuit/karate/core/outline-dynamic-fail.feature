Feature:

# @setup
Scenario:
* def data = [{a: 1}, {a: 2}]

Scenario Outline:
* print __row

Examples:
| karate.setup().data |
