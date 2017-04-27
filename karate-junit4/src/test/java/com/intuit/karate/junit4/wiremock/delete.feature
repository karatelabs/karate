@ignore
Feature: testing http delete method

Scenario: without request body

    Given url 'http://localhost:' + wiremockPort + '/v1/delete'
    When method delete
    Then status 200
    And match response == { success: true }


Scenario: with request body

    Given url 'http://localhost:' + wiremockPort + '/v1/delete'
    And request { foo: 'bar' }
    When method delete
    Then status 200
    And match response == { success: true }