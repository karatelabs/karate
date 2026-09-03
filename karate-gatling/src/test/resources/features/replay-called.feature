Feature: A called feature that prints and makes one HTTP call

  Scenario: print and post
    * print 'CALLED-MARKER'
    Given url baseUrl
    And path 'cats'
    And request { name: 'CalledKitty' }
    When method post
    Then status 201
