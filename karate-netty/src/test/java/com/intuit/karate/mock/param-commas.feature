Feature: testing commas in param values

  Scenario: using csv params in a string

    Given url mockServerUrl
    And path 'commas'
    And param foo = 'bar,baz'
    When method get
    Then status 200
    And match response == { success: true }

  Scenario: using json string

    Given url mockServerUrl
    And path 'commas'
    And params { foo: 'bar,baz' }
    When method get
    Then status 200
    And match response == { success: true }
