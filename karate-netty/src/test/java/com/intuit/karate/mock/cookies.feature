Feature: testing cookies from server

Background:
* url mockServerUrl

Scenario: without request body
    Given path 'cookies'
    When method get
    Then status 200
    And match responseCookies.foo.value == 'bar'
