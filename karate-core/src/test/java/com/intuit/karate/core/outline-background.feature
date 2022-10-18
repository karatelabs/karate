Feature:

@setup
Scenario:
* table data
    | name  | extra        |
    | 'one' |              |
    | 'two' | configSource |

Scenario Outline:
* assert name == 'one' || name == 'two'
* assert name == 'two' ? extra == 'normal' : true

Examples:
| karate.setup().data |
