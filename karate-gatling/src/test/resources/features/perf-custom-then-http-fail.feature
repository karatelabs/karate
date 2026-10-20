Feature: Custom perf event followed by an HTTP request whose assertion fails

  Scenario: custom event then failing http assertion
    * def start = Date.now()
    * karate.capturePerfEvent('my-event', start, Date.now())
    Given url baseUrl
    And path 'cats'
    And request { name: 'Kitty' }
    When method post
    Then status 201
    * match response.name == 'WRONG_NAME'
