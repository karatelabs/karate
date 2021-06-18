Feature:

Background:
    * def foo = 'hello'
    * def fun = function(){ return 'bar' }
    * configure driver = { type: "noopdriver" }
    * driver 'http://google.com'
    * table data
        | name  | extra        |
        | 'one' |              |
        | 'two' | configSource |

Scenario Outline:
    * assert name == 'one' || name == 'two'
    * assert name == 'two' ? extra == 'normal' : true
    * match foo == "hello"
    * match fun() == 'bar'
    * click("#testing.usage.driver.method")

Examples:
    | data |
