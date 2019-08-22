Feature: no response headers

Scenario Outline: route by header value: <value>
    Given url mockServerUrl
    And path 'headers'
    And header val = value
    When method get
    Then status 200
    And match response == { val: '#(value)' }

  Examples:
    | value |
    | foo   |
    | bar   |