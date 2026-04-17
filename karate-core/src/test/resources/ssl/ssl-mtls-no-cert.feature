Feature: SSL mTLS negative test - no client certificate provided

Background:
    * configure ssl = true
    * url 'https://localhost:' + karate.properties['mtls.port']

Scenario: mtls - connect WITHOUT client certificate should fail
    Given path 'test'
    When method get
    Then status 200
