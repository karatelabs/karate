Feature:

Background:
* def a = 1
* def fun = function(){ return {} }
* def c = callonce fun

Scenario:
    * assert a == 1
    * def a = 2
    * def b = 3
    * match c == {}
    * set c.foo = 'bar'

Scenario:
    * assert a == 1
    * assert typeof b == 'undefined'
    * match c == { foo: 'bar' }
    