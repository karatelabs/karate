Feature: no scenario matched

Scenario: test when mock does not match any "route"
    Given url mockServerUrl
    And path 'nomatch'
    When method get
    Then status 404
    And match response == 'no matching scenarios in backend feature files'
