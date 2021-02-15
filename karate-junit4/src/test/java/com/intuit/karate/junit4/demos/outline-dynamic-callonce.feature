Feature:

Background:
* def fun = function(){ karate.log('CALLED') }
* callonce fun
* def data = [{a: 1}, {a: 2}, {a: 3}]

Scenario Outline: row <a>
* print 'a: ', a

Examples:
| data |
