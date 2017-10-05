@ignore
Feature:

Scenario: null arg
* def result = call read('called-arg-null.feature')

Scenario: single arg
* def result = call read('called-arg-single.feature') { foo: 'bar' }

Scenario: loop arg
* table foos
| foo   |
| 'bar' |
| 'baz' |
| 'ban' |
* def result = call read('called-arg-loop.feature') foos

