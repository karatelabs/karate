Feature: jersey ssl with trust store / cert

Background:
    * configure ssl = { keyStore: 'classpath:keystore.p12', password: 'karate-mock', type: 'pkcs12' }
    * url 'https://localhost:' + karate.properties['jersey.ssl.port']

Scenario:
    Given path 'test'
    When method get
    Then status 200
    And match response == { success: true }

