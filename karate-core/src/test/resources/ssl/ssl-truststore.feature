Feature: SSL with trust store

Background:
    * configure ssl = { trustStore: 'classpath:ssl/server-keystore.p12', trustStorePassword: 'karate-mock', trustStoreType: 'pkcs12' }
    * url 'https://localhost:' + karate.properties['ssl.port']

Scenario: truststore - connect with custom trust store
    Given path 'test'
    When method get
    Then status 200
    And match response == { success: true }
