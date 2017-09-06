@ignore
Feature: testing multi param values

  Scenario: using params in a list

    Given url 'http://localhost:' + wiremockPort + '/v1/multiparams'
    And param foo = 'bar', 'baz'
    When method get
    Then status 200
    And match response == { success: true }


  Scenario: using json array

    Given url 'http://localhost:' + wiremockPort + '/v1/multiparams'
    And params { foo: ['bar', 'baz'] }
    When method get
    Then status 200
    And match response == { success: true }
