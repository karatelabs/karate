Feature: SSL with key store and trust store (mTLS setup)

Background:
    * configure ssl = { keyStore: 'classpath:ssl/server-keystore.p12', keyStorePassword: 'karate-mock', keyStoreType: 'pkcs12', trustStore: 'classpath:ssl/server-keystore.p12', trustStorePassword: 'karate-mock', trustStoreType: 'pkcs12' }
    * url 'https://localhost:' + karate.properties['ssl.port']

Scenario: keystore - connect with client certificate
    Given path 'test'
    When method get
    Then status 200
    And match response == { success: true }
