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
    # the callonce in the background is a snapshot at THAT point in time
    # so the next scenario should "rewind" to that state

Scenario:
    * assert a == 1
    * assert typeof b == 'undefined'
    # get else default value
    * def b = karate.get('b', 42)
    * match b == 42
    * match c == {}
    