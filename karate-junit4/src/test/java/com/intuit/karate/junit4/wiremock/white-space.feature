@ignore
Feature: white space edge cases

Scenario: json response with leading line feed
    Given url 'http://localhost:' + wiremockPort + '/v1/linefeed'
    When method get
    Then status 200
    And match response == { success: true }

Scenario: string response which is pure white space with line feeds
    Given url 'http://localhost:' + wiremockPort + '/v1/spaces'
    When method get
    Then status 200
    And match response == '\n    \n'