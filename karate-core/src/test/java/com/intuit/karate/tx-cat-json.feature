@ignore
Feature:

Scenario:
* def result = call read('tx-kit-json.feature') input.kittens
* def kittens = $result[*].output

* set output
| path       | value            |
| name.first | input.firstName  |
| name.last  | input.lastName   |
| kittens    | kittens          |
