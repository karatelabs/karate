Feature:

@setup
Scenario:
    * def data = [{ name: 'one' }, { name: 'two' }]

Scenario Outline:
    * match name == "#present"

Examples:
    | karate.setup().data |

Scenario Outline:
    * match name == "#present"
    * def params = { 'foo': 'bar' }
    * call read('js-read-called-2.feature') params

Examples:
    | karate.setup().data |
