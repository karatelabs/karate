Feature: Navigation Tests
  Basic browser navigation tests

  Background:
    * configure driver = driverConfig

  Scenario: Navigate to index page and verify title
    * def url = serverUrl + '/'
    * driver url
    * match driver.title == 'Karate Driver Test'

  Scenario: Script execution
    * driver serverUrl + '/'
    * def result = script('1 + 1')
    * match result == 2

  Scenario: Get page content via script
    * driver serverUrl + '/'
    * def testValue = script('window.testValue')
    * match testValue == 42
