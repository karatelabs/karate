Feature: headers configured in the background apply for all scenarios

Background: 

Given url demoBaseUrl
And path 'headers'
When method get
Then status 200
And def token = response
And def time = responseCookies.time.value
* configure headers = read('classpath:headers.js')

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
