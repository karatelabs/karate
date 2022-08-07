Feature:

Background:
* print 'in background', __num

@setup
Scenario:
* print 'in setup'
* def data = [{a:1}, {a:2}]

Scenario Outline:
* print __row

Examples:
| karate.setup().data |
