@apache
Feature: test url-encoded form-field submissions

Scenario: should be able to over-ride the content-type
    Given url demoBaseUrl
    And path 'search', 'headers'
    And form field text = 'hello'
    And header Content-Type = 'application/json'
    When method post
    Then status 200
    And def response = karate.lowerCase(response)
    And match response['content-type'][0] contains 'application/json'

Scenario: normal form submit
    Given url demoBaseUrl
    And path 'echo'
    And form field text = 'hello'
    When method post
    Then status 200
    And match response == 'text=hello'
    And match header Content-Type contains 'text/plain'

