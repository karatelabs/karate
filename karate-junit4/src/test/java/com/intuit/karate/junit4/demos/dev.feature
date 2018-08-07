@ignore
Feature: scratch pad to work on only one construct at a time

  Scenario: test
    Given url 'https://httpbin.org'
    And path '/post'
    And form field test = '123'
    And header Content-Type = 'application/json'
    When method post
    Then status 200
