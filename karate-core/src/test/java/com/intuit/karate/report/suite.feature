Feature: sample karate test script
  for help, see: https://github.com/intuit/karate/wiki/IDE-Support

  Background:
    Given url baseURL
    * configure report = false
  @test1
  Scenario: Test and variables should not come in cucumber report
    Given path 'users'
    When method get
    Then status 200
