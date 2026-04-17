Feature: SSL mTLS configured mid-flow - starts without SSL then configures

Scenario: mtls mid-flow - configure ssl after initial non-SSL setup
    # Start with a non-SSL request to the plain HTTP server
    * url 'http://localhost:' + karate.properties['http.port']
    * path 'test'
    * method get
    * status 200
    * match response == { success: true }

    # Now configure SSL with mTLS for a different server
    * configure ssl = { keyStore: '#(karate.properties["mtls.clientKeyStore"])', keyStorePassword: 'test123', keyStoreType: 'pkcs12', trustStore: '#(karate.properties["mtls.clientTrustStore"])', trustStorePassword: 'test123', trustStoreType: 'pkcs12', trustAll: false }

    # Make request to the mTLS server - HTTP client must be rebuilt with SSL
    * url 'https://localhost:' + karate.properties['mtls.port']
    * path 'test'
    * method get
    * status 200
    * match response == { success: true }
