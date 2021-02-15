Feature: binary json

Background:
    * url demoBaseUrl
    * def Base64 = Java.type('java.util.Base64')

Scenario: json with byte-array
    Given path 'echo', 'binary'
    And def encoded = Base64.encoder.encodeToString('hello'.getBytes())
    And request { message: 'hello', data: '#(encoded)' }
    When method post
    Then status 200
    And def expected = Base64.encoder.encodeToString('world'.getBytes())
    And match response == { message: 'world', data: '#(expected)' }
