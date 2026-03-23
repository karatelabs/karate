Feature: Create Cat (Intentional Failure)

  This feature intentionally fails to test that Karate assertion
  failures are properly reported in Gatling metrics.

  Scenario: create cat with wrong assertion
    Given url baseUrl
    And path 'cats'
    And request { name: '#(__gatling.name)' }
    When method post
    Then status 201
    * def id = response.id
    # Intentional wrong assertion to test error reporting in Gatling
    * match response.name == 'WRONG_NAME_THAT_WILL_NEVER_MATCH'
