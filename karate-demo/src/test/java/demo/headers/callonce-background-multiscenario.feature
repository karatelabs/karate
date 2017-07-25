Feature: ensure that a callonce and a header in the background
    works fine even when there are multiple scenarios

Background:

* def setup = callonce read('common-noheaders.feature')
* def authToken = (setup.token + setup.time)
* header Authorization = authToken + demoBaseUrl
* cookie time = setup.time
* url demoBaseUrl

Scenario: first scenario
    Given path 'headers', setup.token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: second scenario
    Given path 'headers', setup.token
    And param url = demoBaseUrl
    When method get
    Then status 200
