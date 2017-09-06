@ignore
Feature: testing commas in param values

  Scenario: using csv params in a string

    Given url 'http://localhost:' + wiremockPort + '/v1/commas'
    And param foo = 'bar,baz'
    When method get
    Then status 200
    And match response == { success: true }

  Scenario: using json string

    Given url 'http://localhost:' + wiremockPort + '/v1/commas'
    And params { foo: 'bar,baz' }
    When method get
    Then status 200
    And match response == { success: true }
