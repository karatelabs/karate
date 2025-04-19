Feature: brotli

Scenario: auto decode brotli compressed response
    Given url mockServerUrl
    And path 'binary', 'brotli'
    When method get
    Then match response == { hello: 'world' }
