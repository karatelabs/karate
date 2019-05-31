Feature: advanced scenario outline and examples table usage

Scenario Outline: name is <name> and age is <age>
  * def temp = '<name>'
  * match temp == name
  * match temp == __row.name
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
  * match __row == { name: '#(name)', alive: '#(alive)' }

  Examples:
    | name | alive! |
    | Bob  | false  |
    | Nyan | true   |

Scenario Outline: inline JSON
  * match __row == { first: 'hello', second: { a: 1 } }

  Examples:
    | first  | second!  |
    | hello  | { a: 1 } |

Scenario Outline: using the optional ##() marker effectively with examples type-hints
    * def search = { name: { first: "##(first)", last: "#(last)" }, age: "##(age)" }
    * match search == expected

    Examples:
    | first | last  | age! | expected!                                           |
    | John  | Smith | 20   | { name: { first: 'John', last: 'Smith' }, age: 20 } |
    | Jane  | Doe   |      | { name: { first: 'Jane', last: 'Doe' } }            |
    |       | Waldo |      | { name: { last: 'Waldo' } }                         |

Scenario Outline: dynamic scenario outline
    * print 'row: ', __row
    * match __row == { name: '#string', age: '#number' }

    Examples:
    | read('cats.json') |