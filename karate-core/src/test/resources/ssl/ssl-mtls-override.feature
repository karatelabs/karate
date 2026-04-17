Feature: SSL mTLS with config override - configure ssl called twice

Background:
    # First configure with just trustAll (simulates karate-config.js)
    * configure ssl = true
    # Then override with full mTLS config (simulates Background override)
    * configure ssl = { keyStore: '#(karate.properties["mtls.clientKeyStore"])', keyStorePassword: 'test123', keyStoreType: 'pkcs12', trustStore: '#(karate.properties["mtls.clientTrustStore"])', trustStorePassword: 'test123', trustStoreType: 'pkcs12', trustAll: false }
    * url 'https://localhost:' + karate.properties['mtls.port']

Scenario: mtls override - second configure ssl replaces first
    Given path 'test'
    When method get
    Then status 200
    And match response == { success: true }
