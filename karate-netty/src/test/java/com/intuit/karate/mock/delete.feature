Feature: testing http delete method

Background:
* url mockServerUrl

Scenario: without request body
    Given path 'delete'
    When method delete
    Then status 200
    And match response == { success: true }

Scenario: with request body
    Given path 'delete'
    And request { foo: 'bar' }
    When method delete
    Then status 200
    And match response == { success: true }

Scenario: empty response body
    Given path 'deleteEmptyResponse'
    When method delete
    Then status 200