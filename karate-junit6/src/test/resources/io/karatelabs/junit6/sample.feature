Feature: Sample feature for JUnit 6 integration test

  Scenario: Simple passing test
    * def message = 'Hello World'
    * match message == 'Hello World'

  Scenario: Another passing test
    * def numbers = [1, 2, 3]
    * match numbers contains 2
