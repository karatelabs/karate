Feature: repeat demos

Scenario: return a list
    * def fun = function(i){ return i * 2 }
    * def foo = karate.repeat(5, fun)
    * match foo == [0, 2, 4, 6, 8]

Scenario: just eval
    * def foo = []
    * def fun = function(i){ foo.add(i) }
    * karate.repeat(5, fun)
    * match foo == [0, 1, 2, 3, 4]

Scenario: use a variable for loop count
    * def count = 3
    * def fun = function(i){ return { val: i } }
    * def foo = karate.repeat(count, fun)
    * match foo == [{ val: 0 }, { val: 1 }, { val: 2 }]

Scenario: generate test data easily
    * def fun = function(i){ return { name: 'User ' + (i + 1) } }
    * def foo = karate.repeat(3, fun)
    * match foo == [{ name: 'User 1' }, { name: 'User 2' }, { name: 'User 3' }]
