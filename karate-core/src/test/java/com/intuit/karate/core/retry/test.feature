Feature: retry test

Background:
* def value = 1

Scenario: one
* assert value == 1

Scenario: two
* assert value != 1
