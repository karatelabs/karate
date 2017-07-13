Feature: various javascript tests

Scenario: arrays returned from js can be used in match
* def fun = function(){ return ['foo', 'bar', 'baz'] }
* def json = ['foo', 'bar', 'baz']
* match json == fun()
* def expected = fun()
* match json == expected

Scenario: json-path can be performed in js
* def json = [{foo: 1}, {foo: 2}]
* def fun = function(arg) { return karate.jsonPath(arg, '$[*].foo') }
* def res = call fun json
* match res == [1, 2]

Scenario: table to json with expressions evaluated
* def one = 'hello'
* def two = { baz: 'world' }
* table json =
    | foo     | bar |
    | one     | 1   |
    | two.baz | 2   |
* match json == [{ foo: 'hello', bar: 1 }, { foo: 'world', bar: 2 }]
 
