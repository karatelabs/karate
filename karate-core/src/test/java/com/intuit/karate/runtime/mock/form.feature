Feature: testing form submits

Background:
* url mockServerUrl

Scenario: without request body
    Given path 'form'
    And form field foo = 'bar'
    When method post
    Then status 200
    And match response == { success: true }
