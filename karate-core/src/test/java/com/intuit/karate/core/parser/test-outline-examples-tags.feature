Feature:

Background:
  * def foo = 'bar'

Scenario Outline: name is <name>
* def name = '<name>'

Examples:
| name    |
| Bob     |
| Nyan    |
| Dylan   |
| Tom   |

@three-examples
Examples:
  | name    |
  | Bob     |
  | Nyan    |
  | Dylan   |

@two-examples
Examples:
  | name    |
  | Bob     |
  | Nyan    |
  | Dylan   |