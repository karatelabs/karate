Feature:

Background:
    * def foo = 'hello'
    * def fun = function(){ return 'bar' }
    * def data = [{ name: 'one' }, { name: 'two' }]
    * configure driver = { type: "noopdriver" }
    * driver 'http://google.com'

Scenario Outline:
    * assert name == 'one' || name == 'two'
    * match foo == "hello"
    * match fun() == 'bar'
    * click("#testing.usage.driver.method")

Examples:
    | data |
