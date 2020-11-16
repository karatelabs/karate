Feature:

Scenario:
* table data
    | first  | last    | age |
    | 'John' | 'Smith' |  20 |
    | 'Bill' |         |  40 |
* match data == [{ first: 'John', last: 'Smith', age: 20 }, { first: 'Bill', age: 40 }]
