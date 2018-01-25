Feature: jersey ssl with trust store / cert

Background:
    * configure ssl = { trustStore: 'classpath:server-keystore.p12', trustStorePassword: 'karate-mock', trustStoreType: 'pkcs12' }
    * url 'https://localhost:' + karate.properties['jersey.ssl.port']

Scenario:
    Given path 'test'
    When method get
    Then status 200
    And match response == { success: true }

    Given path 'test'
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 200
    And match response == { success: true }

