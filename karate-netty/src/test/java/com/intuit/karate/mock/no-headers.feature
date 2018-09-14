Feature: no response headers

Scenario: test when mock routines return no content or headers
    Given url mockServerUrl
    And path 'noheaders'
    When method get
    Then status 404
    And match response == ''
