Feature: the karate-config.js can perform 'singleton' style one-time init of auth
    instead of re-doing it for every feature in a test-suite, even for multi-threaded / parallel runs

Background:
* url demoBaseUrl

# refer to karate-config.js to see how these were initialized
* def time = authInfo.authTime
* def token = authInfo.authToken

# we now have enough information to set up auth / headers for all scenarios
* cookie time = time
* configure headers = read('classpath:headers.js')

Scenario: no extra config - they have been set automatically by the background 
    and the 'callSingle' in karate-config.js

    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200
