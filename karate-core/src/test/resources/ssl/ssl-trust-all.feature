Feature: SSL with trust all (self-signed certificate)

Background:
    * configure ssl = true
    * url 'https://localhost:' + karate.properties['ssl.port']

Scenario: trust all - connect to self-signed HTTPS server
    Given path 'test'
    When method get
    Then status 200
    And match response == { success: true }
