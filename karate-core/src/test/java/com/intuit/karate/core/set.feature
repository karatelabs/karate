Feature:

Scenario:
* set cat
| path   | value |
| name   | 'Bob' |
| age    | 5     |

* match cat == { name: 'Bob', age: 5 }
