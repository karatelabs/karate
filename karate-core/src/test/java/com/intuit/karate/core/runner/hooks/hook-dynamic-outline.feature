Feature: Test Hook Feature

@setup
Scenario:
  * def cats = [{name: 'cat1'}, {name: 'cat2'}];

Scenario Outline: cats: ${name}
  * match name == '<name>'
  Examples:
    | karate.setup().cats |
