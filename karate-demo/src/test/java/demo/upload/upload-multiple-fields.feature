Feature: multipart fields (multiple)

Background:
* url demoBaseUrl

Scenario: upload multiple fields
    Given path 'files', 'fields'
    And multipart fields { message: 'hello world', json: { foo: 'bar' } }
    When method post
    Then status 200
    And match response == { message: 'hello world', json: { foo: 'bar' } }
