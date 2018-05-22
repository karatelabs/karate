Feature: error simulation

Background:
* url demoBaseUrl

Scenario: malformed json request
    Given path 'cats'
    And header Content-Type = 'application/json'
    And request '{ "name": }'
    When method post
    Then status 400
#    And match response contains { status: 400, error: 'Bad Request' }

Scenario: malformed json response
    Given path 'echo'
    And request '{ "foo": }'
    When method post
    Then status 200
    And match response == '{ "foo": }'
