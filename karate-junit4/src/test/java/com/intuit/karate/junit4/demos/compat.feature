@ignore
Feature: syntax upgrade / backwards compatibility

Scenario: json path on a string should auto-convert
    * def response = "{ foo: { hello: 'world' } }"
    # use $response.foo or $.foo instead
    * def foo = response.foo
    * match foo == { hello: 'world' }

Scenario: 
    * def foo = { bar: [{baz: 1}, {baz: 2}, {baz: 3}]}
    * def fun = function(){ return karate.get('foo.bar[*].baz') }
    * def res = call fun
    * match res == [1, 2, 3]

