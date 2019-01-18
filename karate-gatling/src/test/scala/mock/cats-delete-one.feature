@ignore
Feature: delete cat by id and verify

  Scenario:
    Given url baseUrl
    And path id
    When method delete
    Then status 200
    And match response == ''

    Given path id
    And header karate-name = 'cats-get-404'
    When method get
    Then status 404
