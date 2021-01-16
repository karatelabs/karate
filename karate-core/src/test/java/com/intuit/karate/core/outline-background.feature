Feature:

Background:
    * def foo = 'hello'
    * def fun = function(){ return 'bar' }
    * def data = [{ name: 'one' }, { name: 'two' }]

Scenario Outline:
    * assert name == 'one' || name == 'two'
    * match foo == "hello"
    * match fun() == 'bar'

Examples:
    | data |
