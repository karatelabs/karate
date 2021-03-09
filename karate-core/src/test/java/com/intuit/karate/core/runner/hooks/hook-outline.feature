Feature: Test Hook Feature

Background:

Scenario Outline: cats: ${name}
  * match name == "<name>"
  Examples:
    | name |
    | Mylo |
    | Oscar |
