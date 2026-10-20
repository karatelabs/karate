Feature: Called feature that makes a request and then fails

  Scenario: request then failing match
    Given url baseUrl
    And path 'cats'
    And request { name: 'Kitty' }
    When method post
    Then status 201
    * match response.name == 'WRONG_NAME'
