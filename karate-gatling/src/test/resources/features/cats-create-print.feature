Feature: Create Cat and print, for the step-log capture tests

  Background:
    * url baseUrl

  Scenario: Create a cat and print a line
    Given path 'cats'
    And request { name: 'CaptureKitty', age: 1 }
    When method post
    Then status 201
    * print 'printed by the feature'
