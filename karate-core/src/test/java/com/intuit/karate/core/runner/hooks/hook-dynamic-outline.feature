Feature: Test Hook Feature

Background:
  * def cats = [{name: 'cat1'}, {name: 'cat2'}];

Scenario Outline: cats: ${name}
  * match name == "<name>"
  Examples:
    | cats |
