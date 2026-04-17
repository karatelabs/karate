Feature: SSL mutual TLS (mTLS) - server requires client certificate

Background:
    * configure ssl = { keyStore: '#(karate.properties["mtls.clientKeyStore"])', keyStorePassword: 'test123', keyStoreType: 'pkcs12', trustStore: '#(karate.properties["mtls.clientTrustStore"])', trustStorePassword: 'test123', trustStoreType: 'pkcs12', trustAll: false }
    * url 'https://localhost:' + karate.properties['mtls.port']

Scenario: mtls - connect with client certificate to server requiring client auth
    Given path 'test'
    When method get
    Then status 200
    And match response == { success: true }
