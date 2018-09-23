Feature:

Scenario:
* table cats
    | name   | age |
    | 'Bob'  | 2   |
    | 'Wild' | 4   |
    | 'Nyan' | 3   |

* match cats == [{name: 'Bob', age: 2}, {name: 'Wild', age: 4}, {name: 'Nyan', age: 3}]

