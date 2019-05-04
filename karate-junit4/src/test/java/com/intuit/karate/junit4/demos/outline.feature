Feature: advanced scenario outline and examples table usage

Scenario Outline: name is <name> and age is <age>
  * def name = '<name>'
  * match name == __row.name
  * def expected = __num == 0 ? 'name is Bob and age is 5' : 'name is Nyan and age is 6'
  * match expected == karate.info.scenarioName

  Examples:
    | name | age |
    | Bob  | 5   |
    | Nyan | 6   |

Scenario Outline: magic variables with type hints
  * def expected = __num == 0 ? { name: 'Bob', age: 5 } : { name: 'Nyan', age: 6 }
  * match __row == expected

  Examples:
    | name | age! |
    | Bob  | 5    |
    | Nyan | 6    |

Scenario Outline: magic variables with embedded expressions
  * def expected = __num == 0 ? { name: 'Bob', alive: false } : { name: 'Nyan', alive: true }
  * match expected == { name: '#(__row.name)', alive: '#(__row.alive)' }
  * eval karate.set(__row)
  * match expected == { name: '#(name)', alive: '#(alive)' }

  Examples:
    | name | alive! |
    | Bob  | false  |
    | Nyan | true   |

Scenario Outline: inline JSON
  * match __row == { first: 'hello', second: { a: 1 } }

  Examples:
    | first  | second!  |
    | hello  | { a: 1 } |