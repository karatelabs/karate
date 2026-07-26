Feature: content-type text/plain not misparsed as JSON

  Scenario: response body starting with '[' but Content-Type text/plain stays a string
    Given url 'http://localhost:' + karate.properties['server.port'] + '/config'
    When method GET
    Then status 200
    * match typeof response == 'string'
    * match response contains 'round_interval'
