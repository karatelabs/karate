Feature: test that arrays returned from js can be used in match

Scenario: 
* def fun = function(){ return ['foo', 'bar', 'baz'] }
* def json = ['foo', 'bar', 'baz']
* match json == fun()
* def expected = fun()
* match json == expected
