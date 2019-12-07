Feature: not equal match test file

# some comment

  Background:
    Given def a = 456
    Given def b = 120

  Scenario: test
    Then match a != b
