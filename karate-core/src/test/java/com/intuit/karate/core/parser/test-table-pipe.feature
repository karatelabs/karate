Feature:

Scenario Outline:
* def value = '<lhs>'
* print 'value: ', value
* match value == "#? _ == 'hello' || _ == 'pi|pe'"

Examples:
| lhs      | rhs     |
| hello    | hello   |
| pi\|pe   | pi\|pe  |
