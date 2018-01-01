Feature: white space edge cases

Scenario: json response with leading line feed
    Given url mockServerUrl
    And path 'linefeed'
    When method get
    Then status 200
    And match response == { success: true }

Scenario: string response which is pure white space with line feeds
    Given url mockServerUrl
    And path 'spaces'
    When method get
    Then status 200
    And match response == '\n    \n'