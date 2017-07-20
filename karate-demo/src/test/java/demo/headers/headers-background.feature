Feature: header set in the background apply for all scenarios

Background: 

Given url demoBaseUrl
And path 'headers'
When method get
Then status 200
And def token = response
And def time = responseCookies.time.value
* header Authorization = token + time + demoBaseUrl

Scenario: first
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: second
    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200
