Feature:

Scenario: map and repeat should not mangle js arrays
* def foo = [1, 2]
* def fun = function(x){ return { x: x, bar: [1, 2] } }
* def res = karate.map(foo, fun)
* match res == [{ x: 1, bar: [1, 2]}, { x: 2, bar: [1, 2] }]

* def fun = function(i){ return { foo: [1, 2]} }
* def bar = karate.repeat(2, fun)
* match bar == [{ foo: [1, 2] }, { foo: [1, 2] }]
