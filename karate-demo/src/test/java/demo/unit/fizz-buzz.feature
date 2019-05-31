Feature: demo of data-driven unit-testing

  Background:
    * def FB = Java.type('com.intuit.karate.demo.util.FizzBuzz')
    * def fb = function(n){ return FB.process(n) }

  Scenario: simple assertions
    * match fb(1) == '1'
    * match fb(3) == 'Fizz'
    * match fb(5) == 'Buzz'
    * match fb(15) == 'FizzBuzz'

  Scenario Outline: data-driven assertions
    * match fb(val) == expected

    Examples:
      | val! | expected |
      | 1    | 1        |
      | 3    | Fizz     |
      | 5    | Buzz     |
      | 15   | FizzBuzz |
