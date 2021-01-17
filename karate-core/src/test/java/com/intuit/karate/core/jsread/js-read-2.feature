Feature:

Background:
    * def anotherVariable = 'hello'
    * def data = [{ name: 'one' }, { name: 'two' }]

Scenario Outline:
    * match name == "#present"
    * match anotherVariable == "hello"

Examples:
    | data |

Scenario Outline:
    * match name == "#present"
    * match anotherVariable == "hello"

Examples:
    | name |
    | test |

Scenario Outline:
    * match name == "#present"
    * match anotherVariable == "hello"
    * def params = { 'foo': 'bar' }
    * call read('js-read-called-2.feature') params

Examples:
    | data |
