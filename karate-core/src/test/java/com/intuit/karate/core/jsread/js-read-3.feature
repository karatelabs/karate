Feature:

Background:
    * def anotherVariable = 'hello'
    * def x = read('js-read-3.json')
    * def data = x.thirderror
    * def backgroundVar =
    """
        {"foo": '#(data)' }
    """

Scenario Outline:
    * match backgroundVar == { "foo": "#(data)" }
    * match id == "#present"
    * match anotherVariable == "hello"

Examples:
    | data |

Scenario Outline:
    * def param =
    """
        { bar: '#(backgroundVar)'}
    """
    * match param == { bar: '#(backgroundVar)'}
    * match id == "#present"
    * match anotherVariable == "hello"

Examples:
    | data |


Scenario Outline:
    * def params =
    """
        { backgroundVar: '#(backgroundVar)'}
    """
    * print params
    * match id == "#present"
    * match anotherVariable == "hello"
    * call read('js-read-called-2.feature') params

Examples:
    | data |
