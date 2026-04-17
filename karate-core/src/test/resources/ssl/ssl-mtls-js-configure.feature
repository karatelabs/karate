Feature: SSL mTLS configured via JavaScript karate.configure()

Background:
    # Configure SSL using JavaScript API (simulates karate-config.js pattern)
    * eval karate.configure('ssl', { keyStore: karate.properties['mtls.clientKeyStore'], keyStorePassword: 'test123', keyStoreType: 'pkcs12', trustStore: karate.properties['mtls.clientTrustStore'], trustStorePassword: 'test123', trustStoreType: 'pkcs12', trustAll: false })
    * url 'https://localhost:' + karate.properties['mtls.port']

Scenario: mtls via JS configure - karate.configure() from JavaScript
    Given path 'test'
    When method get
    Then status 200
    And match response == { success: true }
