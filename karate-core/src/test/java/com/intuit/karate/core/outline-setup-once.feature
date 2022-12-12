Feature:

Background:
* print 'in background', __num

@setup
Scenario:
* print 'in setup'
* def data = [{a:1}, {a:2}]

Scenario Outline: first
* print __row

Examples:
| karate.setupOnce().data |

Scenario Outline: second
* print __row

Examples:
| karate.setupOnce().data |
