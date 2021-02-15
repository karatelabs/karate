Feature: simple feature file

Scenario Outline: test

When def a = <val>
Then assert a == <val>

Examples:
| val |
|   1 |
|  10 |
|  42 |
|  55 |
