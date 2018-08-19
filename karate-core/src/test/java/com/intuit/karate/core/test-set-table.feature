Feature:

Scenario:
* def kittenName = 'Bob'
* def kittenAge = 2

* set output  
| path       | value            |
| name       | kittenName       |
| age        | kittenAge        |

* match output == { name: 'Bob', age: 2 }
