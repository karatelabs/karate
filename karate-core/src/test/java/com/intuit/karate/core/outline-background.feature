Feature:

Background:
    * def foo = 'hello'
    * def data = [{ name: 'one' }, { name: 'two' }]

Scenario Outline:
    * assert name == 'one' || name == 'two'
    * match foo == "hello"

Examples:
    | data |
