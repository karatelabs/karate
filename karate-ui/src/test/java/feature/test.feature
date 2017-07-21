Feature: test feature

Scenario: test scenario
* def a = 1
* def b = 2
* assert a + b == 3

Scenario Outline: test outline
* def a = <foo>
* def b = 2
* assert a + b == <bar>

Examples:
| foo | bar |
| 1   | 3   |
| 2   | 4   |
